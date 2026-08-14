package dev.fabricmultiloader.runtime.mixin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.testing.FakeFabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConditionalMixinPluginTest {

    private static final String PAYLOAD = "examplemod-mc1214";
    private static final String CONFIG = "examplemod-mc1214.integration.mixins.json";
    private static final String PACKAGE = "com.example.mc1214.integration.mixin";
    private static final String CLOTH_MIXIN = PACKAGE + ".ClothConfigScreenMixin";
    private static final String JEI_MIXIN = PACKAGE + ".JeiPluginMixin";
    private static final String UNLISTED_MIXIN = PACKAGE + ".AlwaysMixin";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty("examplemod.debugOverlay");
    }

    /** A payload carrying one mixin config with an {@code omni} block. */
    private FakeFabricLoader loaderWith(String omniBlock) throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("game"));
        Path payloadRoot = Files.createDirectories(tempDir.resolve("payload"));

        FakeFabricLoader loader = new FakeFabricLoader(gameDir)
                .withMod("minecraft", "1.21.4")
                .withMod("fabricloader", "0.16.9")
                .withMod(PAYLOAD, "2.0.0", payloadRoot)
                .onSide(Side.CLIENT);

        loader.withFile(PAYLOAD, "fabric.mod.json",
                "{\"schemaVersion\": 1, \"id\": \"" + PAYLOAD + "\", \"version\": \"2.0.0\",\n"
                        + " \"mixins\": [\"" + CONFIG + "\","
                        + " {\"config\": \"other.mixins.json\", \"environment\": \"client\"}]}");
        loader.withFile(PAYLOAD, CONFIG,
                "{\n"
                        + "  \"required\": true,\n"
                        + "  \"package\": \"" + PACKAGE + "\",\n"
                        + "  \"plugin\": \"dev.fabricmultiloader.runtime.mixin"
                        + ".ConditionalMixinPlugin\",\n"
                        + "  \"mixins\": [\"ClothConfigScreenMixin\", \"JeiPluginMixin\"]"
                        + (omniBlock == null ? "" : ",\n  \"omni\": " + omniBlock)
                        + "\n}");
        // A second config in another package, to prove the locator picks the right one.
        loader.withFile(PAYLOAD, "other.mixins.json",
                "{\"package\": \"com.example.mc1214.other\", \"mixins\": []}");
        return loader;
    }

    private static final String CONDITIONS = "{\n"
            + "    \"conditions\": {\n"
            + "      \"ClothConfigScreenMixin\": {\"requireMod\": \"cloth-config\","
            + " \"version\": \">=15.0.0\"},\n"
            + "      \"JeiPluginMixin\": {\"requireMod\": \"jei\"}\n"
            + "    },\n"
            + "    \"defaultDecision\": \"apply\"\n"
            + "  }";

    private static ConditionalMixinPlugin loaded(FakeFabricLoader loader) {
        ConditionalMixinPlugin plugin = new ConditionalMixinPlugin(loader);
        plugin.onLoad(PACKAGE);
        return plugin;
    }

    @Nested
    @DisplayName("condition matrix")
    class Conditions {

        @Test
        @DisplayName("a mixin whose required mod is absent is skipped")
        void skipsWhenTheModIsMissing() throws IOException {
            ConditionalMixinPlugin plugin = loaded(loaderWith(CONDITIONS));

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isFalse();
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", JEI_MIXIN)).isFalse();
        }

        @Test
        @DisplayName("a mixin whose required mod is present is applied")
        void appliesWhenTheModIsPresent() throws IOException {
            FakeFabricLoader loader = loaderWith(CONDITIONS)
                    .withMod("cloth-config", "15.0.140")
                    .withMod("jei", "19.0.0");

            ConditionalMixinPlugin plugin = loaded(loader);

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", JEI_MIXIN)).isTrue();
        }

        @Test
        @DisplayName("a required mod present in the wrong version is skipped")
        void respectsTheVersionRange() throws IOException {
            FakeFabricLoader loader = loaderWith(CONDITIONS).withMod("cloth-config", "14.0.0");

            assertThat(loaded(loader).shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN))
                    .isFalse();
        }

        @Test
        @DisplayName("a system property condition follows the property")
        void respectsASystemProperty() throws IOException {
            String block = "{\"conditions\": {\"ClothConfigScreenMixin\":"
                    + " {\"requireProperty\": \"examplemod.debugOverlay\"}}}";
            ConditionalMixinPlugin plugin = loaded(loaderWith(block));

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isFalse();

            System.setProperty("examplemod.debugOverlay", "true");
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
        }

        @Test
        @DisplayName("a side condition follows the physical side")
        void respectsTheSide() throws IOException {
            String block = "{\"conditions\": {"
                    + "\"ClothConfigScreenMixin\": {\"requireEnv\": \"client\"},"
                    + "\"JeiPluginMixin\": {\"requireEnv\": \"server\"}}}";
            ConditionalMixinPlugin plugin = loaded(loaderWith(block));

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", JEI_MIXIN)).isFalse();
        }

        @Test
        @DisplayName("an unlisted mixin follows defaultDecision")
        void honoursTheDefaultDecision() throws IOException {
            assertThat(loaded(loaderWith(CONDITIONS))
                    .shouldApplyMixin("net.minecraft.Screen", UNLISTED_MIXIN)).isTrue();

            String skipping = "{\"conditions\": {}, \"defaultDecision\": \"skip\"}";
            assertThat(loaded(loaderWith(skipping))
                    .shouldApplyMixin("net.minecraft.Screen", UNLISTED_MIXIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("fail-open")
    class FailOpen {

        @Test
        @DisplayName("a config with no omni block applies everything")
        void appliesEverythingWithoutAnOmniBlock() throws IOException {
            ConditionalMixinPlugin plugin = loaded(loaderWith(null));

            assertThat(plugin.conditions()).isEmpty();
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
        }

        @Test
        @DisplayName("malformed JSON applies everything rather than aborting the launch")
        void survivesMalformedJson() throws IOException {
            FakeFabricLoader loader = loaderWith(CONDITIONS);
            loader.withFile(PAYLOAD, CONFIG, "{ this is not json");

            ConditionalMixinPlugin plugin = loaded(loader);

            // A mixin applied when it should not have been fails loudly at its injection point. One
            // silently skipped produces a mod that starts cleanly and does nothing.
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
        }

        @Test
        @DisplayName("an invalid single condition costs only that mixin's condition")
        void survivesOneBrokenCondition() throws IOException {
            String block = "{\"conditions\": {"
                    + "\"ClothConfigScreenMixin\": {\"version\": \"not a version range\"},"
                    + "\"JeiPluginMixin\": {\"requireMod\": \"jei\"}}}";

            ConditionalMixinPlugin plugin = loaded(loaderWith(block));

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
            // The other condition was still read and still applies.
            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", JEI_MIXIN)).isFalse();
        }

        @Test
        @DisplayName("an unknown requireEnv is ignored instead of throwing")
        void ignoresAnUnknownEnvironment() throws IOException {
            String block = "{\"conditions\": {"
                    + "\"ClothConfigScreenMixin\": {\"requireEnv\": \"dedicated\"}}}";

            assertThat(loaded(loaderWith(block))
                    .shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
        }

        @Test
        @DisplayName("a package no config declares applies everything")
        void survivesAnUnknownPackage() throws IOException {
            ConditionalMixinPlugin plugin = new ConditionalMixinPlugin(loaderWith(CONDITIONS));
            plugin.onLoad("com.example.nothing.here");

            assertThat(plugin.shouldApplyMixin("net.minecraft.Screen", CLOTH_MIXIN)).isTrue();
        }
    }

    @Nested
    @DisplayName("config location")
    class Location {

        @Test
        @DisplayName("the config is found by package, not by file name")
        void findsTheRightConfig() throws IOException {
            ConditionalMixinPlugin plugin = loaded(loaderWith(CONDITIONS));

            assertThat(plugin.configName()).isEqualTo(CONFIG);
            assertThat(plugin.conditions().keySet())
                    .containsExactly("ClothConfigScreenMixin", "JeiPluginMixin");
            assertThat(plugin.defaultDecision()).isTrue();
        }

        @Test
        @DisplayName("a foreign mod with unreadable metadata does not break the search")
        void toleratesAForeignModWithBrokenMetadata() throws IOException {
            FakeFabricLoader loader = loaderWith(CONDITIONS);
            Path otherRoot = Files.createDirectories(tempDir.resolve("othermod"));
            loader.withMod("othermod", "1.0.0", otherRoot);
            loader.withFile("othermod", "fabric.mod.json", "{ broken");

            ConditionalMixinPlugin plugin = loaded(loader);

            assertThat(plugin.configName()).isEqualTo(CONFIG);
            assertThat(plugin.conditions()).hasSize(2);
        }
    }

    @Test
    @DisplayName("the plugin adds no mixins and overrides no refmap")
    void staysOutOfMixinsWay() throws IOException {
        ConditionalMixinPlugin plugin = loaded(loaderWith(CONDITIONS));

        // Returning a refmap here would override the payload's own, which is the one the Mixin
        // annotation processor actually produced; adding mixins would hide them from the validator.
        assertThat(plugin.getRefMapperConfig()).isNull();
        assertThat(plugin.getMixins()).isEmpty();
    }
}
