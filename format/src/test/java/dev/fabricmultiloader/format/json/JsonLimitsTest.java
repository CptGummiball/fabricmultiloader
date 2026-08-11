package dev.fabricmultiloader.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Manifest content is untrusted: a universal JAR can be tampered with, repacked or truncated.
 * These bounds keep a crafted document from exhausting memory or the stack during {@code preLaunch},
 * where a failure is hardest to diagnose.
 */
class JsonLimitsTest {

    @Test
    @DisplayName("nesting depth is bounded, so a crafted document cannot blow the stack")
    void rejectsExcessiveDepth() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            deep.append('[');
        }
        for (int i = 0; i < 200; i++) {
            deep.append(']');
        }

        JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> Json.parse(deep.toString()));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3003);
        assertThat(thrown.getMessage()).contains("nesting depth").contains("64");
    }

    @Test
    @DisplayName("a deeply nested document within the limit still parses")
    void acceptsDepthAtTheLimit() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            deep.append('[');
        }
        for (int i = 0; i < 64; i++) {
            deep.append(']');
        }
        assertThat(Json.parse(deep.toString()).isArray()).isTrue();
    }

    @Test
    void rejectsTooManyArrayElements() {
        JsonLimits limits = JsonLimits.builder().maxContainerEntries(3).build();

        assertThat(Json.parse("[1,2,3]", limits).asArray()).hasSize(3);
        assertThatThrownBy(() -> Json.parse("[1,2,3,4]", limits))
                .isInstanceOf(JsonFormatException.class)
                .hasMessageContaining("elements in one array");
    }

    @Test
    void rejectsTooManyObjectMembers() {
        JsonLimits limits = JsonLimits.builder().maxContainerEntries(2).build();

        assertThatThrownBy(() -> Json.parse("{\"a\":1,\"b\":2,\"c\":3}", limits))
                .isInstanceOf(JsonFormatException.class)
                .hasMessageContaining("members in one object");
    }

    @Test
    void rejectsOverlongStrings() {
        JsonLimits limits = JsonLimits.builder().maxStringLength(8).build();

        assertThat(Json.parse("\"12345678\"", limits).asString()).hasSize(8);
        assertThatThrownBy(() -> Json.parse("\"123456789\"", limits))
                .isInstanceOf(JsonFormatException.class)
                .hasMessageContaining("characters in one string");
    }

    @Test
    void rejectsOverlongDocuments() {
        JsonLimits limits = JsonLimits.builder().maxDocumentChars(10).build();

        JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> Json.parse("{\"key\":\"a longer value\"}", limits));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3003);
        assertThat(thrown.getMessage()).contains("document size");
    }

    @Test
    void rejectsOverlongStreams() {
        JsonLimits limits = JsonLimits.builder().maxDocumentChars(4).build();
        byte[] bytes = "{\"a\":1}".getBytes(Charset.forName("UTF-8"));

        assertThatThrownBy(() -> Json.parse(new ByteArrayInputStream(bytes), limits))
                .isInstanceOf(JsonFormatException.class)
                .hasMessageContaining("document size");
    }

    @Test
    @DisplayName("a limit diagnostic explains why the bound exists and how to raise it")
    void limitDiagnosticIsActionable() {
        JsonLimits limits = JsonLimits.builder().maxContainerEntries(1).build();

        JsonFormatException thrown = catchThrowableOfType(JsonFormatException.class, () -> Json.parse("[1,2]", limits));

        assertThat(thrown.getMessage())
                .contains("untrusted input")
                .contains("JsonLimits.builder()")
                .contains("docs/errors.md#omni-3003");
    }

    @Test
    void unlimitedDisablesEveryBound() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            deep.append('[');
        }
        for (int i = 0; i < 500; i++) {
            deep.append(']');
        }
        assertThat(Json.parse(deep.toString(), JsonLimits.UNLIMITED).isArray()).isTrue();
    }

    @Test
    void buildersRejectNonPositiveBounds() {
        assertThatThrownBy(() -> JsonLimits.builder().maxDepth(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepth");
    }

    @Test
    void streamsAreParsedAsUtf8AndToleratesABom() {
        byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
            '{', '"', 'a', '"', ':', '"', 'G', 'r', (byte) 0xC3, (byte) 0xBC, 'n', '"', '}'};

        JsonValue parsed = Json.parse(new ByteArrayInputStream(withBom), JsonLimits.DEFAULT);
        assertThat(parsed.asObject().getString("a")).isEqualTo("Grün");
    }
}
