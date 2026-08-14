package dev.fabricmultiloader.runtime.diag;

import dev.fabricmultiloader.api.platform.CrashContext;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.payload.Environment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lines FabricMultiLoader contributes to a Minecraft crash report.
 *
 * <p>Without them a crash report from a universal mod names the mod and its version and stops
 * there — which is the least useful place to stop, because the same version of the same mod ships
 * a different implementation for every Minecraft version and the report gives no way to tell which
 * one was running. The framework therefore always adds the active payload, the detected environment
 * and the mapping namespace, and the payload may add whatever else it knows.
 *
 * <p>Attaching this to the game's crash report is the payload's job: {@code CrashReportSection} and
 * its neighbours are Minecraft types with signatures that have changed more than once, so the
 * runtime collects the content and the adapter — fifteen lines, one per version — hands it over.
 *
 * <p>The whole point is to be readable in a report written while the game is dying, so values are
 * captured as strings when they are added rather than computed lazily on the way out.
 */
public final class CrashContextImpl implements CrashContext {

    private final Map<String, String> entries = new LinkedHashMap<String, String>();

    /**
     * Builds the context with the framework's own entries already in place.
     *
     * @param manifest the container manifest
     * @param environment the detected environment
     * @param payload the active payload
     * @return a context the payload may add to
     */
    public static CrashContextImpl forContainer(ContainerManifest manifest,
            Environment environment, PayloadDescriptor payload) {
        CrashContextImpl context = new CrashContextImpl();
        context.add("Universal mod", manifest.container().modId()
                + " " + manifest.container().modVersion());
        context.add("Active payload", payload.id()
                + " (" + payload.modId() + " " + payload.modVersion() + ")");
        context.add("Payload built for", "Minecraft " + payload.requires().minecraft()
                + ", Java " + payload.requires().java()
                + ", class file " + payload.classfileMajor());
        context.add("Detected environment", "mc=" + environment.minecraft()
                + " loader=" + environment.fabricLoader()
                + " fabric-api=" + (environment.fabricApi().isUnknown()
                        ? "none" : environment.fabricApi().toString())
                + " java=" + environment.javaMajor()
                + " side=" + environment.side().id());
        context.add("Mapping namespace", payload.mappings().namespace()
                + (environment.isDevelopment() ? " (remapped to named for development)" : ""));
        if (!payload.capabilities().isEmpty()) {
            context.add("Payload capabilities", join(payload.capabilities()));
        }
        return context;
    }

    @Override
    public void add(String label, String value) {
        if (label == null || label.isEmpty()) {
            return;
        }
        entries.put(label, value == null ? "(null)" : value);
    }

    /** Every entry, in insertion order. */
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(entries));
    }

    /** The section title a payload should use when attaching this to a crash report. */
    public String sectionTitle() {
        return "FabricMultiLoader";
    }

    /**
     * The entries as text, for logs and for the diagnostic report.
     *
     * @return one {@code label: value} per line, without a trailing newline
     */
    public String render() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "crash context (" + entries.size() + " entries)";
    }

    private static String join(List<String> values) {
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (String value : sorted) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }
}
