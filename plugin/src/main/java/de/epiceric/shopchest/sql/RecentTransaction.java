package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.event.ShopBuySellEvent;

import java.util.UUID;

public record RecentTransaction(
        long id,
        int shopId,
        String timestamp,
        long time,
        String playerName,
        String playerUuid,
        String productName,
        int amount,
        String vendorName,
        String vendorUuid,
        boolean adminShop,
        String world,
        int x,
        int y,
        int z,
        double price,
        ShopBuySellEvent.Type type
) {

    public Perspective perspective(UUID playerId) {
        final String uuid = playerId.toString();
        if (uuid.equalsIgnoreCase(playerUuid)) {
            return type == ShopBuySellEvent.Type.BUY
                    ? Perspective.PLAYER_BOUGHT
                    : Perspective.PLAYER_SOLD;
        }
        if (!adminShop && uuid.equalsIgnoreCase(vendorUuid)) {
            return type == ShopBuySellEvent.Type.BUY
                    ? Perspective.SHOP_SOLD
                    : Perspective.SHOP_BOUGHT;
        }
        return Perspective.UNRELATED;
    }

    public double moneyDelta(UUID playerId) {
        return switch (perspective(playerId)) {
            case PLAYER_BOUGHT, SHOP_BOUGHT -> -price;
            case PLAYER_SOLD, SHOP_SOLD -> price;
            case UNRELATED -> 0;
        };
    }

    public String counterparty(UUID playerId) {
        return switch (perspective(playerId)) {
            case PLAYER_BOUGHT, PLAYER_SOLD -> vendorName;
            case SHOP_SOLD, SHOP_BOUGHT -> playerName;
            case UNRELATED -> "";
        };
    }

    public double unitPrice() {
        return amount > 0 ? price / amount : price;
    }

    public enum Perspective {
        PLAYER_BOUGHT,
        PLAYER_SOLD,
        SHOP_SOLD,
        SHOP_BOUGHT,
        UNRELATED
    }
}
