package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.ImplementedByFramework;

/**
 * Collects lines to add to Minecraft crash reports.
 *
 * <p>The framework always contributes which payload was active, the container version and the
 * detected environment. Without that, a crash report from a universal mod says only which mod is
 * installed, not which of its implementations was running — which is the first thing anyone
 * triaging the report needs to know and the one thing they cannot infer.
 *
 * <p>The Minecraft API for this differs per version, so a payload adds the entries and its adapter
 * attaches them.
 */
@ImplementedByFramework
public interface CrashContext {

    /**
     * Adds a line to the report section.
     *
     * @param label the left-hand label
     * @param value the value; evaluated immediately, so it must not itself be able to throw
     */
    void add(String label, String value);
}
