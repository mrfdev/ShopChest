package de.epiceric.shopchest.diagnostics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginBuildInfoTest {

    @Test
    void readsEmbeddedBuildTargets() throws Exception {
        final String properties = """
                build=774
                java-target=25
                paper-target=26.2
                """;

        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                properties.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("774", info.build());
        assertEquals("25", info.javaTarget());
        assertEquals("26.2", info.paperTarget());
    }

    @Test
    void replacesMissingAndBlankValuesWithUnknown() throws Exception {
        final PluginBuildInfo info = PluginBuildInfo.read(new ByteArrayInputStream(
                "build= \npaper-target=26.2\n".getBytes(StandardCharsets.US_ASCII)));

        assertEquals("unknown", info.build());
        assertEquals("unknown", info.javaTarget());
        assertEquals("26.2", info.paperTarget());
    }
}
