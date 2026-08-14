package com.example.mc1214;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.platform.AbstractPlatform;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformFactory;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.registry.Registries;

/**
 * A platform that constructs cleanly and then fails in a lifecycle hook.
 *
 * <p>Separate from a factory that throws, because the two produce different codes and different
 * advice: a broken factory means the payload cannot start at all, while a broken hook means the
 * adapter's own initialisation failed and the payload id is the useful thing to report.
 */
public final class FailingHookFactory implements PlatformFactory {

    @Override
    public Platform create(ModContext ctx) {
        return new FailingPlatform(ctx);
    }

    static final class FailingPlatform extends AbstractPlatform {

        private final Platform1214.FakeRegistries registries = new Platform1214.FakeRegistries();
        private final Platform1214.FakeNetworking networking = new Platform1214.FakeNetworking();
        private final Platform1214.FakeCommands commands = new Platform1214.FakeCommands();
        private final Platform1214.FakeEvents events = new Platform1214.FakeEvents();

        FailingPlatform(ModContext ctx) {
            super(ctx);
        }

        @Override
        public Registries registries() {
            return registries;
        }

        @Override
        public Networking networking() {
            return networking;
        }

        @Override
        public Commands commands() {
            return commands;
        }

        @Override
        public Events events() {
            return events;
        }

        @Override
        public void onInitialize(ModContext ctx) {
            throw new IllegalStateException("adapter initialisation failed");
        }
    }
}
