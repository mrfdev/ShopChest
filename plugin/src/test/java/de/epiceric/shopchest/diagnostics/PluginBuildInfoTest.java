package de.epiceric.shopchest.diagnostics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginBuildInfoTest {

    @Test
    void readsEmbeddedBuildTargets() throws Exception {
        final String properties = """
                version=1.15.2
                build=774
                java-target=25
                paper-target=26.2
                paper-build=84
                paper-channel=STABLE
                paper-api-version=26.2.build.84-stable
                """;

        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                properties.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("1.15.2", info.version());
        assertEquals("774", info.build());
        assertEquals("25", info.javaTarget());
        assertEquals("26.2", info.paperTarget());
        assertEquals("84", info.paperBuild());
        assertEquals("STABLE", info.paperChannel());
        assertEquals("26.2.build.84-stable", info.paperApiVersion());
        assertEquals(
                "io.papermc.paper:paper-api:26.2.build.84-stable",
                info.paperApiCoordinate());
    }

    @Test
    void replacesMissingAndBlankValuesWithUnknown() throws Exception {
        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                "build= \npaper-target=26.2\n".getBytes(StandardCharsets.US_ASCII)));

        assertEquals("unknown", info.version());
        assertEquals("unknown", info.build());
        assertEquals("unknown", info.javaTarget());
        assertEquals("26.2", info.paperTarget());
        assertEquals("unknown", info.paperBuild());
        assertEquals("unknown", info.paperChannel());
        assertEquals("unknown", info.paperApiVersion());
        assertEquals("unknown", info.paperApiCoordinate());
    }
}
