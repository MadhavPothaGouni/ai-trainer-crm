package com.aitrainercrm.platform.intakeform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.intakeform.dto.CreateIntakeFormSubmissionRequest;
import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import com.aitrainercrm.platform.intakeform.entity.IntakeFormSubmission;
import com.aitrainercrm.platform.intakeform.repository.IntakeFormRepository;
import com.aitrainercrm.platform.intakeform.repository.IntakeFormSubmissionRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link IntakeFormSubmissionService}'s javadoc for the parent-form validation covered here. */
@ExtendWith(MockitoExtension.class)
class IntakeFormSubmissionServiceTest {

    @Mock private IntakeFormSubmissionRepository intakeFormSubmissionRepository;
    @Mock private IntakeFormRepository intakeFormRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private IntakeFormSubmissionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID formId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IntakeFormService intakeFormService = new IntakeFormService(intakeFormRepository, events);
        service = new IntakeFormSubmissionService(
                intakeFormSubmissionRepository, intakeFormService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void create_formExists_savesSubmission() {
        IntakeForm form = new IntakeForm(organizationId, "New Client Intake");
        form.setId(formId);
        when(intakeFormRepository.findActiveByIdAndOrganizationId(formId, organizationId)).thenReturn(Optional.of(form));
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        IntakeFormSubmission submission = service.create(
                principal(), new CreateIntakeFormSubmissionRequest(formId, contactId, "{\"answer\":\"yes\"}", null, null));

        assertThat(submission.getFormId()).isEqualTo(formId);
        assertThat(submission.getContactId()).isEqualTo(contactId);
        assertThat(submission.getResponses()).isEqualTo("{\"answer\":\"yes\"}");
    }

    @Test
    void create_formDoesNotExist_throwsNotFound() {
        when(intakeFormRepository.findActiveByIdAndOrganizationId(formId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal(), new CreateIntakeFormSubmissionRequest(formId, contactId, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
