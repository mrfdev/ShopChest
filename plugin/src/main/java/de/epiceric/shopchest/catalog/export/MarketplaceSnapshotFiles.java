package de.epiceric.shopchest.catalog.export;

import java.nio.file.Path;
import java.util.Objects;

public record MarketplaceSnapshotFiles(Path json, Path csv) {

    public MarketplaceSnapshotFiles {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(csv, "csv");
    }
}
