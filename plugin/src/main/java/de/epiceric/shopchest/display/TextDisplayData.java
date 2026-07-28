package de.epiceric.shopchest.display;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.TextDisplay;

/**
 * Text and presentation settings for a ShopChest hologram panel.
 *
 * @param text panel text, including newlines
 * @param lineWidth maximum rendered line width in client font pixels
 * @param backgroundColor ARGB background color
 * @param fixedFacing whether the panel uses its entity yaw instead of facing each viewer
 * @param scale uniform visual scale applied to the text and its background
 * @param textOpacity foreground opacity from 0 through 255
 * @param shadowed whether glyphs render with their native shadow
 * @param seeThrough whether text remains visible through blocks
 * @param alignment alignment of the text inside the panel
 */
public record TextDisplayData(
        Component text,
        int lineWidth,
        int backgroundColor,
        boolean fixedFacing,
        float scale,
        int textOpacity,
        boolean shadowed,
        boolean seeThrough,
        TextDisplay.TextAlignment alignment
) {

    public static final int DEFAULT_LINE_WIDTH = 200;
    public static final int DEFAULT_BACKGROUND_COLOR = 0x70315B7D;
    public static final float DEFAULT_SCALE = 0.5f;
    public static final int DEFAULT_TEXT_OPACITY = 255;

    public TextDisplayData {
        text = text == null ? Component.empty() : text;
        textOpacity = Math.max(0, Math.min(textOpacity, 255));
        alignment = alignment == null ? TextDisplay.TextAlignment.CENTER : alignment;
    }

    public TextDisplayData(Component text) {
        this(
                text,
                DEFAULT_LINE_WIDTH,
                DEFAULT_BACKGROUND_COLOR,
                true,
                DEFAULT_SCALE,
                DEFAULT_TEXT_OPACITY,
                false,
                false,
                TextDisplay.TextAlignment.CENTER);
    }

    public TextDisplayData(String text) {
        this(TextComponentHelper.LEGACY_COMPONENT_SERIALIZER.deserialize(text == null ? "" : text));
    }
}
