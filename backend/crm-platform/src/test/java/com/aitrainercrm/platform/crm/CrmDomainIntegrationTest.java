package com.aitrainercrm.platform.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.lead.dto.ConvertLeadRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.opportunity.dto.UpdateOpportunityStageRequest;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the CRM domain that a pure-Mockito test can't give:
 * real {@code @PreAuthorize} authority strings matching what V2 actually
 * seeds and RoleService actually assigns to OWNER/MEMBER, and - the piece
 * this test exists specifically to prove - {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}'s
 * OWN-scope visibility filtering working all the way through a real MEMBER
 * user's list request against a real database, not just the unit-level
 * decision in isolation.
 *
 * <p>Walks the full lifecycle once: account -> contact -> opportunity ->
 * stage transition to CLOSED_WON -> lead -> lead conversion, all as the
 * org's OWNER, then invites a MEMBER teammate and shows that the teammate's
 * own new account is visible to them while the OWNER's pre-existing account
 * is not, and that the OWNER (ORGANIZATION scope) still sees both.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CrmDomainIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void accountContactOpportunityLeadLifecycle_andOwnScopeListFiltering_endToEnd() throws Exception {
        String ownerEmail = "crm-owner-%d@example.com".formatted(System.nanoTime());
        RegisterRequest registerRequest =
                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Globex Holdings");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- Account ---
        CreateAccountRequest createAccount = new CreateAccountRequest(
                "Globex Corporation", "Manufacturing", "https://globex.example.com", "555-0100",
                "1 Industrial Way", "Springfield", "IL", "62701", "USA",
                new BigDecimal("5000000"), 250, "Key manufacturing account", null);
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Globex Corporation"))
                .andReturn();
        String accountId = readField(accountResult, "data", "id");

        // --- Contact, linked to the account ---
        CreateContactRequest createContact = new CreateContactRequest(
                "Hank", "Scorpio", "hank.scorpio@globex.example.com", "555-0101", "VP of Operations",
                "Primary contact for the account", UUID.fromString(accountId), null);
        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content(objectMapper.writeValueAsString(createContact)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(accountId))
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        // --- Opportunity, linked to both ---
        CreateOpportunityRequest createOpportunity = new CreateOpportunityRequest(
                UUID.fromString(accountId), UUID.fromString(contactId), "Globex Q3 Expansion Deal",
                new BigDecimal("120000.00"), "USD", LocalDate.now().plusMonths(2), "New assembly line contract", null);
        MvcResult opportunityResult = mockMvc
                .perform(authed(post("/api/v1/opportunities"), ownerToken).content(objectMapper.writeValueAsString(createOpportunity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stage").value("PROSPECTING"))
                .andReturn();
        String opportunityId = readField(opportunityResult, "data", "id");

        // Moving to CLOSED_WON should stamp actualCloseDate - that's the one piece of
        // business logic OpportunityService#updateStage has beyond a plain field update.
        MvcResult stageResult = mockMvc
                .perform(authed(patch("/api/v1/opportunities/" + opportunityId + "/stage"), ownerToken)
                        .content(objectMapper.writeValueAsString(new UpdateOpportunityStageRequest(Opportunity.Stage.CLOSED_WON))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("CLOSED_WON"))
                .andReturn();
        String actualCloseDate = readField(stageResult, "data", "actualCloseDate");
        assertThat(actualCloseDate).isNotBlank();

        // --- Lead, then conversion ---
        CreateLeadRequest createLead = new CreateLeadRequest(
                "Monty", "Burns", "monty.burns@springfield-power.example.com", "555-0199",
                "Springfield Power", "Owner", Lead.Source.REFERRAL, "Wants a CRM demo", null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andReturn();
        String leadId = readField(leadResult, "data", "id");

        ConvertLeadRequest convertRequest = new ConvertLeadRequest(null, null, null, null, null, null);
        MvcResult convertResult = mockMvc
                .perform(authed(post("/api/v1/leads/" + leadId + "/convert"), ownerToken)
                        .content(objectMapper.writeValueAsString(convertRequest)))
                .andExpect(status().isOk())
                .andReturn();
        String convertedAccountId = readField(convertResult, "data", "accountId");
        String convertedContactId = readField(convertResult, "data", "contactId");
        String convertedOpportunityId = readField(convertResult, "data", "opportunityId");
        assertThat(convertedAccountId).isNotBlank();
        assertThat(convertedContactId).isNotBlank();
        assertThat(convertedOpportunityId).isNotBlank();

        mockMvc.perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedAccountId").value(convertedAccountId))
                .andExpect(jsonPath("$.data.convertedContactId").value(convertedContactId))
                .andExpect(jsonPath("$.data.convertedOpportunityId").value(convertedOpportunityId));

        // --- Invite a MEMBER teammate ---
        String teammateEmail = "crm-teammate-%d@example.com".formatted(System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("MEMBER"));

        // Invited users get a random unusable password + emailed set-password link - there's
        // no inbox to read in a test, so set a known password directly via the repository
        // (exactly the persisted-entity update path BaseEntity's version-field convention
        // exists to make safe) and log in normally to get a real JWT for this teammate.
        String teammatePassword = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(teammateEmail.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(teammatePassword));
        userRepository.save(teammate);

        MvcResult teammateLoginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teammateEmail, teammatePassword))))
                .andExpect(status().isOk())
                .andReturn();
        String teammateToken = readField(teammateLoginResult, "data", "accessToken");

        // MEMBER holds ACCOUNT:CREATE:OWN/TEAM - null ownerId defaults to themselves.
        CreateAccountRequest teammateAccount = new CreateAccountRequest(
                "Teammate's Own Account", null, null, null, null, null, null, null, null, null, null, null, null);
        MvcResult teammateAccountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), teammateToken).content(objectMapper.writeValueAsString(teammateAccount)))
                .andExpect(status().isCreated())
                .andReturn();
        String teammateAccountId = readField(teammateAccountResult, "data", "id");

        // The OWNER holds ACCOUNT:READ:ORGANIZATION - sees every account in the org: the
        // explicit Globex account, the "Springfield Power" account auto-created by the lead
        // conversion above, and the teammate's own account.
        mockMvc.perform(authed(get("/api/v1/accounts"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // The MEMBER's highest granted scope for ACCOUNT:READ is TEAM, which - since nothing
        // sets User#teamId yet - degrades to exactly themselves. This is the actual proof that
        // ScopeAuthorizationService#visibleOwnerIds filters real DB rows end to end: the
        // teammate sees only the account they just created, never the OWNER's Globex account.
        mockMvc.perform(authed(get("/api/v1/accounts"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(teammateAccountId));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON);
    }

    private String readField(MvcResult result, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String segment : path) {
            node = node.get(segment);
        }
        return node.asText();
    }
}
