package de.epiceric.shopchest.sql;

/**
 * Raw, read-only projection of one persisted shop row.
 *
 * <p>Values deliberately remain unparsed so an administrator audit can report
 * malformed rows individually instead of aborting the complete result.</p>
 */
public record ShopAuditRecord(
        long rowNumber,
        String rawId,
        String vendor,
        String product,
        String rawAmount,
        String world,
        String rawX,
        String rawY,
        String rawZ,
        String rawBuyPrice,
        String rawSellPrice,
        String shopType
) {

    private static final int MAX_NUMERIC_TEXT_LENGTH = 64;

    public Integer parsedId() {
        return parseInteger(rawId);
    }

    public Integer parsedAmount() {
        return parseInteger(rawAmount);
    }

    public Integer parsedX() {
        return parseInteger(rawX);
    }

    public Integer parsedY() {
        return parseInteger(rawY);
    }

    public Integer parsedZ() {
        return parseInteger(rawZ);
    }

    public Double parsedBuyPrice() {
        return parseDouble(rawBuyPrice);
    }

    public Double parsedSellPrice() {
        return parseDouble(rawSellPrice);
    }

    private static Integer parseInteger(String rawValue) {
        if (rawValue == null || rawValue.length() > MAX_NUMERIC_TEXT_LENGTH) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue.strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseDouble(String rawValue) {
        if (rawValue == null || rawValue.length() > MAX_NUMERIC_TEXT_LENGTH) {
            return null;
        }
        try {
            return Double.valueOf(rawValue.strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
