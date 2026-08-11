package dev.fabricmultiloader.format.json;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;

/**
 * Renders the format-layer diagnostics in the normative message layout.
 *
 * <p>Package-private on purpose: these texts are an implementation detail of the parser, and
 * pinning them down in one place is what makes {@code MessagesSnapshotTest} able to catch an
 * accidental wording change in review.
 */
final class JsonMessages {

    static JsonFormatException typeMismatch(JsonValue value, JsonType expected) {
        JsonLocation location = value.location();
        String report = Messages.report(ErrorCode.OMNI_3002)
                .detected("at", location.describe())
                .detected("expected", expected.displayName())
                .detected("found", value.type().displayName())
                .detail("The value at this position has the wrong JSON type.")
                .fix("correct the value, or regenerate the file with ./gradlew buildUniversalJar")
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3002, report, location);
    }

    static JsonFormatException numberNotIntegral(JsonValue value, String targetType, Throwable cause) {
        JsonLocation location = value.location();
        String report = Messages.report(ErrorCode.OMNI_3002)
                .detected("at", location.describe())
                .detected("expected", targetType)
                .detected("found", "number " + value.asRawNumber())
                .detail("The number cannot be represented as " + targetType + ".")
                .fix("use a whole number within the range of " + targetType)
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3002, report, location, cause);
    }

    static JsonFormatException missingMember(JsonLocation containerLocation, String key) {
        String pointer = JsonPointer.child(containerLocation.pointer(), key);
        JsonLocation location = new JsonLocation(
                containerLocation.line(), containerLocation.column(), pointer);
        String report = Messages.report(ErrorCode.OMNI_3001)
                .detected("missing", pointer)
                .detected("in object at", JsonPointer.describe(containerLocation.pointer()))
                .detected("line", containerLocation.hasPosition()
                        ? String.valueOf(containerLocation.line()) : "unknown")
                .detail("A required field is not present.")
                .fix("add the field, or regenerate the file with ./gradlew buildUniversalJar")
                .fix("if this file was hand-edited, compare it against docs/design/part-03-container-format.md")
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3001, report, location);
    }

    static JsonFormatException missingElement(JsonLocation arrayLocation, int index, int size) {
        String pointer = JsonPointer.index(arrayLocation.pointer(), index);
        JsonLocation location = new JsonLocation(arrayLocation.line(), arrayLocation.column(), pointer);
        String report = Messages.report(ErrorCode.OMNI_3001)
                .detected("missing", pointer)
                .detected("array size", String.valueOf(size))
                .detail("An array element was requested beyond the end of the array.")
                .fix("check the array length before indexing into it")
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3001, report, location);
    }

    static JsonFormatException syntax(
            int line, int column, String pointer, String problem, String sourceLine) {
        JsonLocation location = new JsonLocation(line, column, pointer);
        Messages.Builder builder = Messages.report(ErrorCode.OMNI_3000)
                .detected("at", location.describe())
                .detail(problem);
        if (sourceLine != null) {
            builder.detail("");
            builder.detail("  " + sourceLine);
            builder.detail("  " + caret(column));
        }
        String report = builder
                .fix("the file is not valid JSON — if it came from a build, report it as a bug")
                .fix("if it was edited by hand, check for a missing comma, quote or brace")
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3000, report, location);
    }

    static JsonFormatException limitExceeded(
            String limitName, long limit, long actual, int line, int column, String pointer) {
        JsonLocation location = new JsonLocation(line, column, pointer);
        String report = Messages.report(ErrorCode.OMNI_3003)
                .detected("limit", limitName)
                .detected("maximum", String.valueOf(limit))
                .detected("encountered", String.valueOf(actual))
                .detected("at", location.describe())
                .detail("The document exceeds a parser safety limit.")
                .detail("Manifest content is untrusted input; these bounds keep a crafted or")
                .detail("corrupted file from exhausting memory during startup.")
                .fix("if this is a legitimate document, raise the bound via JsonLimits.builder()")
                .fix("otherwise treat the file as corrupted and re-download the mod")
                .build();
        return new JsonFormatException(ErrorCode.OMNI_3003, report, location);
    }

    private static String caret(int column) {
        int spaces = Math.max(0, column - 1);
        StringBuilder out = new StringBuilder(spaces + 1);
        for (int i = 0; i < spaces; i++) {
            out.append(' ');
        }
        return out.append('^').toString();
    }

    private JsonMessages() {
        throw new AssertionError("no instances");
    }
}
