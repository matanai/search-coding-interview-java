package albums.challenge;

import albums.challenge.models.Entry;
import albums.challenge.models.Facet;
import albums.challenge.models.Results;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final int PRICE_RANGE_STEP = 5;

    private static final Comparator<String> YEAR_COMPARATOR_DESC =
            Comparator.<String, Integer>comparing(Integer::parseInt).reversed();
    private static final Comparator<String> PRICE_COMPARATOR =
            Comparator.comparing(e -> Integer.parseInt(e.split(" - ")[0]));

    Results search(List<Entry> entries, String query) {
        return search(entries, query, List.of(), List.of());
    }

    Results search(List<Entry> entries, String query, List<String> selectedYears, List<String> selectedPrices) {
        var queryResult = query.isBlank() ? entries : filterByQuery(entries, query);

        var itemsFilteredByYear = filterBy(queryResult, selectedYears, this::extractYear);
        var itemsFilteredByPrice = filterBy(queryResult, selectedPrices, this::extractPriceRange);

        var yearFacetList = mapToSortedFacets(
                countEntries(itemsFilteredByPrice, selectedYears, this::extractYear),
                YEAR_COMPARATOR_DESC
        );

        var priceFacetList = mapToSortedFacets(
                countEntries(itemsFilteredByYear, selectedPrices, this::extractPriceRange),
                PRICE_COMPARATOR
        );

        // The last list that has all filters applied is the final result. Order doesn't matter.
        var itemsFilteredByYearAndPrice = filterBy(itemsFilteredByYear, selectedPrices, this::extractPriceRange);

        return new Results(
                itemsFilteredByYearAndPrice,
                Map.ofEntries(
                        Map.entry("year", yearFacetList),
                        Map.entry("price", priceFacetList)
                ),
                query
        );
    }

    Set<String> tokenizeToWords(String query) {
        return Set.copyOf(List.of(query.toLowerCase().split("\\W+")));
    }

    List<Entry> filterByQuery(List<Entry> entries, String query) {
        var words = tokenizeToWords(query);
        return entries.stream().filter(entry -> {
            var tokens = tokenizeToWords(entry.title());
            return tokens.containsAll(words);
        }).toList();
    }

    /**
     * Generic filtering of list of entries by provided classifier function.
     */
    List<Entry> filterBy(List<Entry> entries, List<String> filters, Function<Entry, String> classifier) {
        if (filters.isEmpty()) {
            return entries;
        }

        return entries.stream()
                .filter(e -> filters.contains(classifier.apply(e)))
                .toList();
    }

    /**
     * Counts entries by their value, e.g. "2012" -> 12, "0-5" -> 2. Any value in selected that has no matches
     * is still included with a count of 0 (so user selection never disappears from the UI).
     */
    Map<String, Integer> countEntries(List<Entry> entries, List<String> selected, Function<Entry, String> keyFunction) {
        var counts = entries.stream()
                .collect(Collectors.groupingBy(keyFunction, Collectors.summingInt(_ -> 1)));

        // User-selected filters that have no matches should also end up on UI with a count of 0
        selected.forEach(e -> counts.putIfAbsent(e, 0));
        return counts;
    }

    /**
     * Convert grouping map of filter counts to facets while applying sorting from the provided comparator.
     */
    List<Facet> mapToSortedFacets(Map<String, Integer> counts, Comparator<String> comparator) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(comparator))
                .map(e -> new Facet(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Extract year part from release date, e.g. "2008-01-29T00:00:00-07:00" -> "2008".
     */
    String extractYear(Entry entry) {
        return String.valueOf(OffsetDateTime.parse(entry.release_date()).getYear());
    }

    /**
     * Group price into one of the predetermined price ranges with step 5, e.g., $2.94 -> "0-5", $16.99 -> "15-20",
     * etc. Using Math.floor() makes lower bound inclusive ($10.00 goes into "10-15", not "5-10").
     */
    String extractPriceRange(Entry entry) {
        int lower = (int) Math.floor(entry.price() / PRICE_RANGE_STEP) * PRICE_RANGE_STEP;
        int upper = lower + PRICE_RANGE_STEP;

        return lower + " - " + upper;
    }
}
