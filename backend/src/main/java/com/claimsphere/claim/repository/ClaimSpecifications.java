package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.dto.ClaimSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic claim search predicate. All filters are optional and combined with AND.
 */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public static Specification<Claim> matching(ClaimSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.claimantId() != null) {
                predicates.add(cb.equal(root.get("claimant").get("id"), criteria.claimantId()));
            }
            if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
                predicates.add(root.get("status").in(criteria.statuses()));
            }
            if (criteria.market() != null) {
                predicates.add(cb.equal(root.get("market"), criteria.market()));
            }
            if (criteria.claimType() != null) {
                predicates.add(cb.equal(root.get("claimType"), criteria.claimType()));
            }
            if (criteria.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), criteria.priority()));
            }
            if (criteria.currency() != null) {
                predicates.add(cb.equal(root.get("currency"), criteria.currency()));
            }
            if (criteria.unassignedOnly()) {
                predicates.add(cb.isNull(root.get("assignedOfficer")));
            } else if (criteria.assignedOfficerId() != null) {
                predicates.add(cb.equal(root.get("assignedOfficer").get("id"), criteria.assignedOfficerId()));
            }
            if (criteria.incidentFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("incidentDate"), criteria.incidentFrom()));
            }
            if (criteria.incidentTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("incidentDate"), criteria.incidentTo()));
            }
            if (criteria.submittedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        criteria.submittedFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (criteria.submittedTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        criteria.submittedTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
