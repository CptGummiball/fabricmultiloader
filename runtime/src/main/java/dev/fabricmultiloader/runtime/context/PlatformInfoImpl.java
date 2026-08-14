package dev.fabricmultiloader.runtime.context;

import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.format.manifest.MappingsInfo;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.format.version.MinecraftVersions;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.Optional;

/** The detected environment, as mod code sees it. */
public final class PlatformInfoImpl implements PlatformInfo {

    private final Environment environment;
    private final PayloadDescriptor payload;

    /**
     * @param environment what was detected
     * @param payload the active payload
     */
    public PlatformInfoImpl(Environment environment, PayloadDescriptor payload) {
        this.environment = environment;
        this.payload = payload;
    }

    @Override
    public SemVer minecraft() {
        return environment.minecraft();
    }

    @Override
    public SemVer fabricLoader() {
        return environment.fabricLoader();
    }

    @Override
    public Optional<SemVer> fabricApi() {
        return environment.fabricApi().isUnknown()
                ? Optional.<SemVer>empty()
                : Optional.of(environment.fabricApi());
    }

    @Override
    public int javaMajor() {
        return environment.javaMajor();
    }

    @Override
    public String payloadId() {
        return payload.id();
    }

    @Override
    public String mappingNamespace() {
        // A development runtime remaps the payload to named on the way in, so what the manifest
        // recorded at build time is no longer what is running.
        return environment.isDevelopment() ? MappingsInfo.NAMED : payload.mappings().namespace();
    }

    @Override
    public boolean minecraftIn(String... predicates) {
        if (predicates == null || predicates.length == 0) {
            return false;
        }
        return VersionRange.parse(predicates).test(environment.minecraft());
    }

    @Override
    public int minecraftOrdinal() {
        return MinecraftVersions.ordinal(environment.minecraft());
    }

    @Override
    public String toString() {
        return "mc=" + minecraft() + " payload=" + payloadId() + " java=" + javaMajor();
    }
}
