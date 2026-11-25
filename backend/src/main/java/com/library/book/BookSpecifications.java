package com.library.book;

import com.library.book.dto.BookSearchCriteria;
import com.library.common.persistence.LikePatterns;
import com.library.common.validation.TextNormalizer;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * The catalogue query (AC-2.BE.1..4): filters are AND-combined, text filters are case-insensitive "contains" with the
 * {@code LIKE} wildcards of the input escaped, availability is derived from {@code total_copies - active_loan_count}.
 * All values are bind parameters (criteria API), never concatenated into SQL.
 *
 * <p>The ordering ({@code lower(title)}, then {@code isbn}, which is unique, so the order is total and stable) is part
 * of the specification. Spring Data drops it for the count query.
 */
final class BookSpecifications {

    private BookSpecifications() {
    }

    static Specification<Book> matching(BookSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.title() != null) {
                predicates.add(containsIgnoreCase(cb.lower(root.get("title")), cb, criteria.title()));
            }
            if (criteria.author() != null) {
                predicates.add(containsIgnoreCase(cb.lower(root.get("author")), cb, criteria.author()));
            }
            if (criteria.isbn() != null) {
                // stored ISBNs are already normalised and upper-case; the filter was normalised the same way
                predicates.add(cb.like(root.get("isbn"), LikePatterns.contains(criteria.isbn()), LikePatterns.ESCAPE));
            }
            if (criteria.availability() != null) {
                Expression<Integer> available = cb.diff(root.<Integer>get("totalCopies"), root.<Integer>get("activeLoanCount"));
                predicates.add(criteria.availability() == Availability.AVAILABLE
                        ? cb.greaterThanOrEqualTo(available, 1)
                        : cb.equal(available, 0));
            }
            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(cb.asc(cb.lower(root.get("title"))), cb.asc(root.get("isbn")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsIgnoreCase(
            Expression<String> lowerCased, jakarta.persistence.criteria.CriteriaBuilder cb, String text) {
        return cb.like(lowerCased, LikePatterns.contains(TextNormalizer.lowerCase(text)), LikePatterns.ESCAPE);
    }
}
