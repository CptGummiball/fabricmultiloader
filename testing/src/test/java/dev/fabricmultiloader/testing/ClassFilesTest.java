package dev.fabricmultiloader.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassFilesTest {

    @ParameterizedTest
    @CsvSource({"8, 52", "11, 55", "17, 61", "21, 65", "25, 69", "30, 74"})
    @DisplayName("major = java + 44 holds in both directions, including Java 25")
    void versionMappingRoundTrips(int javaVersion, int classFileMajor) {
        assertThat(ClassFiles.classFileMajorOf(javaVersion)).isEqualTo(classFileMajor);
        assertThat(ClassFiles.javaVersionOf(classFileMajor)).isEqualTo(javaVersion);
    }

    @Test
    @DisplayName("a well-formed header is read without loading the class")
    void readsMajorVersionFromHeader() throws IOException {
        byte[] header = {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
            0x00, 0x00, // minor
            0x00, 0x45, // major 69 = Java 25
        };
        assertThat(ClassFiles.majorVersionOf(new ByteArrayInputStream(header))).isEqualTo(69);
    }

    @Test
    void rejectsNonClassFiles() {
        byte[] notAClass = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0}; // a zip header
        assertThatThrownBy(() -> ClassFiles.majorVersionOf(new ByteArrayInputStream(notAClass)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a class file");
    }

    @Test
    void rejectsImpossibleVersions() {
        assertThatThrownBy(() -> ClassFiles.javaVersionOf(44))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassFiles.classFileMajorOf(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
