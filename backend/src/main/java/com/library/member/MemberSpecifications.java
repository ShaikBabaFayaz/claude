package com.library.member;

import com.library.common.persistence.LikePatterns;
import com.library.common.validation.TextNormalizer;
import com.library.member.dto.MemberSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * The member list query (AC-3.BE.8, D9): optional exact status, optional case-insensitive "contains" on name or email
 * (LIKE wildcards in the input escaped, values bound), ordered by {@code lower(name)} then {@code id}, which is total
 * and stable. Spring Data drops the ordering for the count query.
 */
final class MemberSpecifications {

    private MemberSpecifications() {
    }

    static Specification<Member> matching(MemberSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.search() != null) {
                String pattern = LikePatterns.contains(TextNormalizer.lowerCase(criteria.search()));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LikePatterns.ESCAPE)));
            }
            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(cb.asc(cb.lower(root.get("name"))), cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
