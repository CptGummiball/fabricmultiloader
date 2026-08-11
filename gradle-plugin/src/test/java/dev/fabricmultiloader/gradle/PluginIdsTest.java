package dev.fabricmultiloader.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PluginIdsTest {

    @Test
    @DisplayName("all plugin ids share the project namespace")
    void idsAreNamespaced() {
        assertThat(PluginIds.all()).hasSize(4).allSatisfy(id ->
                assertThat(id).startsWith("dev.fabricmultiloader."));
    }

    @Test
    void idsAreUnique() {
        assertThat(Set.of(PluginIds.all())).hasSize(PluginIds.all().length);
    }

    @Test
    @DisplayName("the matrix path is relative and inside gradle/")
    void matrixFileIsARelativeGradlePath() {
        assertThat(PluginIds.MATRIX_FILE)
                .isEqualTo("gradle/fabricmultiloader.toml")
                .doesNotStartWith("/")
                .doesNotContain("..");
    }
}
