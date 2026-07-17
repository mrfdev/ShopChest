package de.epiceric.shopchest.config;

public enum Placeholder {

    VENDOR("%VENDOR%"),
    AMOUNT("%AMOUNT%"),
    ITEM_NAME("%ITEMNAME%"),
    CREATION_PRICE("%CREATION-PRICE%"),
    ERROR("%ERROR%"),
    ENCHANTMENT("%ENCHANTMENT%"),
    ITEM_DETAILS("%ITEM-DETAILS%"),
    DETAIL_COUNT("%DETAIL-COUNT%"),
    MIN_PRICE("%MIN-PRICE%"),
    MAX_PRICE("%MAX-PRICE%"),
    VERSION("%VERSION%"),
    BUY_PRICE("%BUY-PRICE%"),
    SELL_PRICE("%SELL-PRICE%"),
    LIMIT("%LIMIT%"),
    PLAYER("%PLAYER%"),
    POTION_EFFECT("%POTION-EFFECT%"),
    MUSIC_TITLE("%MUSIC-TITLE%"),
    BANNER_PATTERN_NAME("%BANNER-PATTERN-NAME%"),
    PROPERTY("%PROPERTY%"),
    VALUE("%VALUE%"),
    EXTENDED("%EXTENDED%"),
    REVENUE("%REVENUE%"),
    GENERATION("%GENERATION%"),
    STOCK("%STOCK%"),
    CHEST_SPACE("%CHEST-SPACE%"),
    MAX_STACK("%MAX-STACK%"),
    COMMAND("%COMMAND%"),
    DURABILITY("%DURABILITY%"),
    COLOR_OWNER("%COLOR-OWNER%"),
    COLOR_QUANTITY("%COLOR-QUANTITY%"),
    COLOR_ITEM("%COLOR-ITEM%"),
    COLOR_LABEL("%COLOR-LABEL%"),
    COLOR_BUY_VALUE("%COLOR-BUY-VALUE%"),
    COLOR_SELL_VALUE("%COLOR-SELL-VALUE%"),
    COLOR_SEPARATOR("%COLOR-SEPARATOR%"),
    COLOR_ADMIN("%COLOR-ADMIN%"),
    COLOR_UNAVAILABLE("%COLOR-UNAVAILABLE%"),
    COLOR_RESET("%COLOR-RESET%"),
    SHOP_ID("%SHOP-ID%"),
    WORLD("%WORLD%"),
    X("%X%"),
    Y("%Y%"),
    Z("%Z%"),
    PAGE("%PAGE%"),
    PAGES("%PAGES%"),
    TIME("%TIME%"),
    COUNTERPARTY("%COUNTERPARTY%"),
    PRICE("%PRICE%"),
    UNIT_PRICE("%UNIT-PRICE%"),
    CMI_WORTH("%CMI-WORTH%"),
    MULTIPLIER("%MULTIPLIER%"),
    EARNED("%EARNED%"),
    SPENT("%SPENT%"),
    NET("%NET%");

    private final String name;

    Placeholder(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

}
