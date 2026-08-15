package com.aitrainercrm.platform.groupclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.groupclass.dto.CreateClassAttendanceRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import com.aitrainercrm.platform.groupclass.repository.ClassAttendanceRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link ClassAttendanceService}'s javadoc for the capacity-check business rule this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class ClassAttendanceServiceTest {

    @Mock private ClassAttendanceRepository classAttendanceRepository;
    @Mock private ClassSessionService classSessionService;
    @Mock private GroupClassService groupClassService;
    @Mock private ContactRepository contactRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClassAttendanceService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID groupClassId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassAttendanceService(
                classAttendanceRepository, classSessionService, groupClassService, contactRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(instructorId, "instructor@example.com", organizationId, List.of());
    }

    private ClassSession session(Integer capacityOverride) {
        ClassSession session = new ClassSession(organizationId, groupClassId, instructorId, Instant.now(), Instant.now().plusSeconds(2700));
        session.setId(sessionId);
        session.setCapacityOverride(capacityOverride);
        return session;
    }

    @Test
    void create_whenSessionHasRoom_copiesSessionOwnerOntoTheAttendance() {
        when(classSessionService.findOrThrow(organizationId, sessionId)).thenReturn(session(2));
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(classAttendanceRepository.countByClassSessionIdAndDeletedAtIsNullAndStatusIn(any(), anyList())).thenReturn(1L);

        ClassAttendance result = service.create(principal(), new CreateClassAttendanceRequest(sessionId, contactId, null));

        assertThat(result.getOwnerId()).isEqualTo(instructorId);
        assertThat(result.getStatus()).isEqualTo(ClassAttendance.Status.REGISTERED);
        verify(classAttendanceRepository).save(result);
    }

    @Test
    void create_whenSessionIsFull_throwsBusinessException() {
        when(classSessionService.findOrThrow(organizationId, sessionId)).thenReturn(session(2));
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(classAttendanceRepository.countByClassSessionIdAndDeletedAtIsNullAndStatusIn(any(), anyList())).thenReturn(2L);

        assertThatThrownBy(() -> service.create(principal(), new CreateClassAttendanceRequest(sessionId, contactId, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("full");
        verify(classAttendanceRepository, never()).save(any());
    }

    @Test
    void create_whenSessionCapacityIsUnlimited_neverChecksTheRosterCount() {
        when(classSessionService.findOrThrow(organizationId, sessionId)).thenReturn(session(null));
        when(groupClassService.findOrThrow(organizationId, groupClassId)).thenReturn(new GroupClass(organizationId, "Spin 45"));
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        service.create(principal(), new CreateClassAttendanceRequest(sessionId, contactId, null));

        verify(classAttendanceRepository, never()).countByClassSessionIdAndDeletedAtIsNullAndStatusIn(any(), anyList());
        verify(classAttendanceRepository).save(any());
    }

    @Test
    void updateStatus_movingToAttended_stampsCheckedInAtOnlyOnTheFirstTime() {
        UUID attendanceId = UUID.randomUUID();
        ClassAttendance attendance = new ClassAttendance(organizationId, sessionId, contactId, instructorId);
        attendance.setId(attendanceId);
        when(classAttendanceRepository.findActiveByIdAndOrganizationId(attendanceId, organizationId)).thenReturn(java.util.Optional.of(attendance));

        ClassAttendance firstCheckIn = service.updateStatus(principal(), attendanceId, ClassAttendance.Status.ATTENDED);
        Instant firstCheckedInAt = firstCheckIn.getCheckedInAt();
        assertThat(firstCheckedInAt).isNotNull();

        firstCheckIn.setStatus(ClassAttendance.Status.NO_SHOW);
        ClassAttendance secondCheckIn = service.updateStatus(principal(), attendanceId, ClassAttendance.Status.ATTENDED);

        assertThat(secondCheckIn.getCheckedInAt()).isEqualTo(firstCheckedInAt);
    }
}
