package de.epiceric.shopchest.diagnostics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginBuildInfoTest {

    @Test
    void readsEmbeddedBuildTargets() throws Exception {
        final String properties = """
                version=1.15.4-SNAPSHOT
                build=794
                java-target=25
                paper-target=26.3
                paper-build=41
                paper-channel=ALPHA
                paper-api-version=26.3.build.41-alpha
                """;

        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                properties.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("1.15.4-SNAPSHOT", info.version());
        assertEquals("794", info.build());
        assertEquals("25", info.javaTarget());
        assertEquals("26.3", info.paperTarget());
        assertEquals("41", info.paperBuild());
        assertEquals("ALPHA", info.paperChannel());
        assertEquals("26.3.build.41-alpha", info.paperApiVersion());
        assertEquals(
                "io.papermc.paper:paper-api:26.3.build.41-alpha",
                info.paperApiCoordinate());
    }

    @Test
    void replacesMissingAndBlankValuesWithUnknown() throws Exception {
        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                "build= \npaper-target=26.3\n".getBytes(StandardCharsets.US_ASCII)));

        assertEquals("unknown", info.version());
        assertEquals("unknown", info.build());
        assertEquals("unknown", info.javaTarget());
        assertEquals("26.3", info.paperTarget());
        assertEquals("unknown", info.paperBuild());
        assertEquals("unknown", info.paperChannel());
        assertEquals("unknown", info.paperApiVersion());
        assertEquals("unknown", info.paperApiCoordinate());
    }
}
