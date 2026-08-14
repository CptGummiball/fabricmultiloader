package dev.fabricmultiloader.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.format.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdTest {

    @Test
    void buildsAndRenders() {
        Id ruby = Id.of("examplemod", "ruby");
        assertThat(ruby.namespace()).isEqualTo("examplemod");
        assertThat(ruby.path()).isEqualTo("ruby");
        assertThat(ruby.toString()).isEqualTo("examplemod:ruby");
    }

    @Test
    @DisplayName("a bare path is minecraft-namespaced, matching Minecraft's own behaviour")
    void parsesBarePathsAsMinecraft() {
        assertThat(Id.parse("stone")).isEqualTo(Id.minecraft("stone"));
        assertThat(Id.parse("examplemod:ruby")).isEqualTo(Id.of("examplemod", "ruby"));
    }

    @Test
    void pathsMayContainSlashesButNamespacesMayNot() {
        assertThat(Id.of("examplemod", "block/ruby").path()).isEqualTo("block/ruby");
        assertThatThrownBy(() -> Id.of("example/mod", "ruby"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid character '/'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Examplemod", "example mod", "example!mod", "", "exampleMod"})
    @DisplayName("invalid namespaces fail at construction, not deep inside Minecraft")
    void rejectsInvalidNamespaces(String namespace) {
        assertThatThrownBy(() -> Id.of(namespace, "ruby"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMultipleColons() {
        assertThatThrownBy(() -> Id.parse("a:b:c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one ':'");
    }

    @Test
    void derivedIdentifiers() {
        Id ruby = Id.of("examplemod", "ruby");
        assertThat(ruby.suffixed("_block")).isEqualTo(Id.of("examplemod", "ruby_block"));
        assertThat(ruby.withPath("sapphire")).isEqualTo(Id.of("examplemod", "sapphire"));
    }

    @Test
    void valueSemantics() {
        assertThat(Id.of("a", "b")).isEqualTo(Id.of("a", "b"));
        assertThat(Id.of("a", "b").hashCode()).isEqualTo(Id.of("a", "b").hashCode());
        assertThat(Id.of("a", "b")).isNotEqualTo(Id.of("a", "c"));
    }

    @Test
    @DisplayName("Side is visible through the api module's transitive format dependency")
    void sideIsReachableFromTheApi() {
        assertThat(Side.parse("client")).isEqualTo(Side.CLIENT);
        assertThat(Side.CLIENT.isClient()).isTrue();
        assertThat(Side.SERVER.id()).isEqualTo("server");
    }
}
