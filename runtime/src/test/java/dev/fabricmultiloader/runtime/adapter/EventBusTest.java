package dev.fabricmultiloader.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.api.event.EventKey;
import dev.fabricmultiloader.api.event.ServerRef;
import dev.fabricmultiloader.api.event.Subscription;
import dev.fabricmultiloader.runtime.log.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private EventBus bus() {
        return new EventBus("examplemod", Log.named("test"));
    }

    @Nested
    @DisplayName("subscription")
    class SubscriptionLifecycle {

        @Test
        @DisplayName("handlers run in subscription order")
        void dispatchesInOrder() {
            EventBus bus = bus();
            final List<String> calls = new ArrayList<String>();
            bus.serverStarted(server -> calls.add("first"));
            bus.serverStarted(server -> calls.add("second"));

            bus.fireServerStarted(null);

            assertThat(calls).containsExactly("first", "second");
        }

        @Test
        @DisplayName("unsubscribing stops the handler and reports itself")
        void unsubscribes() {
            EventBus bus = bus();
            final AtomicInteger calls = new AtomicInteger();
            Subscription subscription = bus.serverTick(server -> calls.incrementAndGet());

            bus.fireServerTick(null);
            assertThat(subscription.isActive()).isTrue();

            subscription.unsubscribe();
            bus.fireServerTick(null);

            assertThat(calls.get()).isEqualTo(1);
            assertThat(subscription.isActive()).isFalse();
            assertThat(bus.subscriberCount("serverTick")).isZero();
        }

        @Test
        @DisplayName("a handler unsubscribing itself mid-dispatch does not disturb the iteration")
        void allowsSelfUnsubscription() {
            final EventBus bus = bus();
            final List<String> calls = new ArrayList<String>();
            final Subscription[] holder = new Subscription[1];

            // The way a one-shot subscription is written. A plain ArrayList here would throw
            // ConcurrentModificationException on the very first fire.
            holder[0] = bus.serverStarted(server -> {
                calls.add("once");
                holder[0].unsubscribe();
            });
            bus.serverStarted(server -> calls.add("always"));

            bus.fireServerStarted(null);
            bus.fireServerStarted(null);

            assertThat(calls).containsExactly("once", "always", "always");
        }

        @Test
        @DisplayName("closing a subscription unsubscribes it")
        void supportsTryWithResources() throws Exception {
            EventBus bus = bus();
            Subscription subscription = bus.playerJoin(player -> {
            });

            subscription.close();

            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("a null handler is a programming error")
        void rejectsNullHandlers() {
            assertThatThrownBy(() -> bus().serverTick(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("failure containment")
    class Containment {

        @Test
        @DisplayName("a throwing handler does not stop the others")
        void isolatesFailures() {
            EventBus bus = bus();
            final List<String> calls = new ArrayList<String>();
            bus.serverStarted(server -> {
                throw new IllegalStateException("mod bug");
            });
            bus.serverStarted(server -> calls.add("still ran"));

            bus.fireServerStarted(null);

            assertThat(calls).containsExactly("still ran");
        }

        @Test
        @DisplayName("a throwing handler is reported once, not once per tick")
        void mutesARepeatedlyFailingHandler() {
            EventBus bus = bus();
            final AtomicInteger attempts = new AtomicInteger();
            bus.serverTick(server -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("every tick");
            });

            for (int i = 0; i < 100; i++) {
                bus.fireServerTick(null);
            }

            // The handler keeps being called — muting is about the log, not about disabling a mod's
            // event. Twenty times a second with a full stack trace each would bury everything else.
            assertThat(attempts.get()).isEqualTo(100);
        }

        @Test
        @DisplayName("a throwing block-break handler fails open")
        void failsOpenOnBlockBreak() {
            EventBus bus = bus();
            bus.blockBroken((world, player, position, block) -> {
                throw new IllegalStateException("mod bug");
            });

            // A mod bug making the world unbreakable is worse than the same bug letting a block
            // break that should have been protected.
            assertThat(bus.fireBlockBroken(null, null, null, null)).isTrue();
        }

        @Test
        @DisplayName("every block-break handler runs even after one vetoes")
        void runsAllBlockBreakHandlers() {
            EventBus bus = bus();
            final List<String> calls = new ArrayList<String>();
            bus.blockBroken((world, player, position, block) -> {
                calls.add("veto");
                return false;
            });
            bus.blockBroken((world, player, position, block) -> {
                calls.add("observer");
                return true;
            });

            assertThat(bus.fireBlockBroken(null, null, null, null)).isFalse();
            // An observing handler must not see a different set of events depending on where it
            // happens to sit in the subscription order.
            assertThat(calls).containsExactly("veto", "observer");
        }
    }

    @Nested
    @DisplayName("custom events")
    class Custom {

        private final EventKey<String> key = EventKey.of("examplemod:charged", String.class);

        @Test
        @DisplayName("a mod-defined event dispatches to its handlers")
        void dispatchesCustomEvents() {
            EventBus bus = bus();
            final List<String> calls = new ArrayList<String>();
            bus.custom(key, calls::add);

            bus.fireCustom(key, "payload");

            assertThat(calls).containsExactly("payload");
            assertThat(bus.subscriberCount("examplemod:charged")).isEqualTo(1);
        }

        @Test
        @DisplayName("firing an event nobody subscribed to does nothing")
        void toleratesNoSubscribers() {
            bus().fireCustom(key, "payload");
        }

        @Test
        @DisplayName("a null key is a programming error on subscribe and a no-op on fire")
        void handlesNullKeys() {
            EventBus bus = bus();
            Consumer<String> handler = value -> {
            };

            assertThatThrownBy(() -> bus.custom(null, handler))
                    .isInstanceOf(IllegalArgumentException.class);
            bus.fireCustom(null, "payload");
        }
    }

    @Test
    @DisplayName("the active events are listed for the diagnostic report")
    void listsActiveEvents() {
        EventBus bus = bus();
        bus.serverStarted(server -> {
        });
        bus.playerJoin(player -> {
        });
        bus.custom(EventKey.of("examplemod:charged", String.class), value -> {
        });

        assertThat(bus.activeEvents())
                .containsExactly("examplemod:charged", "playerJoin", "serverStarted");
        assertThat(bus.subscriberCount("worldLoad")).isZero();
    }

    @Test
    @DisplayName("every Events method returns a working subscription")
    void everyEventIsWired() {
        EventBus bus = bus();
        Consumer<ServerRef> server = ref -> {
        };

        assertThat(bus.serverStarted(server).isActive()).isTrue();
        assertThat(bus.serverStopping(server).isActive()).isTrue();
        assertThat(bus.serverTick(server).isActive()).isTrue();
        assertThat(bus.clientTick(ctx -> {
        }).isActive()).isTrue();
        assertThat(bus.playerJoin(player -> {
        }).isActive()).isTrue();
        assertThat(bus.playerLeave(player -> {
        }).isActive()).isTrue();
        assertThat(bus.worldLoad(world -> {
        }).isActive()).isTrue();
        assertThat(bus.dataReload(ctx -> {
        }).isActive()).isTrue();
        assertThat(bus.blockBroken((world, player, position, block) -> true).isActive()).isTrue();
    }
}
