package dev.fabricmultiloader.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonParserTest {

    @Nested
    @DisplayName("scalars")
    class Scalars {

        @Test
        void parsesLiterals() {
            assertThat(Json.parse("true").asBoolean()).isTrue();
            assertThat(Json.parse("false").asBoolean()).isFalse();
            assertThat(Json.parse("null").isNull()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
            "0, 0",
            "-0, 0",
            "1, 1",
            "-1, -1",
            "1234567890, 1234567890",
            "-2147483648, -2147483648",
        })
        void parsesIntegers(String input, int expected) {
            assertThat(Json.parse(input).asInt()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1.5", "-1.5", "1e3", "1E3", "1e+3", "1e-3", "0.0", "1.0e10"})
        void parsesDecimalsAndExponents(String input) {
            assertThat(Json.parse(input).isNumber()).isTrue();
            assertThat(Json.parse(input).asDouble()).isEqualTo(Double.parseDouble(input));
        }

        @Test
        @DisplayName("the literal lexeme is preserved for byte-exact round-tripping")
        void keepsTheOriginalNumberLexeme() {
            assertThat(((JsonNumber) Json.parse("1.0")).raw()).isEqualTo("1.0");
            assertThat(((JsonNumber) Json.parse("1e3")).raw()).isEqualTo("1e3");
            assertThat(Json.write(Json.parse("1.500"))).isEqualTo("1.500");
        }

        @Test
        void parsesStringsWithEscapes() {
            assertThat(Json.parse("\"plain\"").asString()).isEqualTo("plain");
            assertThat(Json.parse("\"a\\\"b\"").asString()).isEqualTo("a\"b");
            assertThat(Json.parse("\"a\\\\b\"").asString()).isEqualTo("a\\b");
            assertThat(Json.parse("\"a\\/b\"").asString()).isEqualTo("a/b");
            assertThat(Json.parse("\"\\b\\f\\n\\r\\t\"").asString()).isEqualTo("\b\f\n\r\t");
            assertThat(Json.parse("\"\\u00e4\"").asString()).isEqualTo("ä");
            assertThat(Json.parse("\"\\uD83D\\uDE00\"").asString()).isEqualTo("\uD83D\uDE00");
        }

        @Test
        void parsesNonAsciiLiterally() {
            assertThat(Json.parse("\"Grüße 🎮\"").asString()).isEqualTo("Grüße 🎮");
        }
    }

    @Nested
    @DisplayName("containers")
    class Containers {

        @Test
        void parsesNestedStructures() {
            JsonObject root = Json.parseObject(
                    "{\"formatId\":\"omni/1\",\"payloads\":[{\"id\":\"mc1201\"},{\"id\":\"mc1214\"}]}");

            assertThat(root.getString("formatId")).isEqualTo("omni/1");
            assertThat(root.getArray("payloads")).hasSize(2);
            assertThat(root.getArray("payloads").getObject(1).getString("id")).isEqualTo("mc1214");
        }

        @Test
        void parsesEmptyContainers() {
            assertThat(Json.parseObject("{}").isEmpty()).isTrue();
            assertThat(Json.parse("[]").asArray().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("member order is preserved, because the manifest key order is normative")
        void preservesMemberOrder() {
            JsonObject object = Json.parseObject("{\"z\":1,\"a\":2,\"m\":3}");
            assertThat(object.keys()).containsExactly("z", "a", "m");
        }

        @Test
        void toleratesWhitespaceEverywhere() {
            JsonObject object = Json.parseObject("  {\n\t\"a\" :\r\n [ 1 , 2 ]\n}  ");
            assertThat(object.getArray("a").getInt(1)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("strictness — a permissive parser would hide generator bugs")
    class Strictness {

        @ParameterizedTest
        @ValueSource(strings = {
            "{'a':1}",              // single quotes
            "{a:1}",                // unquoted key
            "{\"a\":1,}",           // trailing comma in an object
            "[1,]",                 // trailing comma in an array
            "{\"a\":1}// comment",  // comment
            "01",                   // leading zero
            "+1",                   // leading plus
            ".5",                   // no integer part
            "5.",                   // no fraction digits
            "1e",                   // empty exponent
            "NaN",
            "Infinity",
            "{\"a\":1}{\"b\":2}",   // trailing content
            "",                     // empty document
            "   ",                  // whitespace only
            "{\"a\"}",              // missing colon and value
            "{\"a\":}",             // missing value
            "[1 2]",                // missing comma
        })
        void rejectsMalformedDocuments(String input) {
            assertThatThrownBy(() -> Json.parse(input))
                    .isInstanceOf(JsonFormatException.class)
                    .satisfies(thrown ->
                            assertThat(((JsonFormatException) thrown).code())
                                    .isEqualTo(ErrorCode.OMNI_3000));
        }

        @Test
        void rejectsDuplicateKeys() {
            JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
            assertThat(thrown.getMessage()).contains("duplicate member key \"a\"");
        }

        @Test
        void rejectsUnescapedControlCharacters() {
            JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> Json.parse("\"a\nb\""));
            assertThat(thrown.getMessage()).contains("unescaped control character U+000A");
        }

        @Test
        void rejectsInvalidEscapes() {
            assertThatThrownBy(() -> Json.parse("\"\\x\""))
                    .isInstanceOf(JsonFormatException.class)
                    .hasMessageContaining("invalid escape sequence");
            assertThatThrownBy(() -> Json.parse("\"\\uZZZZ\""))
                    .isInstanceOf(JsonFormatException.class)
                    .hasMessageContaining("invalid hex digit");
        }

        @Test
        void rejectsUnterminatedString() {
            assertThatThrownBy(() -> Json.parse("\"unterminated"))
                    .isInstanceOf(JsonFormatException.class)
                    .hasMessageContaining("unterminated string");
        }
    }

    @Nested
    @DisplayName("diagnostics")
    class Diagnostics {

        @Test
        @DisplayName("a syntax error reports line, column and quotes the source line with a caret")
        void reportsPositionAndCaret() {
            String document = "{\n  \"formatId\": \"omni/1\",\n  \"schemaVersion\": 01\n}";

            JsonFormatException thrown =
                    catchThrowableOfType(JsonFormatException.class, () -> Json.parse(document));

            assertThat(thrown.location().line()).isEqualTo(3);
            assertThat(thrown.getMessage())
                    .contains("OMNI-3000")
                    .contains("line 3")
                    .contains("leading zeroes")
                    .contains("\"schemaVersion\": 01")
                    .contains("^");
        }

        @Test
        @DisplayName("every parsed value knows its JSON pointer")
        void tracksPointers() {
            JsonObject root = Json.parseObject(
                    "{\"payloads\":[{\"requires\":{\"minecraft\":[\">=1.21.4 <1.21.5\"]}}]}");

            JsonValue range = root.getArray("payloads")
                    .getObject(0)
                    .getObject("requires")
                    .getArray("minecraft")
                    .get(0);

            assertThat(range.location().pointer()).isEqualTo("/payloads/0/requires/minecraft/0");
        }

        @Test
        @DisplayName("keys containing '/' or '~' are escaped per RFC 6901")
        void escapesPointerTokens() {
            JsonObject root = Json.parseObject("{\"a/b\":{\"c~d\":1}}");
            assertThat(root.getObject("a/b").require("c~d").location().pointer())
                    .isEqualTo("/a~1b/c~0d");
        }

        @Test
        @DisplayName("a missing required field names the pointer it would have had")
        void missingFieldReportsPointer() {
            JsonObject root = Json.parseObject("{\"payloads\":[{\"id\":\"mc1201\"}]}");

            JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> root.getArray("payloads").getObject(0).getString("platformFactory"));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3001);
            assertThat(thrown.pointer()).isEqualTo("/payloads/0/platformFactory");
            assertThat(thrown.getMessage()).contains("/payloads/0/platformFactory");
        }

        @Test
        @DisplayName("a wrongly typed field reports expected and actual type")
        void typeMismatchReportsBothTypes() {
            JsonObject root = Json.parseObject("{\"schemaVersion\":\"1\"}");

            JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> root.getInt("schemaVersion"));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3002);
            assertThat(thrown.getMessage())
                    .contains("expected")
                    .contains("number")
                    .contains("found")
                    .contains("string")
                    .contains("/schemaVersion");
        }

        @Test
        void nonIntegralNumbersAreNotSilentlyTruncated() {
            JsonObject root = Json.parseObject("{\"size\":1.5}");
            assertThatThrownBy(() -> root.getInt("size"))
                    .isInstanceOf(JsonFormatException.class)
                    .hasMessageContaining("cannot be represented as int");
        }

        @Test
        void everyDiagnosticCarriesADocumentationLink() {
            JsonFormatException thrown =
                    catchThrowableOfType(JsonFormatException.class, () -> Json.parse("{"));
            assertThat(thrown.getMessage()).contains("docs/errors.md#omni-3000");
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        void optionalAccessorsFallBackWithoutThrowing() {
            JsonObject object = Json.parseObject("{\"present\":\"yes\",\"nulled\":null}");

            assertThat(object.optString("present", "fallback")).isEqualTo("yes");
            assertThat(object.optString("absent", "fallback")).isEqualTo("fallback");
            assertThat(object.optString("nulled", "fallback")).isEqualTo("fallback");
            assertThat(object.optInt("absent", 42)).isEqualTo(42);
            assertThat(object.optBoolean("absent", true)).isTrue();
            assertThat(object.optObject("absent")).isNull();
            assertThat(object.optArray("absent")).isNull();
        }

        @Test
        @DisplayName("has() distinguishes an absent member from an explicit null")
        void hasSeesExplicitNulls() {
            JsonObject object = Json.parseObject("{\"nulled\":null}");
            assertThat(object.has("nulled")).isTrue();
            assertThat(object.has("absent")).isFalse();
            assertThat(object.require("nulled").isNull()).isTrue();
        }

        @Test
        void unknownKeysAreReportedForTheValidator() {
            JsonObject object = Json.parseObject("{\"formatId\":\"omni/1\",\"typo\":1,\"other\":2}");
            java.util.Set<String> known = new java.util.HashSet<String>();
            known.add("formatId");
            assertThat(object.unknownKeys(known)).containsExactly("typo", "other");
        }

        @Test
        void arrayIndexOutOfBoundsIsADiagnosticNotAnAioobe() {
            JsonArray array = Json.parse("[1]").asArray();
            JsonFormatException thrown =
                    catchThrowableOfType(JsonFormatException.class, () -> array.get(3));
            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3001);
            assertThat(thrown.getMessage()).contains("array size").contains("1");
        }

        @Test
        void stringListsAreExtractedInOneStep() {
            JsonArray array = Json.parse("[\"a\",\"b\",\"c\"]").asArray();
            assertThat(array.asStringList()).containsExactly("a", "b", "c");
        }
    }
}
