package com.aitrainercrm.platform.dashboard.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.dashboard.dto.CreateDashboardRequest;
import com.aitrainercrm.platform.dashboard.dto.CreateDashboardWidgetRequest;
import com.aitrainercrm.platform.dashboard.dto.DashboardDataDto;
import com.aitrainercrm.platform.dashboard.dto.DashboardWidgetDataDto;
import com.aitrainercrm.platform.dashboard.dto.DashboardWidgetDto;
import com.aitrainercrm.platform.dashboard.dto.UpdateDashboardRequest;
import com.aitrainercrm.platform.dashboard.dto.UpdateDashboardWidgetRequest;
import com.aitrainercrm.platform.dashboard.entity.Dashboard;
import com.aitrainercrm.platform.dashboard.entity.DashboardWidget;
import com.aitrainercrm.platform.dashboard.repository.DashboardRepository;
import com.aitrainercrm.platform.dashboard.repository.DashboardWidgetRepository;
import com.aitrainercrm.platform.report.service.ReportService;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for Dashboard + DashboardWidget, owner-scoped like
 * {@code WorkflowService} (see {@code Dashboard}'s javadoc for why), plus
 * {@link #getData}, the actual point of the feature: composing each
 * widget's live numbers by calling straight into {@link ReportService}.
 *
 * <p>{@link #getData} deliberately does NOT re-derive its own owner
 * filter - it delegates entirely to whatever {@code ReportService}'s three
 * methods already do internally against the REPORT permission. That means
 * viewing a dashboard's *data* requires the caller to hold some level of
 * REPORT:READ in addition to DASHBOARD:READ (to see the dashboard shell
 * itself) - a deliberate, correct use of the existing permission rather
 * than a shortcut, unlike {@code CustomFieldController#/values} riding on
 * CUSTOM_FIELD instead of the target entity's own permission (see that
 * class's javadoc for why that one *is* a documented simplification).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Permission.Resource RESOURCE = Permission.Resource.DASHBOARD;

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ReportService reportService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Dashboard> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> dashboardRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> dashboardRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Dashboard get(UserPrincipal principal, UUID dashboardId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, dashboard.getOwnerId());
        return dashboard;
    }

    @Transactional(readOnly = true)
    public List<DashboardWidgetDto> listWidgets(UserPrincipal principal, UUID dashboardId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, dashboard.getOwnerId());
        return dashboardWidgetRepository.findByDashboardIdOrderByDisplayOrderAsc(dashboardId).stream().map(DashboardWidgetDto::from).toList();
    }

    @Transactional
    public Dashboard create(UserPrincipal principal, CreateDashboardRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());

        Dashboard dashboard = new Dashboard(principal.getOrganizationId(), ownerId, request.name());
        dashboard.setDescription(request.description());
        dashboardRepository.save(dashboard);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Dashboard", dashboard.getId()));
        return dashboard;
    }

    @Transactional
    public Dashboard update(UserPrincipal principal, UUID dashboardId, UpdateDashboardRequest request) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, dashboard.getOwnerId());

        dashboard.setName(request.name());
        dashboard.setDescription(request.description());
        dashboardRepository.save(dashboard);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Dashboard", dashboard.getId()));
        return dashboard;
    }

    /**
     * MANAGE-gated: marks {@code dashboardId} as this owner's default, unsetting whichever dashboard held that spot
     * before - the same single-consequential-action-gets-MANAGE reasoning WorkflowService#setActive uses.
     *
     * <p>The unset-then-set order here is deliberate and the {@code saveAndFlush} on the old default is load-bearing,
     * not optional. {@code dashboards} has a partial unique index enforcing at most one {@code is_default = true} row
     * per {@code (organization_id, owner_id)} (see V12), and Postgres checks a non-deferred unique index immediately
     * after each row-affecting statement, not at commit. Hibernate's flush order is driven by persistence-context
     * insertion order, not by setter-call order: {@code dashboard} (line above, via findOrThrow) entered the session
     * before {@code current} (looked up just below it), so a plain save()+save() would have flushed dashboard's
     * "is_default = true" UPDATE before current's "is_default = false" one - momentarily giving the owner two default
     * rows and tripping the unique index, which Spring translates to a 409 via
     * GlobalExceptionHandler#handleDataIntegrityViolation. Flushing the unset immediately guarantees it reaches the
     * database first.
     */
    @Transactional
    public Dashboard setDefault(UserPrincipal principal, UUID dashboardId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.MANAGE, dashboard.getOwnerId());

        dashboardRepository
                .findByOrganizationIdAndOwnerIdAndDefaultDashboardTrueAndDeletedAtIsNull(principal.getOrganizationId(), dashboard.getOwnerId())
                .filter(current -> !current.getId().equals(dashboard.getId()))
                .ifPresent(current -> {
                    current.setDefaultDashboard(false);
                    dashboardRepository.saveAndFlush(current);
                });

        dashboard.setDefaultDashboard(true);
        dashboardRepository.save(dashboard);
        return dashboard;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID dashboardId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, dashboard.getOwnerId());

        dashboard.setDeletedAt(Instant.now());
        dashboardRepository.save(dashboard);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Dashboard", dashboardId));
    }

    @Transactional
    public DashboardWidget addWidget(UserPrincipal principal, UUID dashboardId, CreateDashboardWidgetRequest request) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, dashboard.getOwnerId());

        DashboardWidget widget = new DashboardWidget(dashboard.getId(), request.reportType());
        widget.setTitle(blankToNull(request.title()));
        widget.setDisplayOrder(request.displayOrder());
        if (request.width() > 0) widget.setWidth(request.width());
        if (request.height() > 0) widget.setHeight(request.height());
        dashboardWidgetRepository.save(widget);
        return widget;
    }

    @Transactional
    public DashboardWidget updateWidget(UserPrincipal principal, UUID dashboardId, UUID widgetId, UpdateDashboardWidgetRequest request) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, dashboard.getOwnerId());
        DashboardWidget widget = findWidgetOrThrow(dashboardId, widgetId);

        widget.setTitle(blankToNull(request.title()));
        widget.setDisplayOrder(request.displayOrder());
        if (request.width() > 0) widget.setWidth(request.width());
        if (request.height() > 0) widget.setHeight(request.height());
        dashboardWidgetRepository.save(widget);
        return widget;
    }

    @Transactional
    public void removeWidget(UserPrincipal principal, UUID dashboardId, UUID widgetId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, dashboard.getOwnerId());
        DashboardWidget widget = findWidgetOrThrow(dashboardId, widgetId);
        dashboardWidgetRepository.delete(widget);
    }

    @Transactional(readOnly = true)
    public DashboardDataDto getData(UserPrincipal principal, UUID dashboardId) {
        Dashboard dashboard = findOrThrow(principal.getOrganizationId(), dashboardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, dashboard.getOwnerId());

        List<DashboardWidgetDataDto> widgetData = dashboardWidgetRepository.findByDashboardIdOrderByDisplayOrderAsc(dashboardId).stream()
                .map(widget -> DashboardWidgetDataDto.builder()
                        .id(widget.getId())
                        .reportType(widget.getReportType())
                        .title(DashboardWidgetDto.from(widget).title())
                        .displayOrder(widget.getDisplayOrder())
                        .width(widget.getWidth())
                        .height(widget.getHeight())
                        .data(widgetData(principal, widget.getReportType()))
                        .build())
                .toList();

        return DashboardDataDto.builder().dashboardId(dashboard.getId()).name(dashboard.getName()).widgets(widgetData).build();
    }

    private Object widgetData(UserPrincipal principal, DashboardWidget.ReportType reportType) {
        return switch (reportType) {
            case PIPELINE_BY_STAGE -> reportService.pipelineByStage(principal);
            case LEAD_FUNNEL -> reportService.leadFunnel(principal);
            case LEADERBOARD -> reportService.repLeaderboard(principal);
        };
    }

    private Dashboard findOrThrow(UUID organizationId, UUID dashboardId) {
        return dashboardRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(dashboardId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Dashboard", dashboardId));
    }

    private DashboardWidget findWidgetOrThrow(UUID dashboardId, UUID widgetId) {
        return dashboardWidgetRepository
                .findByIdAndDashboardId(widgetId, dashboardId)
                .orElseThrow(() -> new ResourceNotFoundException("DashboardWidget", widgetId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " dashboards owned by yourself");
        }
        boolean exists = userRepository.findActiveById(requestedOwnerId).map(u -> principal.getOrganizationId().equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", requestedOwnerId);
        }
        return requestedOwnerId;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
