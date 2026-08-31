package de.epiceric.shopchest.catalog;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** A bounded suggestion index containing only materials in the public catalogue. */
public final class MaterialSuggestionIndex {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]+");
    private static final int MAX_SUGGESTIONS = 5;
    private static final int MAX_QUERY_LENGTH = 64;

    private final List<ResolvedMaterial> materials;

    private MaterialSuggestionIndex(List<ResolvedMaterial> materials) {
        this.materials = List.copyOf(materials);
    }

    public static MaterialSuggestionIndex empty() {
        return new MaterialSuggestionIndex(List.of());
    }

    public static MaterialSuggestionIndex fromMaterials(Collection<Material> source) {
        Objects.requireNonNull(source, "source");
        final Map<String, ResolvedMaterial> unique = new LinkedHashMap<>();
        for (Material material : source) {
            if (material == null || material == Material.AIR) {
                continue;
            }
            final String path = material.name().toLowerCase(Locale.ROOT);
            unique.putIfAbsent(path, new ResolvedMaterial(
                    material, "minecraft:" + path));
        }
        return new MaterialSuggestionIndex(unique.values().stream()
                .sorted(Comparator.comparing(ResolvedMaterial::canonicalKey))
                .toList());
    }

    public List<ResolvedMaterial> suggest(String input, int requestedLimit) {
        final String query = normalize(input);
        if (query.isEmpty() || requestedLimit <= 0) {
            return List.of();
        }
        final int limit = Math.min(requestedLimit, MAX_SUGGESTIONS);
        final int maximumDistance = Math.clamp(query.length() / 4, 1, 4);
        final List<ScoredMaterial> scored = new ArrayList<>();
        for (ResolvedMaterial material : materials) {
            final String path = material.canonicalKey().substring("minecraft:".length());
            final int category;
            final int distance = levenshtein(query, path);
            if (path.equals(query)) {
                category = 0;
            } else if (path.startsWith(query) || query.startsWith(path)) {
                category = 1;
            } else if (path.contains(query) || query.contains(path)) {
                category = 2;
            } else if (distance <= maximumDistance) {
                category = 3;
            } else {
                continue;
            }
            scored.add(new ScoredMaterial(material, category, distance));
        }
        return scored.stream()
                .sorted(Comparator
                        .comparingInt(ScoredMaterial::category)
                        .thenComparingInt(ScoredMaterial::distance)
                        .thenComparing(scoredMaterial ->
                                scoredMaterial.material().canonicalKey()))
                .limit(limit)
                .map(ScoredMaterial::material)
                .toList();
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            return "";
        }
        normalized = SEPARATORS.matcher(normalized).replaceAll("_");
        return normalized.matches("[a-z0-9_]+") ? normalized : "";
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                final int substitution = previous[rightIndex - 1]
                        + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(
                        Math.min(previous[rightIndex] + 1, current[rightIndex - 1] + 1),
                        substitution);
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record ScoredMaterial(
            ResolvedMaterial material,
            int category,
            int distance
    ) {
    }
}
