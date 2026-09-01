package de.epiceric.shopchest.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ShopCommandTabCompletionTest {

    private static final List<String> ROOT_COMMANDS = List.of(
            "help",
            "search",
            "profile");

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
}
