package dev.fabricmultiloader.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.OmniFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeInfoTest {

    @Test
    void modIdMatchesTheFormatConstant() {
        assertThat(RuntimeInfo.MOD_ID).isEqualTo(OmniFormat.RUNTIME_MOD_ID);
    }

    @Test
    @DisplayName("the current schema version is supported")
    void supportsCurrentSchema() {
        assertThat(RuntimeInfo.supportsSchemaVersion(OmniFormat.SCHEMA_VERSION)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("nonsensical schema versions are refused")
    void rejectsNonPositiveSchemaVersions(int schemaVersion) {
        assertThat(RuntimeInfo.supportsSchemaVersion(schemaVersion)).isFalse();
    }

    @Test
    @DisplayName("a newer schema is refused rather than half-interpreted (OMNI-2002)")
    void rejectsNewerSchema() {
        assertThat(RuntimeInfo.supportsSchemaVersion(RuntimeInfo.MAX_SUPPORTED_SCHEMA_VERSION + 1))
                .isFalse();
    }
}
