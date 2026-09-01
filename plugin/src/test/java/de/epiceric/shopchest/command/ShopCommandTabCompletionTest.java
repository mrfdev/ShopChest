package de.epiceric.shopchest.command;

import de.epiceric.shopchest.catalog.PublicShopCandidate;
import de.epiceric.shopchest.catalog.PublicShopKind;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ShopCommandTabCompletionTest {

    private static final List<String> ROOT_COMMANDS = List.of(
            "help",
            "search",
            "profile");
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void returnsEveryRootCommandWhenPaperSuppliesNoArguments() {
        final List<String> suggestions = ShopCommand.rootTabCompletions(
                ROOT_COMMANDS,
                new String[0]);

        assertEquals(ROOT_COMMANDS, suggestions);
        assertNotSame(ROOT_COMMANDS, suggestions);
    }

    @Test
    void returnsEveryRootCommandForTheLegacyEmptyArgumentShape() {
        assertEquals(
                ROOT_COMMANDS,
                ShopCommand.rootTabCompletions(ROOT_COMMANDS, new String[]{""}));
    }

    @Test
    void filtersRootCommandsCaseInsensitivelyAfterTypingStarts() {
        assertEquals(
                List.of("search"),
                ShopCommand.rootTabCompletions(ROOT_COMMANDS, new String[]{"S"}));
    }

    @Test
    void leavesNestedCompletionToTheSelectedSubcommand() {
        assertEquals(
                List.of(),
                ShopCommand.rootTabCompletions(ROOT_COMMANDS, new String[]{"profile", ""}));
    }

    @Test
    void suggestsOnlyOwnedEligibleFeaturedShopIdsAndFiltersNumericPrefixes() {
        final List<PublicShopCandidate> candidates = List.of(
                candidate(12, OWNER, 15.0D),
                candidate(2, OWNER, 10.0D),
                candidate(7, OWNER, 0.0D),
                candidate(9, UUID.randomUUID(), 20.0D));

        assertEquals(
                List.of("2", "12"),
                ShopTabCompleter.featuredShopIdCompletions(OWNER, candidates, ""));
        assertEquals(
                List.of("12"),
                ShopTabCompleter.featuredShopIdCompletions(OWNER, candidates, "1"));
    }

    private static PublicShopCandidate candidate(
            int shopId,
            UUID ownerId,
            double buyPrice
    ) {
        return new PublicShopCandidate(
                shopId,
                ownerId,
                Material.STONE_BRICKS,
                1,
                buyPrice,
                PublicShopKind.NORMAL,
                false);
    }
}
