package dev.fabricmultiloader.runtime.fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the fake platform and the fake mod write down what happened to them.
 *
 * <p>The lifecycle's guarantees are almost entirely about <em>order</em> — the platform before the
 * mod, services before common code, the registry flush after it — and order is what a mock's
 * verification API expresses least clearly. A flat list of strings compared with
 * {@code containsExactly} says precisely what the specification says.
 *
 * <p>Static, because the classes doing the recording are instantiated reflectively by the code under
 * test and there is nowhere to inject a collaborator. Cleared per test.
 */
public final class Recorder {

    private static final List<String> EVENTS =
            Collections.synchronizedList(new ArrayList<String>());

    /** Records one event. */
    public static void record(String event) {
        EVENTS.add(event);
    }

    /** Everything recorded so far, in order. */
    public static List<String> events() {
        synchronized (EVENTS) {
            return new ArrayList<String>(EVENTS);
        }
    }

    /** Whether an event was recorded. */
    public static boolean sawEvent(String event) {
        return events().contains(event);
    }

    /** Resets between tests. */
    public static void clear() {
        EVENTS.clear();
    }

    private Recorder() {
        throw new AssertionError("no instances");
    }
}
