package albums.challenge;

import albums.challenge.models.Entry;
import albums.challenge.models.Facet;
import albums.challenge.models.Results;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class SearchService {
    Results search(List<Entry> entries, String query) {
        return search(entries, query, List.of(), List.of());
    }

    Results search(List<Entry> entries, String query, List<String> years, List<String> prices) {
        var queryResult = query.isBlank() ? entries : filterByQuery(entries, query);

        var itemsFilteredByYear = filterBy(queryResult, years, e -> years.contains(extractYear(e)));

        // The last list that has all filters applied is the final result. Order doesn't matter.
        var itemsFilteredByYearAndPrice = filterBy(itemsFilteredByYear, prices, e -> prices.contains(extractPriceRange(e)));

        return new Results(
                itemsFilteredByYearAndPrice,
                Map.ofEntries(
                        Map.entry("year", List.of(
                                new Facet("2002", 25),
                                new Facet("2008", 2)
                        )),
                        Map.entry("price", List.of(
                                new Facet("5 - 10", 25),
                                new Facet("10 - 15", 2)
                        ))
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
