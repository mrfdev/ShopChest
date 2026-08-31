package de.epiceric.shopchest.advertising;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned, fail-closed envelope for the captured advertising-currency authority. */
public final class AdvertisingCurrencyAuthorityCodec {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_DOCUMENT_CHARS = 4 * 1024 * 1024;

    private AdvertisingCurrencyAuthorityCodec() {
    }

    public static String encode(
            byte[] serializedItem,
            UUID capturedBy,
            Instant capturedAt
    ) {
        Objects.requireNonNull(capturedBy, "capturedBy");
        Objects.requireNonNull(capturedAt, "capturedAt");
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", FORMAT_VERSION);
        yaml.set("item-payload", ItemStackBinaryCodec.encodeRawBytes(serializedItem));
        yaml.set("captured-by", capturedBy.toString());
        yaml.set("captured-at", capturedAt.toString());
        return yaml.saveToString();
    }

    public static Decoded decode(String document) {
        if (document == null || document.isBlank()
                || document.length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException(
                    "Advertising currency authority is absent or oversized");
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(document);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException(
                    "Advertising currency authority is invalid YAML", exception);
        }
        if (yaml.getInt("format-version", -1) != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "Advertising currency authority version is unsupported");
        }
        final byte[] serializedItem = ItemStackBinaryCodec.decodeRawBytes(
                yaml.getString("item-payload"));
        final UUID capturedBy;
        final Instant capturedAt;
        try {
            capturedBy = UUID.fromString(requireText(yaml, "captured-by"));
            capturedAt = Instant.parse(requireText(yaml, "captured-at"));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Advertising currency capture audit data is invalid", exception);
        }
        return new Decoded(serializedItem, capturedBy, capturedAt);
    }

    private static String requireText(YamlConfiguration yaml, String path) {
        final String value = yaml.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Advertising currency authority is missing " + path);
        }
        return value;
    }

    public record Decoded(byte[] serializedItem, UUID capturedBy, Instant capturedAt) {

        public Decoded {
            serializedItem = serializedItem.clone();
            Objects.requireNonNull(capturedBy, "capturedBy");
            Objects.requireNonNull(capturedAt, "capturedAt");
        }

        @Override
        public byte[] serializedItem() {
            return serializedItem.clone();
        }
    }
}
