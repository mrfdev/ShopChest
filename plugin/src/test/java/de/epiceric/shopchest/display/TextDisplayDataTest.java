package de.epiceric.shopchest.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;

class TextDisplayDataTest {

    @Test
    void usesTheMutedBluePanelDefault() {
        assertEquals(0x70315B7D, TextDisplayData.DEFAULT_BACKGROUND_COLOR);
        assertEquals(0x70315B7D, new TextDisplayData("Shop").backgroundColor());
        assertEquals(true, new TextDisplayData("Shop").fixedFacing());
        assertEquals(0.5f, new TextDisplayData("Shop").scale());
        assertEquals(255, new TextDisplayData("Shop").textOpacity());
        assertEquals(false, new TextDisplayData("Shop").shadowed());
        assertEquals(false, new TextDisplayData("Shop").seeThrough());
        assertEquals(TextDisplay.TextAlignment.CENTER, new TextDisplayData("Shop").alignment());
    }
}
