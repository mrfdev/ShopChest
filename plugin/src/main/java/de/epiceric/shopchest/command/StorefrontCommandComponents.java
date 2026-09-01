package de.epiceric.shopchest.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

final class StorefrontCommandComponents {

    private StorefrontCommandComponents() {
    }

    static Component withShopIdHover(Component component, int shopId) {
        return component.hoverEvent(HoverEvent.showText(Component.text(
                "Unique shop ID: #" + shopId,
                NamedTextColor.YELLOW)));
    }

    static Component featuredPickerPrompt(String commandName) {
        return Component.text(
                        "/" + commandName + " profile featured add <shop-id>",
                        NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Click to show your eligible shops and their ID numbers")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + commandName + " profile shops 1"));
    }

    static Component addFeaturedAction(String commandName, int shopId) {
        return Component.text("[Feature shop #" + shopId + "]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Add this shop to your Featured Listings")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + commandName + " profile featured add " + shopId));
    }

    static Component removeFeaturedAction(String commandName, int shopId) {
        return Component.text("[Remove]", NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Remove shop #" + shopId + " from your Featured Listings")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + commandName + " profile featured remove " + shopId));
    }
}
