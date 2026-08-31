package de.epiceric.shopchest.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses `/shops search` arguments without confusing numbered item keys for pages. */
public final class ShopSearchRequestParser {

    private static final Pattern POSITIVE_INTEGER = Pattern.compile("[1-9][0-9]*");

    private final MaterialKeyResolver materialKeyResolver;

    public ShopSearchRequestParser(MaterialKeyResolver materialKeyResolver) {
        this.materialKeyResolver = Objects.requireNonNull(
                materialKeyResolver,
                "materialKeyResolver");
    }

    public Optional<ShopSearchRequest> parse(List<String> arguments) {
        if (arguments == null || arguments.isEmpty() || arguments.stream().anyMatch(Objects::isNull)) {
            return Optional.empty();
        }

        final Optional<ResolvedMaterial> completeMaterial = materialKeyResolver.resolve(
                String.join(" ", arguments));
        if (completeMaterial.isPresent()) {
            return Optional.of(new ShopSearchRequest(completeMaterial.orElseThrow(), 1));
        }

        if (arguments.size() < 2) {
            return Optional.empty();
        }

        final String pageArgument = arguments.getLast();
        if (!POSITIVE_INTEGER.matcher(pageArgument).matches()) {
            return Optional.empty();
        }

        final int requestedPage;
        try {
            requestedPage = Integer.parseInt(pageArgument);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }

        return materialKeyResolver.resolve(String.join(
                        " ",
                        arguments.subList(0, arguments.size() - 1)))
                .map(material -> new ShopSearchRequest(material, requestedPage));
    }
}
