package dev.fabricmultiloader.format.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafePathsAndIdentifiersTest {

    @Nested
    @DisplayName("path safety — manifest content is untrusted input")
    class Paths {

        @ParameterizedTest
        @ValueSource(strings = {
            "/META-INF/jars/x.jar",              // absolute
            "\\META-INF\\jars\\x.jar",           // windows separators
            "META-INF\\jars\\x.jar",             // mixed separators
            "../META-INF/jars/x.jar",            // parent escape
            "META-INF/../../etc/passwd",         // parent escape mid-path
            "META-INF/jars/../../../x.jar",
            "./META-INF/jars/x.jar",             // current-dir segment
            "META-INF/./jars/x.jar",
            "META-INF//jars/x.jar",              // empty segment
            "C:/windows/system32/x.dll",         // drive qualified
            "META-INF/jars/",                    // directory, not a file
            "",                                  // empty
            "..",
            ".",
            "/",
        })
        @DisplayName("every classic escape pattern is refused")
        void rejectsUnsafePaths(String path) {
            assertThat(SafePaths.isSafeJarPath(path)).as(path).isFalse();
            assertThatThrownBy(() -> SafePaths.requireJarPath(path, "test.field"))
                    .isInstanceOf(OmniException.class)
                    .satisfies(thrown -> assertThat(((OmniException) thrown).code())
                            .isEqualTo(ErrorCode.OMNI_3004));
        }

        @Test
        void rejectsNulBytesAndOverlongPaths() {
            assertThat(SafePaths.isSafeJarPath("META-INF/jars/x\u0000.jar")).isFalse();

            StringBuilder overlong = new StringBuilder("META-INF/jars/");
            for (int i = 0; i < 600; i++) {
                overlong.append('a');
            }
            assertThat(SafePaths.isSafeJarPath(overlong.toString())).isFalse();
        }

        @Test
        @DisplayName("paths outside the roots the format uses are refused even when otherwise safe")
        void rejectsPathsOutsideKnownRoots() {
            assertThat(SafePaths.isSafeJarPath("com/example/Secret.class")).isFalse();
            assertThat(SafePaths.isSafeJarPath("META-INF/MANIFEST.MF")).isFalse();
            assertThat(SafePaths.isSafeJarPath("config/other.json")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "META-INF/jars/examplemod-mc1214.jar",
            "META-INF/jars/nested/deep.jar",
            "omni/payload.json",
            "omni/icon.png",
            "assets/examplemod/lang/en_us.json",
            "data/examplemod/tags/blocks/x.json",
            "fabric.mod.json",
            "META-INF/omni-container.json",
        })
        void acceptsThePathsTheFormatActuallyUses(String path) {
            assertThat(SafePaths.isSafeJarPath(path)).as(path).isTrue();
        }

        @Test
        @DisplayName("relative paths without a root restriction still reject escapes")
        void relativePathsAreStillChecked() {
            assertThat(SafePaths.requireRelativePath("examplemod.mixins.json", "f"))
                    .isEqualTo("examplemod.mixins.json");
            assertThatThrownBy(() -> SafePaths.requireRelativePath("../x", "f"))
                    .isInstanceOf(OmniException.class);
        }

        @Test
        void diagnosticExplainsWhyRatherThanJustRefusing() {
            assertThatThrownBy(() -> SafePaths.requireJarPath("../etc/passwd", "payloads[0].file"))
                    .hasMessageContaining("OMNI-3004")
                    .hasMessageContaining("payloads[0].file")
                    .hasMessageContaining("untrusted input")
                    .hasMessageContaining("re-download");
        }
    }

    @Nested
    @DisplayName("identifiers")
    class Ids {

        @ParameterizedTest
        @ValueSource(strings = {"examplemod", "example-mod", "example_mod", "a1", "fabric-api",
            "examplemod-mc1214"})
        void acceptsValidModIds(String modId) {
            assertThat(Identifiers.requireModId(modId, "f")).isEqualTo(modId);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "a", "Examplemod", "1example", "-example", "example mod",
            "example.mod", "example/mod", "EXAMPLEMOD"})
        void rejectsInvalidModIds(String modId) {
            assertThatThrownBy(() -> Identifiers.requireModId(modId, "f"))
                    .isInstanceOf(OmniException.class);
        }

        @Test
        @DisplayName("payload ids are stricter than mod ids: they become task and directory names")
        void payloadIdsAreLettersAndDigitsOnly() {
            assertThat(Identifiers.requirePayloadId("mc1214", "f")).isEqualTo("mc1214");
            assertThat(Identifiers.requirePayloadId("mc261", "f")).isEqualTo("mc261");
            assertThatThrownBy(() -> Identifiers.requirePayloadId("mc-1214", "f"))
                    .isInstanceOf(OmniException.class)
                    .hasMessageContaining("Gradle task names");
            assertThatThrownBy(() -> Identifiers.requirePayloadId("mc_1214", "f"))
                    .isInstanceOf(OmniException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "com.example.Platform",
            "com.example.mc1214.Platform1214Factory",
            "com.example.Outer$Inner",
            "Single",
        })
        void acceptsValidClassNames(String className) {
            assertThat(Identifiers.requireClassName(className, "f")).isEqualTo(className);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", ".com.example.X", "com..example.X", "com.example.",
            "com.example.X ", "com example X", "com.example.X;", "com.example.9X"})
        void rejectsInvalidClassNames(String className) {
            assertThatThrownBy(() -> Identifiers.requireClassName(className, "f"))
                    .isInstanceOf(OmniException.class);
        }

        @Test
        @DisplayName("package containment is the check that stops a tampered manifest naming any class")
        void detectsPackageContainment() {
            assertThat(Identifiers.isInsideAnyPackage(
                    "com.example.mc1214.Platform", Arrays.asList("com.example.mc1214"))).isTrue();
            assertThat(Identifiers.isInsideAnyPackage(
                    "com.example.mc1214.sub.Platform", Arrays.asList("com.example.mc1214"))).isTrue();

            // The trap: a prefix match on the raw string would accept this.
            assertThat(Identifiers.isInsideAnyPackage(
                    "com.example.mc1214evil.Platform", Arrays.asList("com.example.mc1214"))).isFalse();
            assertThat(Identifiers.isInsideAnyPackage(
                    "java.lang.Runtime", Arrays.asList("com.example.mc1214"))).isFalse();
        }
    }
}
