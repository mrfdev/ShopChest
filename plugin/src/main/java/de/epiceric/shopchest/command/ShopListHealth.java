package de.epiceric.shopchest.command;

/**
 * Read-only health snapshot for one registered shop.
 *
 * <p>Unchecked shops live in unloaded chunks and are deliberately not treated
 * as broken. Unavailable shops have a known structural problem, such as a
 * missing world or supported container.</p>
 */
record ShopListHealth(
        Inspection inspection,
        ShopListStock stock,
        boolean full,
        boolean blocked
) {

    static ShopListHealth checked(
            double buyPrice,
            double sellPrice,
            boolean adminShop,
            int stock,
            int freeSpace,
            int transactionAmount,
            boolean blocked
    ) {
        final int requiredAmount = Math.max(1, transactionAmount);
        return new ShopListHealth(
                Inspection.CHECKED,
                ShopListStock.resolve(
                        buyPrice,
                        adminShop,
                        true,
                        stock,
                        requiredAmount),
                !adminShop
                        && sellPrice > 0
                        && Math.max(0, freeSpace) < requiredAmount,
                blocked);
    }

    static ShopListHealth unchecked(
            double buyPrice,
            boolean adminShop,
            int transactionAmount
    ) {
        return unresolved(Inspection.UNCHECKED, buyPrice, adminShop, transactionAmount);
    }

    static ShopListHealth unavailable(
            double buyPrice,
            boolean adminShop,
            int transactionAmount
    ) {
        return unresolved(Inspection.UNAVAILABLE, buyPrice, adminShop, transactionAmount);
    }

    private static ShopListHealth unresolved(
            Inspection inspection,
            double buyPrice,
            boolean adminShop,
            int transactionAmount
    ) {
        return new ShopListHealth(
                inspection,
                ShopListStock.resolve(
                        buyPrice,
                        adminShop,
                        false,
                        0,
                        Math.max(1, transactionAmount)),
                false,
                false);
    }

    boolean outOfStock() {
        return stock.outOfStock();
    }

    boolean unavailable() {
        return inspection == Inspection.UNAVAILABLE;
    }

    boolean unchecked() {
        return inspection == Inspection.UNCHECKED;
    }

    boolean needsAttention() {
        return unavailable() || outOfStock() || full || blocked;
    }

    boolean healthy() {
        return inspection == Inspection.CHECKED && !needsAttention();
    }

    enum Inspection {
        CHECKED,
        UNCHECKED,
        UNAVAILABLE
    }
}
