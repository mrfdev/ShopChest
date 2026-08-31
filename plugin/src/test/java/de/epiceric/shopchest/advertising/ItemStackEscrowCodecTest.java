package de.epiceric.shopchest.advertising;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackEscrowCodecTest {

    @Test
    void corruptPerStackBinaryPayloadFailsBeforePaperDeserialization() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("slot-count", 1);
        yaml.set("change-count", 1);
        yaml.set("changes.0.slot", 0);
        yaml.set("changes.0.before.present", true);
        yaml.set("changes.0.before.item-payload", "paper-item-v1:not base64!");
        yaml.set("changes.0.after.present", false);
        yaml.set("removed-count", 1);
        yaml.set("removed.0.present", true);
        yaml.set("removed.0.item-payload", "paper-item-v1:AAAA");
        final String payload = Base64.getEncoder().encodeToString(
                yaml.saveToString().getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> new ItemStackEscrowCodec().decode(payload));
    }

    @Test
    void oversizedOuterPayloadFailsBeforeYamlOrStackDeserialization() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStackEscrowCodec().decode("A".repeat(16 * 1024 * 1024 + 1)));
    }
}
