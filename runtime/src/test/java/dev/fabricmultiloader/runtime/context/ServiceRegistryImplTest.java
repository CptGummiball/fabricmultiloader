package dev.fabricmultiloader.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceRegistryImplTest {

    /** A mod-defined service interface, with no Minecraft anywhere in its signature. */
    private interface OreGen {
        String describe();
    }

    private interface Unregistered {
    }

    private static OreGen oreGen(final String description) {
        return new OreGen() {
            @Override
            public String describe() {
                return description;
            }
        };
    }

    @Test
    @DisplayName("a registered service can be read back")
    void registersAndReads() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");
        services.openRegistration();
        services.register(OreGen.class, oreGen("1.21.4"));
        services.sealRegistration();

        assertThat(services.get(OreGen.class).describe()).isEqualTo("1.21.4");
        assertThat(services.find(OreGen.class)).isPresent();
        assertThat(services.has(OreGen.class)).isTrue();
        assertThat(services.registered()).containsExactly(OreGen.class);
    }

    @Test
    @DisplayName("registering before the window opens reports OMNI-4002")
    void refusesEarlyRegistration() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");

        OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                () -> services.register(OreGen.class, oreGen("too early")));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
        assertThat(thrown.getMessage()).contains("not open yet");
    }

    @Test
    @DisplayName("registering after the window closes reports OMNI-4002 and says so")
    void refusesLateRegistration() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");
        services.openRegistration();
        services.sealRegistration();

        OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                () -> services.register(OreGen.class, oreGen("too late")));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
        // The two states get different wording because they call for different fixes: one is a
        // call made too early, the other a call left behind in the wrong lifecycle hook.
        assertThat(thrown.getMessage()).contains("already closed");
    }

    @Test
    @DisplayName("asking for a service nobody registered reports OMNI-4010 and lists what is there")
    void reportsAMissingService() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");
        services.openRegistration();
        services.register(OreGen.class, oreGen("1.21.4"));
        services.sealRegistration();

        OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                () -> services.get(Unregistered.class));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4010);
        assertThat(thrown.getMessage())
                .contains("Unregistered")
                .contains("OreGen");
    }

    @Test
    @DisplayName("find returns empty rather than throwing for an optional service")
    void findIsForwardCompatible() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");

        assertThat(services.find(Unregistered.class)).isEmpty();
        assertThat(services.has(Unregistered.class)).isFalse();
    }

    @Test
    @DisplayName("registering the same interface twice is refused rather than resolved by order")
    void refusesDuplicateRegistration() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");
        services.openRegistration();
        services.register(OreGen.class, oreGen("first"));

        OmniApiMisuseException thrown = catchThrowableOfType(OmniApiMisuseException.class,
                () -> services.register(OreGen.class, oreGen("second")));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
        assertThat(thrown.getMessage()).contains("registered twice");
    }

    @Test
    @DisplayName("null and mismatched arguments are rejected as programming errors, not error codes")
    void rejectsInvalidArguments() {
        ServiceRegistryImpl services = new ServiceRegistryImpl("examplemod");
        services.openRegistration();

        assertThatThrownBy(() -> services.register(OreGen.class, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> services.get(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
