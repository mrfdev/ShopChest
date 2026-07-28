package de.epiceric.shopchest.shop;

/**
 * The editable trading terms of a shop.
 */
public record ShopTerms(int amount, double buyPrice, double sellPrice) {

    public static ShopTerms from(Shop shop) {
        return new ShopTerms(
                shop.getProduct().getAmount(),
                shop.getBuyPrice(),
                shop.getSellPrice());
    }
}
