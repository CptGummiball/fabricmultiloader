package dev.fabricmultiloader.format.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The normative registry of FabricMultiLoader diagnostics.
 *
 * <p>Every failure path in the framework carries one of these codes, and every code has a section
 * in {@code docs/errors.md} — {@code ErrorCodeDocumentationTest} fails if either side is missing.
 * That is what makes a message searchable years after it was printed into somebody's log.
 *
 * <p>Ranges:
 * <ul>
 *   <li><b>1xxx</b> build time — the Gradle plugin and the validator</li>
 *   <li><b>2xxx</b> runtime — bootstrap, payload activation, lifecycle</li>
 *   <li><b>3xxx</b> format — JSON parsing, manifest reading, version strings</li>
 *   <li><b>4xxx</b> API misuse — a programming error in the mod, not in the framework</li>
 * </ul>
 *
 * <p><b>Codes are never reused.</b> A retired code keeps its documentation section marked
 * "removed in x.y" so that old logs stay interpretable.
 *
 * <p>This enum is the registry, not a promise of implementation: a code exists here from the
 * moment it is specified, and the step that raises it is noted in the implementation plan.
 */
public enum ErrorCode {

    // ------------------------------------------------------------------ 1xxx build time
    OMNI_1001(Severity.ERROR, "matrix file missing or unreadable"),
    OMNI_1002(Severity.ERROR, "unknown field in the Omni manifest"),
    OMNI_1003(Severity.ERROR, "attempt to disable a non-disableable validator rule"),
    OMNI_1010(Severity.ERROR, "overlapping payload domains with equal priority"),
    OMNI_1011(Severity.ERROR, "manifest constraints differ from the payload fabric.mod.json"),
    OMNI_1012(Severity.ERROR, "two payloads differ only in requires.mods"),
    OMNI_1013(Severity.INFO, "gap in Minecraft version coverage"),
    OMNI_1014(Severity.ERROR, "container depends.java is not the minimum of the payloads"),
    OMNI_1015(Severity.ERROR, "payload entirely shadowed by higher-priority payloads"),
    OMNI_1021(Severity.ERROR, "hand-written fabric.mod.json in module resources"),
    OMNI_1022(Severity.ERROR, "payload contains a container manifest"),
    OMNI_1023(Severity.ERROR, "container contains assets/ or data/ entries"),
    OMNI_1024(Severity.ERROR, "container declares mixins or an access widener"),
    OMNI_1030(Severity.ERROR, "mixin config, refmap or access widener name is not unique"),
    OMNI_1031(Severity.ERROR, "referenced refmap missing"),
    OMNI_1032(Severity.ERROR, "refmap references classes absent from the payload"),
    OMNI_1033(Severity.WARNING, "refmap without a matching mixin config"),
    OMNI_1034(Severity.ERROR, "mixin package violates the naming convention"),
    OMNI_1035(Severity.ERROR, "conditional mixin plugin isolation violated"),
    OMNI_1036(Severity.ERROR, "forbidden reference to a custom ClassLoader or loader internals"),
    OMNI_1040(Severity.ERROR, "container class exceeds the bytecode baseline"),
    OMNI_1041(Severity.ERROR, "payload class has an unexpected class file version"),
    OMNI_1042(Severity.ERROR, "container class references Minecraft, Fabric API or Mixin"),
    OMNI_1043(Severity.ERROR, "container class outside the declared common packages"),
    OMNI_1044(Severity.ERROR, "package overlap between payloads or with common"),
    OMNI_1045(Severity.ERROR, "client reference outside a client-only package"),
    OMNI_1046(Severity.ERROR, "class file version incompatible with requires.java"),
    OMNI_1047(Severity.ERROR, "baseline java is not the minimum of the payload requirements"),
    OMNI_1048(Severity.WARNING, "nested library exceeds the payload class file version"),
    OMNI_1049(Severity.ERROR, "multi-release artifacts in the container"),
    OMNI_1050(Severity.WARNING, "open upper Minecraft bound"),
    OMNI_1051(Severity.WARNING, "java minimum below the Minecraft requirement"),
    OMNI_1060(Severity.ERROR, "reproducibility violation"),
    OMNI_1070(Severity.WARNING, "resource digest differs between payloads"),
    OMNI_1080(Severity.ERROR, "mapping inconsistency inside a payload"),
    OMNI_1081(Severity.ERROR, "shared versions use different mapping providers"),
    OMNI_1082(Severity.ERROR, "access widener namespace is not intermediary"),
    OMNI_1083(Severity.WARNING, "pinned mapping layer"),
    OMNI_1090(Severity.ERROR, "required toolchain JDK unavailable"),
    OMNI_1100(Severity.ERROR, "mixin config package prefix mismatch"),
    OMNI_1101(Severity.ERROR, "mixin class named in a config does not exist"),
    OMNI_1102(Severity.ERROR, "mixin class not named in any config"),
    OMNI_1103(Severity.ERROR, "refmap invalid or foreign"),
    OMNI_1104(Severity.ERROR, "mixin compatibilityLevel exceeds the payload java requirement"),
    OMNI_1105(Severity.ERROR, "client mixin registered outside a client config"),
    OMNI_1106(Severity.ERROR, "client reference in a non-client mixin config"),
    OMNI_1107(Severity.ERROR, "mixin config is not marked required"),
    OMNI_1108(Severity.ERROR, "container declares mixins"),
    OMNI_1109(Severity.ERROR, "mixin config present but not registered"),
    OMNI_1110(Severity.ERROR, "registered mixin config file missing"),
    OMNI_1120(Severity.ERROR, "access widener source has the wrong namespace header"),
    OMNI_1121(Severity.WARNING, "access widener target not found in the mappings"),
    OMNI_1122(Severity.WARNING, "access widener entry for a non-Minecraft class"),
    OMNI_1123(Severity.ERROR, "access widener declaration inconsistent"),
    OMNI_1124(Severity.WARNING, "access widener file is not deterministically sorted"),
    OMNI_1130(Severity.WARNING, "declared capability without an implementation"),
    OMNI_1140(Severity.ERROR, "duplicate entrypoint declaration"),
    OMNI_1141(Severity.ERROR, "no common entrypoint declared"),
    OMNI_1150(Severity.ERROR, "common-reachable code references a client package"),
    OMNI_1160(Severity.ERROR, "build target version outside its own runtime range"),
    OMNI_1161(Severity.ERROR, "unknown key in the matrix file"),
    OMNI_1162(Severity.ERROR, "matrix entry without a project directory"),
    OMNI_1163(Severity.ERROR, "project directory without a matrix entry"),
    OMNI_1170(Severity.ERROR, "duplicate entry during container assembly"),
    OMNI_1180(Severity.ERROR, "declared mod dependency is not a Fabric mod"),
    OMNI_1181(Severity.ERROR, "Fabric mod embedded as a common library"),
    OMNI_1182(Severity.WARNING, "second Fabric API version inside a payload"),
    OMNI_1183(Severity.ERROR, "forbidden library reference in the common module"),
    OMNI_1184(Severity.WARNING, "Kotlin without fabric-language-kotlin"),
    OMNI_1185(Severity.WARNING, "Minecraft-dependent version in the build version catalog"),
    OMNI_1186(Severity.ERROR, "class shadowing between shared and a version module"),
    OMNI_1187(Severity.ERROR, "shared versions use different java release levels"),
    OMNI_1200(Severity.ERROR, "undeclared resource override"),
    OMNI_1201(Severity.ERROR, "mixin, access widener or refmap in common resources"),
    OMNI_1202(Severity.ERROR, "datagen entrypoint in a release payload"),
    OMNI_1204(Severity.ERROR, "resource symlink escapes the project"),

    // ------------------------------------------------------------------ 2xxx runtime
    OMNI_2001(Severity.ERROR, "container manifest missing or unparseable"),
    OMNI_2002(Severity.ERROR, "container requires a newer FabricMultiLoader runtime"),
    OMNI_2003(Severity.ERROR, "no payload matches this environment"),
    OMNI_2004(Severity.ERROR, "several payloads active simultaneously"),
    OMNI_2010(Severity.ERROR, "Minecraft mod container absent"),
    OMNI_2011(Severity.ERROR, "payload descriptor contradicts the container manifest"),
    OMNI_2012(Severity.ERROR, "manifest mod id does not match the carrying mod"),
    OMNI_2013(Severity.ERROR, "payload integrity check failed"),
    OMNI_2020(Severity.ERROR, "platform factory class not found"),
    OMNI_2021(Severity.ERROR, "platform factory threw an exception"),
    OMNI_2022(Severity.ERROR, "platform factory does not implement PlatformFactory"),
    OMNI_2023(Severity.ERROR, "platform factory returned null"),
    OMNI_2024(Severity.ERROR, "platform factory outside the payload packages"),
    OMNI_2030(Severity.ERROR, "common entrypoint class not found"),
    OMNI_2031(Severity.ERROR, "common entrypoint threw an exception"),
    OMNI_2032(Severity.ERROR, "entrypoint outside the declared common packages"),
    OMNI_2040(Severity.ERROR, "payload lifecycle hook threw an exception"),
    OMNI_2050(Severity.WARNING, "runtime classes loaded from an unexpected source"),
    OMNI_2100(Severity.INFO, "standalone payload without a container"),
    OMNI_2101(Severity.WARNING, "lenient mode — the mod stays deactivated"),
    OMNI_2200(Severity.WARNING, "conditional mixin config unreadable"),
    OMNI_2201(Severity.INFO, "conditional mixin decision"),

    // ------------------------------------------------------------------ 3xxx format
    OMNI_3000(Severity.ERROR, "malformed JSON document"),
    OMNI_3001(Severity.ERROR, "required field missing"),
    OMNI_3002(Severity.ERROR, "field has the wrong type"),
    OMNI_3003(Severity.ERROR, "input limit exceeded"),
    OMNI_3004(Severity.ERROR, "invalid identifier or unsafe path"),
    OMNI_3010(Severity.WARNING, "version string unparseable"),
    OMNI_3011(Severity.ERROR, "invalid version predicate"),

    // ------------------------------------------------------------------ 4xxx API misuse
    OMNI_4001(Severity.ERROR, "invalid lifecycle transition"),
    OMNI_4002(Severity.ERROR, "call made in the wrong lifecycle phase"),
    OMNI_4010(Severity.ERROR, "service requested but never registered"),
    OMNI_4011(Severity.ERROR, "capability used without checking availability"),
    OMNI_4012(Severity.ERROR, "unwrap called with the wrong target type"),
    OMNI_4013(Severity.ERROR, "channel used from the wrong side");

    /** Which subsystem raises a code, derived from its numeric range. */
    public enum Category {
        /** 1xxx — the Gradle plugin and the validator. */
        BUILD,
        /** 2xxx — the runtime: bootstrap, payload activation, lifecycle. */
        RUNTIME,
        /** 3xxx — the format layer: JSON, manifests, version strings. */
        FORMAT,
        /** 4xxx — misuse of the developer API. */
        API
    }

    private static final Map<String, ErrorCode> BY_ID;

    static {
        Map<String, ErrorCode> byId = new LinkedHashMap<String, ErrorCode>();
        for (ErrorCode code : values()) {
            byId.put(code.id(), code);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private final Severity severity;
    private final String title;
    private final int number;
    private final String id;

    ErrorCode(Severity severity, String title) {
        this.severity = severity;
        this.title = title;
        this.number = Integer.parseInt(name().substring("OMNI_".length()));
        this.id = "OMNI-" + this.number;
    }

    /** The stable, printable identifier, for example {@code OMNI-1040}. */
    public String id() {
        return id;
    }

    /** The numeric part, for example {@code 1040}. */
    public int number() {
        return number;
    }

    /** A one-line summary, used as the message title. */
    public String title() {
        return title;
    }

    /** Whether this diagnostic aborts by default. */
    public Severity severity() {
        return severity;
    }

    /** The subsystem this code belongs to, derived from its range. */
    public Category category() {
        switch (number / 1000) {
            case 1:
                return Category.BUILD;
            case 2:
                return Category.RUNTIME;
            case 3:
                return Category.FORMAT;
            case 4:
                return Category.API;
            default:
                throw new IllegalStateException("error code outside every defined range: " + id);
        }
    }

    /** The documentation anchor for this code, for example {@code omni-1040}. */
    public String docAnchor() {
        return id.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Looks a code up by its printable id.
     *
     * @param id for example {@code "OMNI-1040"}
     * @return the code, or {@code null} if unknown — callers decide whether an unknown code from a
     *     newer version is an error or something to pass through
     */
    public static ErrorCode byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** All codes keyed by printable id, in declaration order. */
    public static Map<String, ErrorCode> all() {
        return BY_ID;
    }

    @Override
    public String toString() {
        return id + "  " + title;
    }
}
