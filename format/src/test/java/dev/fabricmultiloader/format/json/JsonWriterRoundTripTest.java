package dev.fabricmultiloader.format.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonWriterRoundTripTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "true",
        "0",
        "-1",
        "1.500",
        "1e3",
        "\"\"",
        "\"text\"",
        "[]",
        "{}",
        "[1,2,3]",
        "{\"a\":1}",
        "{\"a\":{\"b\":[1,{\"c\":null}]}}",
        "\"unicode: \\u00e4 \\uD83D\\uDE00\"",
        "\"escapes: \\\" \\\\ \\n \\t\"",
    })
    @DisplayName("parse -> write -> parse is value-identical")
    void roundTripsThroughTheWriter(String input) {
        JsonValue first = Json.parse(input);
        JsonValue second = Json.parse(Json.write(first));
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("writing is idempotent — the second pass changes nothing")
    void writingIsIdempotent() {
        String source = "{\"formatId\":\"omni/1\",\"payloads\":[{\"id\":\"mc1201\",\"priority\":0}]}";
        String once = Json.write(Json.parse(source));
        String twice = Json.write(Json.parse(once));
        assertThat(twice).isEqualTo(once);
    }

    @Test
    @DisplayName("the canonical form is two-space indented with LF and no trailing whitespace")
    void producesTheCanonicalForm() {
        JsonObject root = new JsonObject()
                .set("formatId", "omni/1")
                .set("schemaVersion", 1L)
                .set("payloads", new JsonArray()
                        .add(new JsonObject().set("id", "mc1201").set("priority", 0L)));

        assertThat(Json.write(root)).isEqualTo(
                "{\n"
                        + "  \"formatId\": \"omni/1\",\n"
                        + "  \"schemaVersion\": 1,\n"
                        + "  \"payloads\": [\n"
                        + "    {\n"
                        + "      \"id\": \"mc1201\",\n"
                        + "      \"priority\": 0\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}");
    }

    @Test
    void documentFormAddsExactlyOneTrailingNewline() {
        String document = Json.writeDocument(new JsonObject().set("a", 1L));
        assertThat(document).isEqualTo("{\n  \"a\": 1\n}\n");
        assertThat(document).doesNotEndWith("\n\n");
    }

    @Test
    void compactFormHasNoWhitespace() {
        JsonValue value = Json.parse("{\"a\": [1, 2], \"b\": {\"c\": true}}");
        assertThat(Json.writeCompact(value)).isEqualTo("{\"a\":[1,2],\"b\":{\"c\":true}}");
    }

    @Test
    @DisplayName("empty containers stay on one line")
    void emptyContainersAreCompact() {
        JsonObject root = new JsonObject()
                .set("emptyObject", new JsonObject())
                .set("emptyArray", new JsonArray());
        assertThat(Json.write(root))
                .isEqualTo("{\n  \"emptyObject\": {},\n  \"emptyArray\": []\n}");
    }

    @Test
    @DisplayName("'/' is not escaped — escaping it is legal but ruins every path in a diff")
    void doesNotEscapeSolidus() {
        assertThat(Json.write(JsonString.of("META-INF/jars/x.jar")))
                .isEqualTo("\"META-INF/jars/x.jar\"");
    }

    @Test
    @DisplayName("control characters are escaped, printable non-ASCII is not")
    void escapesOnlyWhatRfc8259Requires() {
        assertThat(Json.write(JsonString.of("a\u0001b"))).isEqualTo("\"a\\u0001b\"");
        assertThat(Json.write(JsonString.of("Grüße"))).isEqualTo("\"Grüße\"");
        assertThat(Json.write(JsonString.of("tab\there"))).isEqualTo("\"tab\\there\"");
    }

    @Test
    @DisplayName("constructed and parsed documents produce identical output")
    void constructedMatchesParsed() {
        JsonObject constructed = new JsonObject()
                .set("id", "examplemod")
                .set("strict", true)
                .set("payloads", new JsonArray().add("mc1201").add("mc1214"));

        JsonValue parsed = Json.parse(
                "{\"id\":\"examplemod\",\"strict\":true,\"payloads\":[\"mc1201\",\"mc1214\"]}");

        assertThat(Json.write(constructed)).isEqualTo(Json.write(parsed));
        assertThat(constructed).isEqualTo(parsed);
    }

    @Test
    void toStringUsesTheCanonicalForm() {
        JsonValue value = Json.parse("{\"a\":1}");
        assertThat(value.toString()).isEqualTo(Json.write(value));
    }
}
