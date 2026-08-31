package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvertisingCurrencyMatcherTest {

    private final FakeStackSemantics semantics = new FakeStackSemantics();
    private final AdvertisingCurrencyMatcher<FakeStack> matcher =
            new AdvertisingCurrencyMatcher<>(semantics);

    @Test
    void matchesTheCompleteCapturedItemRegardlessOfStackAmount() {
        final FakeStack template = token(1, Map.of(
                "display-name", "AFK Shrine Token",
                "lore", "Collectors-item",
                "opaque-component", "issued-by-afk-shrine"));
        final FakeStack candidate = token(5, template.metadata());

        assertTrue(matcher.matches(candidate, template));
    }

    @Test
    void rejectsLookalikesWithMissingAdditionalOrChangedMetadata() {
        final FakeStack template = token(1, Map.of(
                "display-name", "AFK Shrine Token",
                "lore", "Collectors-item",
                "opaque-component", "issued-by-afk-shrine"));
        final FakeStack missingOpaqueComponent = token(5, Map.of(
                "display-name", "AFK Shrine Token",
                "lore", "Collectors-item"));

        final Map<String, String> additionalMetadata = new LinkedHashMap<>(template.metadata());
        additionalMetadata.put("forged-marker", "extra");
        final FakeStack additionalComponent = token(5, additionalMetadata);

        final Map<String, String> changedMetadata = new LinkedHashMap<>(template.metadata());
        changedMetadata.put("opaque-component", "copied-by-player");
        final FakeStack changedComponent = token(5, changedMetadata);

        assertFalse(matcher.matches(missingOpaqueComponent, template));
        assertFalse(matcher.matches(additionalComponent, template));
        assertFalse(matcher.matches(changedComponent, template));
    }

    @Test
    void rejectsPlainRenamedAndCopiedNameLoreDyes() {
        final FakeStack template = token(1, Map.of(
                "display-name", "AFK Shrine Token",
                "lore", "Collectors-item",
                "component:provenance", "genuine-capture"));

        assertFalse(matcher.matches(token(5, Map.of()), template));
        assertFalse(matcher.matches(token(5, Map.of(
                "display-name", "AFK Shrine Token")), template));
        assertFalse(matcher.matches(token(5, Map.of(
                "display-name", "AFK Shrine Token",
                "lore", "Collectors-item")), template));
    }

    @Test
    void rejectsMissingAdditionalAndChangedPdcOrDataComponents() {
        final FakeStack template = token(1, Map.of(
                "display-name", "AFK Shrine Token",
                "pdc:afkshrine:future-data", "captured-value",
                "component:custom-data", "opaque-value"));
        final Map<String, String> additionalPdc = new LinkedHashMap<>(template.metadata());
        additionalPdc.put("pdc:other-plugin:marker", "unexpected");

        assertFalse(matcher.matches(token(5, Map.of(
                "display-name", "AFK Shrine Token",
                "component:custom-data", "opaque-value")), template));
        assertFalse(matcher.matches(token(5, additionalPdc), template));
        assertFalse(matcher.matches(token(5, Map.of(
                "display-name", "AFK Shrine Token",
                "pdc:afkshrine:future-data", "changed-value",
                "component:custom-data", "opaque-value")), template));
    }

    @Test
    void failsClosedWithoutAnAuthoritativeTemplate() {
        assertFalse(matcher.matches(token(5, Map.of()), null));
    }

    private static FakeStack token(int amount, Map<String, String> metadata) {
        return new FakeStack("LIGHT_BLUE_DYE", amount, metadata);
    }

    record FakeStack(String material, int amount, Map<String, String> metadata) {

        FakeStack {
            metadata = Map.copyOf(metadata);
        }
    }

    static final class FakeStackSemantics implements StackSemantics<FakeStack> {

        @Override
        public boolean isEmpty(FakeStack stack) {
            return stack == null || stack.amount() <= 0 || "AIR".equals(stack.material());
        }

        @Override
        public int amount(FakeStack stack) {
            return stack.amount();
        }

        @Override
        public FakeStack copyOf(FakeStack stack) {
            return stack == null ? null : new FakeStack(stack.material(), stack.amount(), stack.metadata());
        }

        @Override
        public FakeStack withAmount(FakeStack stack, int amount) {
            return new FakeStack(stack.material(), amount, stack.metadata());
        }

        @Override
        public boolean isSimilar(FakeStack candidate, FakeStack template) {
            return candidate.material().equals(template.material())
                    && candidate.amount() == template.amount()
                    && candidate.metadata().equals(template.metadata());
        }

        @Override
        public boolean exactlyEquals(FakeStack left, FakeStack right) {
            return java.util.Objects.equals(left, right);
        }
    }
}
