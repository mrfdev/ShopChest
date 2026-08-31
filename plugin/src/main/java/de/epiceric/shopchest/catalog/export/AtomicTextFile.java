package de.epiceric.shopchest.catalog.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicTextFile {

    private AtomicTextFile() {
    }

    static void replace(Path target, String content) throws IOException {
        final Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Snapshot output must have a parent directory: " + target);
        }
        Files.createDirectories(parent);

        final Path temporary = Files.createTempFile(parent, ".shopchest-snapshot-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveIntoPlace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
