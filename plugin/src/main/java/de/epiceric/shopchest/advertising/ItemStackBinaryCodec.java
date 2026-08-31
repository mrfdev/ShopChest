package de.epiceric.shopchest.advertising;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Complete Paper ItemStack persistence using the versioned binary form.
 *
 * <p>The binary API retains data components and unknown namespaced metadata. The textual
 * envelope is independently versioned and deliberately bounded before any deserialization.</p>
 */
public final class ItemStackBinaryCodec {

    static final int MAX_RAW_BYTES = 2 * 1024 * 1024;
    private static final String PREFIX = "paper-item-v1:";
    private static final int MAX_ENCODED_CHARS = PREFIX.length()
            + ((MAX_RAW_BYTES + 2) / 3) * 4;

    public String encode(ItemStack stack) {
        return encodeRawBytes(serialize(stack));
    }

    public byte[] serialize(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) {
            throw new IllegalArgumentException("Cannot persist an empty ItemStack");
        }
        final byte[] serialized;
        try {
            serialized = stack.serializeAsBytes();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ItemStack binary serialization failed", exception);
        }
        if (serialized.length == 0 || serialized.length > MAX_RAW_BYTES) {
            throw new IllegalArgumentException("ItemStack binary payload is empty or oversized");
        }
        return serialized.clone();
    }

    public ItemStack decode(String payload) {
        return deserialize(decodeRawBytes(payload));
    }

    public ItemStack deserialize(byte[] serialized) {
        if (serialized == null || serialized.length == 0
                || serialized.length > MAX_RAW_BYTES) {
            throw new IllegalArgumentException("ItemStack binary payload is empty or oversized");
        }
        final ItemStack stack;
        try {
            stack = ItemStack.deserializeBytes(serialized.clone());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ItemStack binary payload is invalid", exception);
        }
        if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) {
            throw new IllegalArgumentException("ItemStack binary payload decoded as empty");
        }
        return stack.clone();
    }

    static String encodeRawBytes(byte[] serialized) {
        if (serialized == null || serialized.length == 0
                || serialized.length > MAX_RAW_BYTES) {
            throw new IllegalArgumentException("ItemStack binary payload is empty or oversized");
        }
        return PREFIX + Base64.getEncoder().encodeToString(serialized);
    }

    static byte[] decodeRawBytes(String payload) {
        if (payload == null || !payload.startsWith(PREFIX)
                || payload.length() <= PREFIX.length()
                || payload.length() > MAX_ENCODED_CHARS) {
            throw new IllegalArgumentException(
                    "ItemStack binary payload is absent, unsupported, or oversized");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("ItemStack binary payload is not Base64", exception);
        }
        if (decoded.length == 0 || decoded.length > MAX_RAW_BYTES) {
            throw new IllegalArgumentException("ItemStack binary payload is empty or oversized");
        }
        return decoded;
    }
}
