package de.epiceric.shopchest.catalog.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceSnapshotWriterTest {

    @Test
    void atomicallyReplacesEachReviewedJsonAndCsvFile(@TempDir Path outputDirectory)
            throws Exception {
        final Path oldJson = outputDirectory.resolve("marketplace-snapshot.json");
        final Path oldCsv = outputDirectory.resolve("marketplace-snapshot.csv");
        Files.writeString(oldJson, "old json");
        Files.writeString(oldCsv, "old csv");
        final MarketplaceSnapshot snapshot = snapshot();

        final MarketplaceSnapshotFiles files =
                MarketplaceSnapshotWriter.write(outputDirectory, snapshot);

        assertEquals(oldJson, files.json());
        assertEquals(oldCsv, files.csv());
        assertEquals(MarketplaceSnapshotJson.render(snapshot), Files.readString(files.json()));
        assertEquals(MarketplaceSnapshotCsv.render(snapshot), Files.readString(files.csv()));
        try (var paths = Files.list(outputDirectory)) {
            assertEquals(2, paths.count());
        }
    }

    private static MarketplaceSnapshot snapshot() {
        return new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        Instant.parse("2026-08-31T12:34:56Z"),
                        ZoneId.of("Europe/Amsterdam"),
                        "1.15.2+783",
                        "Captured in August 2026. Prices and stock may have changed.",
                        "/warp shops"),
                new MarketplaceSnapshotCounts(1, 1, 0, 0, 1, 0, 0),
                List.of(new MarketplaceListing(
                        "Builder",
                        null,
                        null,
                        "STONE_BRICKS",
                        "Stone Bricks",
                        null,
                        64,
                        new BigDecimal("100.00"),
                        new BigDecimal("1.5625"),
                        ListingAvailability.UNCHECKED,
                        "Stall 7")));
    }
}
