package com.claimsphere.dashboard.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.dashboard.dto.DashboardFilter;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse.CurrencyLiability;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse.MarketSummary;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse.OfficerWorkload;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.dto.UserResponse;
import com.claimsphere.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregated claims metrics for managers. Counts and money totals are computed with grouped database queries;
 * only the time-based metrics (ageing, processing time) are derived in memory from projected timestamps.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final Set<ClaimStatus> UNDECIDED = EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
            ClaimStatus.INFO_REQUESTED);
    static final Set<ClaimStatus> DECIDED = EnumSet.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED,
            ClaimStatus.SETTLED);
    private static final Set<ClaimStatus> CLOSED_BY_DECISION = EnumSet.of(ClaimStatus.SETTLED, ClaimStatus.REJECTED);
    private static final int MAX_AGEING_DAYS = 3650;
    private static final int MAX_OFFICERS = 100;

    private static final Map<String, String> DEFINITIONS = Map.of(
            "outstandingClaims", "Claims not yet closed: SUBMITTED, UNDER_REVIEW, INFO_REQUESTED or APPROVED.",
            "estimatedExposure", "Sum of estimated loss on claims awaiting a decision "
                    + "(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED), per currency.",
            "approvedLiability", "Sum of approved amounts on APPROVED claims awaiting settlement, per currency.",
            "settledAmount", "Sum of amounts paid on SETTLED claims, per currency.",
            "currencies", "Amounts in different currencies are never added together; no FX conversion is applied.",
            "processingTime", "Average days from submission to closure for SETTLED and REJECTED claims.",
            "ageing", "Age in days since submission of outstanding claims.",
            "officerWorkload", "Per claims officer: open assigned claims, decided claims (approved, rejected or "
                    + "settled) and settled claims.");

    private final EntityManager entityManager;
    private final UserService userService;
    private final Clock clock;

    public DashboardService(EntityManager entityManager, UserService userService, Clock clock) {
        this.entityManager = entityManager;
        this.userService = userService;
        this.clock = clock;
    }

    public ManagerDashboardResponse managerDashboard(DashboardFilter filter) {
        validate(filter);
        Instant now = clock.instant();

        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            byStatus.put(status, 0L);
        }
        Map<Market, long[]> byMarket = new EnumMap<>(Market.class);
        Map<CurrencyCode, Totals> byCurrency = new EnumMap<>(CurrencyCode.class);
        long total = 0;
        long outstanding = 0;

        for (Tuple row : statusMarketCurrencyTotals(filter)) {
            ClaimStatus status = row.get(0, ClaimStatus.class);
            Market market = row.get(1, Market.class);
            CurrencyCode currency = row.get(2, CurrencyCode.class);
            long count = row.get(3, Long.class);
            boolean open = !status.isTerminal();

            total += count;
            outstanding += open ? count : 0;
            byStatus.merge(status, count, Long::sum);
            long[] marketCounts = byMarket.computeIfAbsent(market, m -> new long[2]);
            marketCounts[0] += count;
            marketCounts[1] += open ? count : 0;

            Totals totals = byCurrency.computeIfAbsent(currency, c -> new Totals());
            totals.outstanding += open ? count : 0;
            if (UNDECIDED.contains(status)) {
                totals.estimatedExposure = totals.estimatedExposure.add(orZero(row.get(4, BigDecimal.class)));
            } else if (status == ClaimStatus.APPROVED) {
                totals.approvedLiability = totals.approvedLiability.add(orZero(row.get(5, BigDecimal.class)));
            } else if (status == ClaimStatus.SETTLED) {
                totals.settledAmount = totals.settledAmount.add(orZero(row.get(6, BigDecimal.class)));
            }
        }

        List<MarketSummary> markets = new ArrayList<>();
        byMarket.forEach((market, counts) -> markets.add(new MarketSummary(market, counts[0], counts[1])));
        List<CurrencyLiability> liability = new ArrayList<>();
        byCurrency.forEach((currency, totals) -> liability.add(new CurrencyLiability(currency,
                totals.estimatedExposure, totals.approvedLiability, totals.settledAmount, totals.outstanding)));

        return new ManagerDashboardResponse(now, filter, total, outstanding, countUnassignedOpen(filter), byStatus,
                markets, liability, officerWorkload(filter),
                DashboardCalculations.processingTime(closedClaimDurations(filter)),
                DashboardCalculations.ageing(openClaimCreatedAt(filter), now, filter.ageingThresholdDays()),
                DEFINITIONS);
    }

    private List<Tuple> statusMarketCurrencyTotals(DashboardFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Claim> claim = query.from(Claim.class);
        query.multiselect(claim.get("status"), claim.get("market"), claim.get("currency"), cb.count(claim),
                        cb.sum(claim.<BigDecimal>get("estimatedLoss")), cb.sum(claim.<BigDecimal>get("approvedAmount")),
                        cb.sum(claim.<BigDecimal>get("settledAmount")))
                .where(filterPredicates(cb, claim, filter).toArray(Predicate[]::new))
                .groupBy(claim.get("status"), claim.get("market"), claim.get("currency"));
        return entityManager.createQuery(query).getResultList();
    }

    private long countUnassignedOpen(DashboardFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Claim> claim = query.from(Claim.class);
        List<Predicate> predicates = filterPredicates(cb, claim, filter);
        predicates.add(cb.isNull(claim.get("assignedOfficer")));
        predicates.add(claim.get("status").in(ClaimStatus.openStatuses()));
        query.select(cb.count(claim)).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(query).getSingleResult();
    }

    private List<OfficerWorkload> officerWorkload(DashboardFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Claim> claim = query.from(Claim.class);
        Join<Claim, User> officer = claim.join("assignedOfficer");
        query.multiselect(officer.get("id"), claim.get("status"), cb.count(claim))
                .where(filterPredicates(cb, claim, filter).toArray(Predicate[]::new))
                .groupBy(officer.get("id"), claim.get("status"));

        Map<UUID, long[]> counts = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            ClaimStatus status = row.get(1, ClaimStatus.class);
            long count = row.get(2, Long.class);
            long[] officerCounts = counts.computeIfAbsent(row.get(0, UUID.class), id -> new long[3]);
            officerCounts[0] += status.isTerminal() ? 0 : count;
            officerCounts[1] += DECIDED.contains(status) ? count : 0;
            officerCounts[2] += status == ClaimStatus.SETTLED ? count : 0;
        }
        List<OfficerWorkload> workload = new ArrayList<>();
        for (UserResponse officerUser : userService.listUsers(Role.CLAIMS_OFFICER,
                PageRequest.of(0, MAX_OFFICERS, Sort.by("fullName"))).getContent()) {
            long[] officerCounts = counts.getOrDefault(officerUser.id(), new long[3]);
            workload.add(new OfficerWorkload(officerUser.id(), officerUser.fullName(), officerCounts[0],
                    officerCounts[1], officerCounts[2]));
        }
        return workload;
    }

    private List<Instant> openClaimCreatedAt(DashboardFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Instant> query = cb.createQuery(Instant.class);
        Root<Claim> claim = query.from(Claim.class);
        List<Predicate> predicates = filterPredicates(cb, claim, filter);
        predicates.add(claim.get("status").in(ClaimStatus.openStatuses()));
        query.select(claim.get("createdAt")).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(query).getResultList();
    }

    private List<Instant[]> closedClaimDurations(DashboardFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Claim> claim = query.from(Claim.class);
        List<Predicate> predicates = filterPredicates(cb, claim, filter);
        predicates.add(claim.get("status").in(CLOSED_BY_DECISION));
        predicates.add(cb.isNotNull(claim.get("closedAt")));
        query.multiselect(claim.get("createdAt"), claim.get("closedAt")).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new Instant[]{row.get(0, Instant.class), row.get(1, Instant.class)})
                .toList();
    }

    private static List<Predicate> filterPredicates(CriteriaBuilder cb, Root<Claim> claim, DashboardFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.market() != null) {
            predicates.add(cb.equal(claim.get("market"), filter.market()));
        }
        if (filter.currency() != null) {
            predicates.add(cb.equal(claim.get("currency"), filter.currency()));
        }
        if (filter.submittedFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(claim.get("createdAt"),
                    filter.submittedFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (filter.submittedTo() != null) {
            predicates.add(cb.lessThan(claim.get("createdAt"),
                    filter.submittedTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        return predicates;
    }

    private static void validate(DashboardFilter filter) {
        if (filter.submittedFrom() != null && filter.submittedTo() != null
                && filter.submittedFrom().isAfter(filter.submittedTo())) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "submittedFrom must not be after submittedTo");
        }
        int ageing = filter.ageingThresholdDays();
        if (ageing < 1 || ageing > MAX_AGEING_DAYS) {
            throw new BusinessRuleException("INVALID_AGEING_THRESHOLD",
                    "ageingDays must be between 1 and " + MAX_AGEING_DAYS);
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static final class Totals {
        private BigDecimal estimatedExposure = BigDecimal.ZERO;
        private BigDecimal approvedLiability = BigDecimal.ZERO;
        private BigDecimal settledAmount = BigDecimal.ZERO;
        private long outstanding;
    }
}
