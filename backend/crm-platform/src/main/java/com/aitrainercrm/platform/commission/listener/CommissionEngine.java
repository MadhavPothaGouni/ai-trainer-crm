package com.aitrainercrm.platform.commission.listener;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import com.aitrainercrm.platform.commission.repository.CommissionPlanRepository;
import com.aitrainercrm.platform.commission.repository.CommissionRecordRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates exactly one {@link CommissionRecord} the moment an Opportunity's currently-persisted
 * stage is read as {@code CLOSED_WON} - the eighth {@code @EventListener} on the {@link
 * CrmAuditEvents} bus, alongside {@code WebhookDispatchListener}, {@code AuditEventListener},
 * {@code WorkflowEngineListener}, {@code TerritoryAssignmentListener}, {@code
 * DuplicateDetectionListener}, {@code LeadScoringEngine}, and {@code CommissionEngine} itself.
 *
 * <p>Unlike every other listener on this bus, this one deliberately does NOT distinguish a stage
 * change into CLOSED_WON from any other Opportunity edit - {@link CrmAuditEvents.RecordUpdated}
 * carries no old/new field values to diff against (see that record's own javadoc: it's
 * intentionally generic across all four CRM entity types), so re-deriving "did the stage just
 * change" from the event itself isn't possible without adding fields no other listener needs. The
 * simpler, equally-correct alternative this class takes instead: react to every update, reload the
 * Opportunity's current state fresh, and rely on idempotency - {@link
 * CommissionRecordRepository#existsByOpportunityId} plus the real {@code
 * uq_commission_records_opportunity} unique constraint (V29) - to make firing on every edit to an
 * already-closed deal (or on the same close event twice, under concurrent delivery) harmless rather
 * than something that has to be prevented by detecting a transition. {@code LeadScoringEngine}'s
 * {@code onRecordUpdated} takes the same "reload and recompute, let idempotency or plain
 * overwrite-with-the-same-answer handle re-fires" approach, just without a uniqueness constraint
 * behind it since a rescore is naturally idempotent on its own.
 *
 * <p>Plan resolution: an active {@link CommissionPlan} owned by the Opportunity's current {@code
 * ownerId} wins if one exists; otherwise, an active plan for that owner's current team is used;
 * otherwise, no commission record is created at all - the same "no matching rule, nothing happens"
 * default {@code TerritoryAssignmentListener} uses when no {@code TerritoryRule} matches a new
 * record.
 */
@Component
@RequiredArgsConstructor
public class CommissionEngine {

    private static final int MONEY_SCALE = 2;

    private final OpportunityRepository opportunityRepository;
    private final CommissionPlanRepository commissionPlanRepository;
    private final CommissionRecordRepository commissionRecordRepository;
    private final UserRepository userRepository;

    @Async
    @EventListener
    @Transactional
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        if (!"Opportunity".equals(event.resourceType())) return;
        maybeCreateCommission(event.organizationId(), event.resourceId());
    }

    @Async
    @EventListener
    @Transactional
    public void onRecordUpdated(CrmAuditEvents.RecordUpdated event) {
        if (!"Opportunity".equals(event.resourceType())) return;
        maybeCreateCommission(event.organizationId(), event.resourceId());
    }

    private void maybeCreateCommission(UUID organizationId, UUID opportunityId) {
        Opportunity opportunity = opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId).orElse(null);
        if (opportunity == null || opportunity.getStage() != Opportunity.Stage.CLOSED_WON) return;
        if (commissionRecordRepository.existsByOpportunityId(opportunityId)) return;

        CommissionPlan plan = resolvePlan(organizationId, opportunity.getOwnerId());
        if (plan == null) return;

        BigDecimal dealAmount = opportunity.getAmount() == null ? BigDecimal.ZERO : opportunity.getAmount();
        BigDecimal commissionAmount = computeCommission(plan.getRateType(), plan.getRate(), dealAmount);

        CommissionRecord record = new CommissionRecord(
                organizationId, opportunity.getId(), opportunity.getOwnerId(), plan.getId(), dealAmount, plan.getRateType(),
                plan.getRate(), commissionAmount);
        commissionRecordRepository.save(record);
    }

    private CommissionPlan resolvePlan(UUID organizationId, UUID ownerId) {
        List<CommissionPlan> individualPlans =
                commissionPlanRepository.findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(organizationId, ownerId);
        if (!individualPlans.isEmpty()) return individualPlans.get(0);

        User owner = userRepository.findActiveById(ownerId).orElse(null);
        if (owner == null || owner.getTeamId() == null) return null;

        List<CommissionPlan> teamPlans =
                commissionPlanRepository.findByOrganizationIdAndTeamIdAndActiveTrueOrderByNameAsc(organizationId, owner.getTeamId());
        return teamPlans.isEmpty() ? null : teamPlans.get(0);
    }

    private BigDecimal computeCommission(CommissionPlan.RateType rateType, BigDecimal rate, BigDecimal dealAmount) {
        BigDecimal raw = rateType == CommissionPlan.RateType.PERCENTAGE
                ? dealAmount.multiply(rate).divide(BigDecimal.valueOf(100), MONEY_SCALE + 2, RoundingMode.HALF_UP)
                : rate;
        return raw.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
