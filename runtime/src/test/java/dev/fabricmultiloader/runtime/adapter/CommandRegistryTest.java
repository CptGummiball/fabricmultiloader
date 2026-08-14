package dev.fabricmultiloader.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.api.command.Arg;
import dev.fabricmultiloader.api.command.CommandInvocation;
import dev.fabricmultiloader.api.command.CommandSender;
import dev.fabricmultiloader.api.command.CommandSpec;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.text.Text;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import dev.fabricmultiloader.runtime.log.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {

    private CommandRegistry registry(Side side) {
        return new CommandRegistry("examplemod", side, Log.named("test"));
    }

    /** {@code /ruby give <player> <amount>} plus {@code /ruby info}. */
    private static CommandSpec rubyCommand() {
        return CommandSpec.named("ruby")
                .permissionLevel(2)
                .sub(CommandSpec.named("give")
                        .arg("target", Arg.player())
                        .arg("amount", Arg.integer(1, 64))
                        .executes(inv -> 1)
                        .build())
                .sub(CommandSpec.named("info")
                        .executes(inv -> 1)
                        .build())
                .build();
    }

    @Nested
    @DisplayName("flattening")
    class Flattening {

        @Test
        @DisplayName("each executable branch becomes one node with its full path")
        void flattensTheTree() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(rubyCommand());

            List<String> paths = new ArrayList<String>();
            for (CommandRegistry.Node node : registry.nodes()) {
                paths.add(node.path());
            }
            assertThat(paths).containsExactly("ruby give", "ruby info");
            assertThat(registry.rootLiterals()).containsExactly("ruby");
        }

        @Test
        @DisplayName("arguments accumulate down the tree in declaration order")
        void foldsArgumentsIntoTheLeaf() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(rubyCommand());

            CommandRegistry.Node give = registry.nodes().get(0);
            // Brigadier chains arguments in order, so the order here is not cosmetic.
            assertThat(give.arguments().keySet()).containsExactly("target", "amount");
            assertThat(give.arguments().get("amount").type()).isEqualTo(Integer.class);
            assertThat(give.segments()).containsExactly("ruby", "give");
        }

        @Test
        @DisplayName("permission accumulates down the tree, so a gated parent gates its children")
        void inheritsPermissionFromTheParent() {
            CommandRegistry registry = registry(Side.SERVER);
            // Level 2 is declared on "ruby" alone; the sub-commands declare nothing.
            registry.register(rubyCommand());

            for (CommandRegistry.Node node : registry.nodes()) {
                // Reading the leaf's own level would report 0 and silently open every sub-command
                // to everyone — the subtree is unreachable without traversing the gated parent.
                assertThat(node.permissionLevel()).as(node.path()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("a sub-command may raise the level further")
        void takesTheHighestLevelOnThePath() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(CommandSpec.named("ruby")
                    .permissionLevel(2)
                    .sub(CommandSpec.named("wipe")
                            .permissionLevel(4)
                            .executes(inv -> 1)
                            .build())
                    .build());

            assertThat(registry.nodes().get(0).permissionLevel()).isEqualTo(4);
        }

        @Test
        @DisplayName("a node with children and its own body yields both")
        void handlesAnExecutableParent() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(CommandSpec.named("ruby")
                    .executes(inv -> 1)
                    .sub(CommandSpec.named("info").executes(inv -> 1).build())
                    .build());

            List<String> paths = new ArrayList<String>();
            for (CommandRegistry.Node node : registry.nodes()) {
                paths.add(node.path());
            }
            assertThat(paths).containsExactly("ruby", "ruby info");
        }
    }

    @Nested
    @DisplayName("side filtering")
    class SideFiltering {

        @Test
        @DisplayName("a client-only command is dropped on a dedicated server")
        void dropsAClientCommandOnTheServer() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(CommandSpec.named("hud")
                    .onlyOn(Side.CLIENT)
                    .executes(inv -> 1)
                    .build());

            // Filtered here rather than in the adapter, because a client-only command registered
            // server-side either fails on its argument types or shadows a real command — and only
            // ever on a dedicated server, where nobody is looking.
            assertThat(registry.nodes()).isEmpty();
            assertThat(registry.specs()).isEmpty();
        }

        @Test
        @DisplayName("a client-only sub-command is dropped while its siblings stay")
        void dropsOnlyTheFilteredBranch() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(CommandSpec.named("ruby")
                    .sub(CommandSpec.named("hud").onlyOn(Side.CLIENT).executes(inv -> 1).build())
                    .sub(CommandSpec.named("info").executes(inv -> 1).build())
                    .build());

            assertThat(registry.nodes()).hasSize(1);
            assertThat(registry.nodes().get(0).path()).isEqualTo("ruby info");
        }

        @Test
        @DisplayName("a command with no side restriction registers everywhere")
        void keepsUnrestrictedCommands() {
            assertThat(registry(Side.CLIENT).isOpen()).isTrue();
            CommandRegistry client = registry(Side.CLIENT);
            client.register(rubyCommand());
            assertThat(client.nodes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("conflicts")
    class Conflicts {

        @Test
        @DisplayName("the same path registered twice is refused rather than resolved by order")
        void refusesDuplicatePaths() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(CommandSpec.named("ruby")
                    .sub(CommandSpec.named("info").executes(inv -> 1).build()).build());

            OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                    () -> registry.register(CommandSpec.named("ruby")
                            .sub(CommandSpec.named("info").executes(inv -> 2).build()).build()));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
            assertThat(thrown.getMessage()).contains("/ruby info");
        }

        @Test
        @DisplayName("an argument name shadowing a parent's is refused")
        void refusesShadowedArguments() {
            CommandRegistry registry = registry(Side.SERVER);

            OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                    () -> registry.register(CommandSpec.named("ruby")
                            .arg("amount", Arg.integer())
                            .sub(CommandSpec.named("give")
                                    .arg("amount", Arg.word())
                                    .executes(inv -> 1)
                                    .build())
                            .build()));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
            assertThat(thrown.getMessage()).contains("amount");
        }
    }

    @Nested
    @DisplayName("registration window")
    class Window {

        @Test
        @DisplayName("registering after the window closes reports OMNI-4002")
        void refusesLateRegistration() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.seal();

            OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                    () -> registry.register(rubyCommand()));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
            // The reason matters: the server has already sent its command tree to connected
            // clients, so a late command exists on the server and in nobody's tab completion.
            assertThat(thrown.getMessage()).contains("command tree");
        }

        @Test
        @DisplayName("a null spec is a programming error, not an error code")
        void rejectsNull() {
            assertThatThrownBy(() -> registry(Side.SERVER).register(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("invocation")
    class Invocation {

        private CommandRegistry.Node giveNode() {
            CommandRegistry registry = registry(Side.SERVER);
            registry.register(rubyCommand());
            return registry.nodes().get(0);
        }

        private final List<String> replies = new ArrayList<String>();

        private final Feedback feedback = new Feedback() {
            @Override
            public void reply(Text text) {
                replies.add("reply:" + text.toPlainString());
            }

            @Override
            public void broadcast(Text text) {
                replies.add("broadcast:" + text.toPlainString());
            }

            @Override
            public void fail(Text text) {
                replies.add("fail:" + text.toPlainString());
            }
        };

        @Test
        @DisplayName("arguments are read back by name and type")
        void readsArguments() {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("amount", 42);
            CommandInvocation invocation = new CommandInvocationImpl(giveNode(), values,
                    CommandSender.CONSOLE, 4, null, null, feedback);

            assertThat(invocation.arg("amount", Integer.class)).isEqualTo(42);
            assertThat(invocation.optionalArg("amount", Integer.class)).contains(42);
            assertThat(invocation.permissionLevel()).isEqualTo(4);
            assertThat(invocation.player()).isEmpty();
        }

        @Test
        @DisplayName("an argument the command does not declare is empty, not an exception")
        void reportsAnUnknownArgumentAsEmpty() {
            CommandInvocation invocation = new CommandInvocationImpl(giveNode(),
                    new LinkedHashMap<String, Object>(), CommandSender.CONSOLE, 4, null, null,
                    feedback);

            assertThat(invocation.optionalArg("nothing", String.class)).isEmpty();
            assertThatThrownBy(() -> invocation.arg("nothing", String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no argument 'nothing'");
        }

        @Test
        @DisplayName("reading an argument as the wrong type names the command and the declared type")
        void reportsATypeMismatch() {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("amount", 42);
            CommandInvocation invocation = new CommandInvocationImpl(giveNode(), values,
                    CommandSender.CONSOLE, 4, null, null, feedback);

            // Brigadier would report this as a ClassCastException from inside a lambda, naming
            // neither the command nor the argument.
            assertThatThrownBy(() -> invocation.arg("amount", PlayerRef.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/ruby give")
                    .hasMessageContaining("declares argument 'amount' as Integer");
        }

        @Test
        @DisplayName("feedback goes through the payload's adapter")
        void routesFeedback() {
            CommandInvocation invocation = new CommandInvocationImpl(giveNode(),
                    new LinkedHashMap<String, Object>(), CommandSender.PLAYER, 2, null, null,
                    feedback);

            invocation.reply("done");
            invocation.broadcast(Text.literal("everyone"));
            invocation.fail(Text.literal("nope"));

            assertThat(replies).containsExactly("reply:done", "broadcast:everyone", "fail:nope");
        }
    }
}
