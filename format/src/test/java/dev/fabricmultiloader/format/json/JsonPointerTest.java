package dev.fabricmultiloader.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonPointerTest {

    @ParameterizedTest
    @CsvSource({
        "plain,        plain",
        "a/b,          a~1b",
        "a~b,          a~0b",
        "a~1b,         a~01b",
        "'',           ''",
    })
    @DisplayName("escaping follows RFC 6901: '~' first, then '/'")
    void escapesTokens(String raw, String escaped) {
        assertThat(JsonPointer.escape(raw)).isEqualTo(escaped);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a/b", "a~b", "a~1b", "~", "/", "~0", "~1", "a/b~c/d"})
    @DisplayName("escape and unescape are exact inverses — the order of replacements matters")
    void escapeUnescapeRoundTrips(String raw) {
        assertThat(JsonPointer.unescape(JsonPointer.escape(raw))).isEqualTo(raw);
    }

    @Test
    void buildsChildPointers() {
        assertThat(JsonPointer.child(JsonPointer.ROOT, "payloads")).isEqualTo("/payloads");
        assertThat(JsonPointer.index("/payloads", 2)).isEqualTo("/payloads/2");
        assertThat(JsonPointer.child("/payloads/2", "requires")).isEqualTo("/payloads/2/requires");
        assertThat(JsonPointer.child("/a", "b/c")).isEqualTo("/a/b~1c");
    }

    @Test
    void splitsIntoUnescapedTokens() {
        List<String> tokens = JsonPointer.split("/payloads/2/requires/minecraft");
        assertThat(tokens).isEqualTo(Arrays.asList("payloads", "2", "requires", "minecraft"));

        assertThat(JsonPointer.split("/a~1b/c~0d")).isEqualTo(Arrays.asList("a/b", "c~d"));
        assertThat(JsonPointer.split(JsonPointer.ROOT)).isEmpty();
    }

    @Test
    @DisplayName("an empty final token is preserved — '/a/' addresses a member named ''")
    void preservesEmptyTokens() {
        assertThat(JsonPointer.split("/a/")).isEqualTo(Arrays.asList("a", ""));
    }

    @Test
    void rejectsPointersNotStartingWithSlash() {
        assertThatThrownBy(() -> JsonPointer.split("payloads/2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with '/'");
    }

    @Test
    void describesTheRootReadably() {
        assertThat(JsonPointer.describe(JsonPointer.ROOT)).isEqualTo("(root)");
        assertThat(JsonPointer.describe("/a")).isEqualTo("/a");
    }

    @Test
    void locationDescribesPathAndPosition() {
        assertThat(new JsonLocation(12, 5, "/payloads/0").describe())
                .isEqualTo("/payloads/0 (line 12, column 5)");
        assertThat(new JsonLocation(-1, -1, "/a").describe()).isEqualTo("/a");
        assertThat(JsonLocation.UNKNOWN.hasPosition()).isFalse();
    }
}
