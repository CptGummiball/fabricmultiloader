package dev.fabricmultiloader.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessorOptionsTest {

    @Test
    @DisplayName("every option is namespaced, so it cannot clash with another processor")
    void optionsAreNamespaced() {
        assertThat(ProcessorOptions.all()).isNotEmpty().allSatisfy(option ->
                assertThat(option).startsWith("omni."));
    }

    @Test
    void optionKeysAreUnique() {
        assertThat(ProcessorOptions.all()).doesNotHaveDuplicates();
    }

    @Test
    void allReturnsADefensiveCopy() {
        String[] first = ProcessorOptions.all();
        first[0] = "tampered";
        assertThat(ProcessorOptions.all()[0]).isEqualTo(ProcessorOptions.MOD_ID);
    }
}
