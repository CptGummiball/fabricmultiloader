package com.example.mc1214;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformFactory;

/**
 * The four ways a platform factory can be wrong, each as its own class.
 *
 * <p>They live in {@code com.example.mc1214} so that the package containment check passes and the
 * <em>next</em> check is the one under test. A factory in the wrong package would fail earlier, and
 * a test that passed for that reason would prove nothing about the case it is named after.
 */
public final class BadFactories {

    /** Throws from {@code create} — the ordinary "the mod's own setup failed" case. */
    public static final class Throwing implements PlatformFactory {
        @Override
        public Platform create(ModContext ctx) {
            throw new IllegalStateException("adapter could not start");
        }
    }

    /** Returns {@code null}, which no caller can do anything sensible with. */
    public static final class ReturningNull implements PlatformFactory {
        @Override
        public Platform create(ModContext ctx) {
            return null;
        }
    }

    /** Named as the factory but not one — a manifest pointing at the wrong class. */
    public static final class NotAFactory {
    }

    /** Has no no-argument constructor, so it cannot be instantiated reflectively. */
    public static final class WithoutDefaultConstructor implements PlatformFactory {

        private final String unused;

        /**
         * @param unused makes the implicit no-argument constructor unavailable
         */
        public WithoutDefaultConstructor(String unused) {
            this.unused = unused;
        }

        @Override
        public Platform create(ModContext ctx) {
            throw new IllegalStateException(unused);
        }
    }

    /** Fails in its static initialiser, before any of its code is called. */
    public static final class FailingStaticInitialiser implements PlatformFactory {

        static {
            if (Boolean.TRUE) {
                throw new IllegalStateException("static initialiser exploded");
            }
        }

        @Override
        public Platform create(ModContext ctx) {
            return null;
        }
    }

    private BadFactories() {
        throw new AssertionError("no instances");
    }
}
