package de.epiceric.shopchest.language.item;

import java.util.List;

public record ItemNameDiagnostics(
        int runtimeItems,
        int translatableItems,
        int localeOverrides,
        int ignoredOverrides,
        List<String> missingTranslationKeys
) {
    public ItemNameDiagnostics {
        missingTranslationKeys = List.copyOf(missingTranslationKeys);
    }

    public boolean complete() {
        return runtimeItems > 0
                && runtimeItems == translatableItems
                && missingTranslationKeys.isEmpty();
    }

    public static ItemNameDiagnostics unavailable() {
        return new ItemNameDiagnostics(0, 0, 0, 0, List.of());
    }
}
