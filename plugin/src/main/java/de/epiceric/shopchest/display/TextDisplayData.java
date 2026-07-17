package de.epiceric.shopchest.display;

import net.kyori.adventure.text.Component;

/**
 * Text and presentation settings for a ShopChest hologram panel.
 *
 * @param text panel text, including newlines
 * @param lineWidth maximum rendered line width in client font pixels
 * @param backgroundColor ARGB background color
 * @param fixedFacing whether the panel uses its entity yaw instead of facing each viewer
 * @param scale uniform visual scale applied to the text and its background
 */
public record TextDisplayData(Component text, int lineWidth, int backgroundColor, boolean fixedFacing, float scale) {

    public static final int DEFAULT_LINE_WIDTH = 200;
    public static final int DEFAULT_BACKGROUND_COLOR = 0x70315B7D;
    public static final float DEFAULT_SCALE = 0.5f;

    public TextDisplayData {
        text = text == null ? Component.empty() : text;
    }

    public TextDisplayData(Component text) {
        this(text, DEFAULT_LINE_WIDTH, DEFAULT_BACKGROUND_COLOR, true, DEFAULT_SCALE);
    }

    public TextDisplayData(String text) {
        this(TextComponentHelper.LEGACY_COMPONENT_SERIALIZER.deserialize(text == null ? "" : text));
    }
}
