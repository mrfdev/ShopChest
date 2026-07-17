package de.epiceric.shopchest.diagnostics;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record PluginBuildInfo(
        String build,
        String javaTarget,
        String paperTarget
) {

    private static final String RESOURCE = "shopchest-build.properties";
    private static final String UNKNOWN = "unknown";

    public static PluginBuildInfo load(Plugin plugin) {
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                return unknown();
            }
            return read(input);
        } catch (IOException exception) {
            return unknown();
        }
    }

    static PluginBuildInfo read(InputStream input) throws IOException {
        final Properties properties = new Properties();
        properties.load(input);
        return new PluginBuildInfo(
                value(properties, "build"),
                value(properties, "java-target"),
                value(properties, "paper-target"));
    }

    private static String value(Properties properties, String key) {
        final String value = properties.getProperty(key);
        return value == null || value.isBlank() ? UNKNOWN : value.strip();
    }

    private static PluginBuildInfo unknown() {
        return new PluginBuildInfo(UNKNOWN, UNKNOWN, UNKNOWN);
    }
}
