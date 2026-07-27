package albums.challenge;

import albums.challenge.models.Entry;
import albums.challenge.models.Facet;
import albums.challenge.models.Results;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class SearchService {
    Results search(List<Entry> entries, String query) {
        return search(entries, query, List.of(), List.of());
    }

    Results search(List<Entry> entries, String query, List<String> years, List<String> prices) {
        var queryResult = query.isBlank() ? entries : filterByQuery(entries, query);

        var itemsFilteredByYear = filterBy(queryResult, years, e -> years.contains(extractYear(e)));
        var itemsFilteredByPrice = filterBy(queryResult, prices, e -> prices.contains(extractPriceRange(e)));

        var yearFacetList = buildFacetList(itemsFilteredByPrice, years, this::extractYear, Comparator.<String, Integer>comparing(Integer::parseInt).reversed());
        var priceFacetList = buildFacetList(itemsFilteredByYear, prices, this::extractPriceRange, Comparator.comparing(e -> Integer.parseInt(e.split(" - ")[0])));

        // The last list that has all filters applied is the final result. Order doesn't matter.
        var itemsFilteredByYearAndPrice = filterBy(itemsFilteredByYear, prices, e -> prices.contains(extractPriceRange(e)));

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
     * Generic filtering of list of entries by provided Predicate
     */
    List<Entry> filterBy(List<Entry> entryList, List<String> filterList, Predicate<Entry> filterFunction) {
        if (filterList.isEmpty()) {
            return entryList;
        }

        return entryList.stream()
                .filter(filterFunction)
                .toList();
    }

    /**
     * Generic facet builder that takes a list of filtered entries, groups it into a map
     * of key to number of matches, e.g., "2012" - 12, "0-5" - 2, then maps them to list
     * of facets while applying sorting from the provided comparator.
     */
    List<Facet> buildFacetList(
            List<Entry> entries,
            List<String> selected,
            Function<Entry, String> keyFunction,
            Comparator<String> comparator
    ) {
        var countMap = entries.stream()
                .collect(Collectors.groupingBy(
                        keyFunction,
                        Collectors.summingInt(_ -> 1)
                ));

        // User-selected filters that dont have match should also end up on UI with 0 count
        selected.forEach(e -> countMap.putIfAbsent(e, 0));

        return countMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(comparator))
                .map(e -> new Facet(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Extract year part from release date, e.g. "2008-01-29T00:00:00-07:00" -> "2008"
     */
    String extractYear(Entry entry) {
        return String.valueOf(OffsetDateTime.parse(entry.release_date()).getYear());
    }

    /**
     * Group price into one of the predetermined price ranges with step 5, e.g.,
     * $2.94 -> "0-5", $16.99 -> "15-20", etc. Using Math.floor() makes lower
     * bound inclusive ($10.00 goes into "10-15", not "5-10")
     */
    String extractPriceRange(Entry entry) {
        int lower = (int) Math.floor(entry.price() / 5) * 5;
        int upper = lower + 5;

        return lower + " - " + upper;
    }
}
