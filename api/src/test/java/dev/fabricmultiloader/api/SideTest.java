package dev.fabricmultiloader.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SideTest {

    @Test
    void clientAndServerAreMutuallyExclusive() {
        assertThat(Side.CLIENT.isClient()).isTrue();
        assertThat(Side.CLIENT.isServer()).isFalse();
        assertThat(Side.SERVER.isServer()).isTrue();
        assertThat(Side.SERVER.isClient()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "client, CLIENT",
        "CLIENT, CLIENT",
        "  Client  , CLIENT",
        "server, SERVER",
        "SERVER, SERVER",
    })
    @DisplayName("environment constraints parse case- and whitespace-insensitively")
    void parsesSpecificSides(String input, Side expected) {
        assertThat(Side.parseConstraint(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("'*' means both sides and is represented as null")
    void parsesWildcardAsNull() {
        assertThat(Side.parseConstraint("*")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "both", "clients", "dedicated", "CLIENT_SERVER"})
    void rejectsUnknownConstraints(String input) {
        assertThatThrownBy(() -> Side.parseConstraint(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid environment constraint");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Side.parseConstraint(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
