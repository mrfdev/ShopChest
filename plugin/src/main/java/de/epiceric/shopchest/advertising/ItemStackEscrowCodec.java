package de.epiceric.shopchest.advertising;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Strict versioned serialization for full ItemStacks held as advertising-purchase escrow. */
public final class ItemStackEscrowCodec {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENCODED_LENGTH = 16 * 1024 * 1024;
    private static final ItemStackBinaryCodec STACK_CODEC = new ItemStackBinaryCodec();

    public String encode(PurchaseEscrowEvidence<ItemStack> evidence) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FORMAT_VERSION);
        yaml.set("slot-count", evidence.slotCount());
        yaml.set("change-count", evidence.affectedSlots().size());
        for (int index = 0; index < evidence.affectedSlots().size(); index++) {
            final String path = "changes." + index;
            yaml.set(path + ".slot", evidence.affectedSlots().get(index));
            writeStack(yaml, path + ".before", evidence.beforeStacks().get(index));
            writeStack(yaml, path + ".after", evidence.afterStacks().get(index));
        }
        yaml.set("removed-count", evidence.removedStacks().size());
        for (int index = 0; index < evidence.removedStacks().size(); index++) {
            writeStack(yaml, "removed." + index, evidence.removedStacks().get(index));
        }
        final String encoded = Base64.getEncoder().encodeToString(
                yaml.saveToString().getBytes(StandardCharsets.UTF_8));
        if (encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Advertising escrow payload is oversized");
        }
        return encoded;
    }

    public PurchaseEscrowEvidence<ItemStack> decode(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Advertising escrow payload is absent or oversized");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Advertising escrow payload is not Base64", exception);
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(decoded, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Advertising escrow payload is invalid", exception);
        }
        if (yaml.getInt("version", -1) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Advertising escrow format version is unsupported");
        }
        final int slotCount = yaml.getInt("slot-count", -1);
        final int changeCount = yaml.getInt("change-count", -1);
        final int removedCount = yaml.getInt("removed-count", -1);
        if (slotCount <= 0 || slotCount > 256
                || changeCount <= 0 || changeCount > slotCount
                || removedCount != changeCount) {
            throw new IllegalArgumentException("Advertising escrow counts are invalid");
        }

        final List<Integer> slots = new ArrayList<>(changeCount);
        final List<ItemStack> before = new ArrayList<>(changeCount);
        final List<ItemStack> after = new ArrayList<>(changeCount);
        final List<ItemStack> removed = new ArrayList<>(removedCount);
        for (int index = 0; index < changeCount; index++) {
            final String path = "changes." + index;
            if (!yaml.isInt(path + ".slot")) {
                throw new IllegalArgumentException("Advertising escrow slot index is missing");
            }
            slots.add(yaml.getInt(path + ".slot"));
            final ItemStack beforeStack = readStack(yaml, path + ".before", false);
            if (beforeStack == null) {
                throw new IllegalArgumentException("Advertising escrow before-stack is missing");
            }
            before.add(beforeStack);
            after.add(readStack(yaml, path + ".after", true));
        }
        for (int index = 0; index < removedCount; index++) {
            final ItemStack removedStack = readStack(yaml, "removed." + index, false);
            if (removedStack == null) {
                throw new IllegalArgumentException("Advertising escrow removed-stack is missing");
            }
            removed.add(removedStack);
        }
        return new PurchaseEscrowEvidence<>(
                ItemStackStackSemantics.INSTANCE,
                slotCount,
                slots,
                before,
                after,
                removed);
    }

    private static void writeStack(
            YamlConfiguration yaml,
            String path,
            ItemStack stack
    ) {
        yaml.set(path + ".present", stack != null);
        if (stack != null) {
            yaml.set(path + ".item-payload", STACK_CODEC.encode(stack));
        }
    }

    private static ItemStack readStack(
            YamlConfiguration yaml,
            String path,
            boolean nullable
    ) {
        if (!yaml.isBoolean(path + ".present")) {
            throw new IllegalArgumentException("Advertising escrow stack marker is missing");
        }
        if (!yaml.getBoolean(path + ".present")) {
            if (!nullable) {
                throw new IllegalArgumentException("Advertising escrow stack cannot be empty");
            }
            return null;
        }
        final String itemPayload = yaml.getString(path + ".item-payload");
        if (itemPayload == null) {
            throw new IllegalArgumentException("Advertising escrow stack payload is missing");
        }
        return STACK_CODEC.decode(itemPayload);
    }
}
