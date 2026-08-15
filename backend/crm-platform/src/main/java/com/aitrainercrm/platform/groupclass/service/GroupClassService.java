package com.aitrainercrm.platform.groupclass.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.groupclass.dto.CreateGroupClassRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateGroupClassRequest;
import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import com.aitrainercrm.platform.groupclass.repository.GroupClassRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The group-class catalog ("Spin 45", "Sunrise Yoga"). Exactly
 * {@link com.aitrainercrm.platform.membership.service.MembershipPlanService}'s shape - no
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} calls here,
 * since a class type has no {@code ownerId} (see {@link GroupClass}'s javadoc); the controller's
 * {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole authorization story.
 * {@link #findOrThrow} is package-private so {@code ClassSessionService} can reuse it, same
 * reason {@code MembershipPlanService#findOrThrow} is package-private for {@code MembershipService}.
 */
@Service
@RequiredArgsConstructor
public class GroupClassService {

    private final GroupClassRepository groupClassRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<GroupClass> list(UserPrincipal principal, Pageable pageable) {
        return groupClassRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public GroupClass get(UserPrincipal principal, UUID groupClassId) {
        return findOrThrow(principal.getOrganizationId(), groupClassId);
    }

    @Transactional
    public GroupClass create(UserPrincipal principal, CreateGroupClassRequest request) {
        GroupClass groupClass = new GroupClass(principal.getOrganizationId(), request.name());
        applyFields(groupClass, request.description(), request.defaultInstructorId(), request.durationMinutes(), request.capacity(), request.location());
        groupClassRepository.save(groupClass);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "GroupClass", groupClass.getId()));
        return groupClass;
    }

    @Transactional
    public GroupClass update(UserPrincipal principal, UUID groupClassId, UpdateGroupClassRequest request) {
        GroupClass groupClass = findOrThrow(principal.getOrganizationId(), groupClassId);
        groupClass.setName(request.name());
        groupClass.setActive(request.active());
        applyFields(groupClass, request.description(), request.defaultInstructorId(), request.durationMinutes(), request.capacity(), request.location());
        groupClassRepository.save(groupClass);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "GroupClass", groupClass.getId()));
        return groupClass;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID groupClassId) {
        GroupClass groupClass = findOrThrow(principal.getOrganizationId(), groupClassId);
        groupClass.setDeletedAt(Instant.now());
        groupClassRepository.save(groupClass);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "GroupClass", groupClassId));
    }

    GroupClass findOrThrow(UUID organizationId, UUID groupClassId) {
        return groupClassRepository.findActiveByIdAndOrganizationId(groupClassId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupClass", groupClassId));
    }

    private void applyFields(GroupClass groupClass, String description, UUID defaultInstructorId, int durationMinutes, Integer capacity, String location) {
        groupClass.setDescription(description);
        groupClass.setDefaultInstructorId(defaultInstructorId);
        groupClass.setDurationMinutes(durationMinutes);
        groupClass.setCapacity(capacity);
        groupClass.setLocation(location);
    }
}
