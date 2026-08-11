package dev.fabricmultiloader.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OmniFormatTest {

    @Test
    @DisplayName("format id and schema version stay in lockstep")
    void formatIdMatchesSchemaVersion() {
        assertThat(OmniFormat.FORMAT_ID).isEqualTo("omni/" + OmniFormat.SCHEMA_VERSION);
    }

    @Test
    @DisplayName("the textual marker really is a prefix of a conforming manifest")
    void markerPrefixMatchesManifestStart() {
        String manifestStart = "{\"formatId\":\"" + OmniFormat.FORMAT_ID + "\",\"schemaVersion\":1}";
        assertThat(manifestStart).startsWith(OmniFormat.FORMAT_MARKER_PREFIX);
    }

    @Test
    @DisplayName("nested jars live under META-INF so they are never on the classpath")
    void nestedJarRootIsUnderMetaInf() {
        assertThat(OmniFormat.NESTED_JAR_ROOT)
                .startsWith("META-INF/")
                .endsWith("/");
    }

    @Test
    @DisplayName("the icon is outside assets/, so the container is not a resource pack")
    void iconIsNotAResourcePackEntry() {
        assertThat(OmniFormat.ICON_PATH)
                .doesNotStartWith("assets/")
                .doesNotStartWith("data/");
    }

    @Test
    @DisplayName("no manifest path escapes the jar")
    void manifestPathsAreRelativeAndSafe() {
        String[] paths = {
            OmniFormat.CONTAINER_MANIFEST_PATH,
            OmniFormat.PAYLOAD_DESCRIPTOR_PATH,
            OmniFormat.NESTED_JAR_ROOT,
            OmniFormat.ICON_PATH,
            OmniFormat.ENTRYPOINTS_PATH,
        };
        for (String path : paths) {
            assertThat(path)
                    .doesNotStartWith("/")
                    .doesNotContain("\\")
                    .doesNotContain("..");
        }
    }

    @Test
    @DisplayName("constants holder cannot be instantiated")
    void isNotInstantiable() throws Exception {
        assertThat(Modifier.isFinal(OmniFormat.class.getModifiers())).isTrue();
        Constructor<OmniFormat> constructor = OmniFormat.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }
}
