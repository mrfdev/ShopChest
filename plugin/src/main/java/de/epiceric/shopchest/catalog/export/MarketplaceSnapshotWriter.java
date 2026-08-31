package de.epiceric.shopchest.catalog.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class MarketplaceSnapshotWriter {

    public static final String JSON_FILE_NAME = "marketplace-snapshot.json";
    public static final String CSV_FILE_NAME = "marketplace-snapshot.csv";

    private MarketplaceSnapshotWriter() {
    }

    public static MarketplaceSnapshotFiles write(
            Path outputDirectory,
            MarketplaceSnapshot snapshot
    ) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(snapshot, "snapshot");

        final String json = MarketplaceSnapshotJson.render(snapshot);
        final String csv = MarketplaceSnapshotCsv.render(snapshot);
        final Path jsonFile = outputDirectory.resolve(JSON_FILE_NAME);
        final Path csvFile = outputDirectory.resolve(CSV_FILE_NAME);

        AtomicTextFile.replace(jsonFile, json);
        AtomicTextFile.replace(csvFile, csv);
        return new MarketplaceSnapshotFiles(jsonFile, csvFile);
    }
}
