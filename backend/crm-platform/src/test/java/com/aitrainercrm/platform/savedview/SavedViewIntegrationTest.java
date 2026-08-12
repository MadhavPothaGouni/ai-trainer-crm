package com.aitrainercrm.platform.savedview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end coverage for the module's two central claims that only a real HTTP round trip (with a
 * real Postgres unique index underneath) can pin down: every endpoint is fully self-scoped with no
 * permission catalog entry at all (a MEMBER with zero explicit grants can still fully manage their
 * own views, and can never see or touch a teammate's - a teammate's view 404s exactly like a
 * nonexistent one, never 403s), and {@code setDefault}'s unset-then-set-with-flush ordering
 * actually prevents two simultaneous defaults from ever being visible, matching {@code
 * DashboardIntegrationTest}'s equivalent coverage for the same partial-unique-index shape.
 * {@code SavedViewServiceTest} covers the same setDefault ordering with mocks, verifying the exact
 * saveAndFlush call sequence that this test can only observe indirectly through the resulting rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SavedViewIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void viewCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("views-crud-owner");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"LEAD\",\"name\":\"Hot Leads\",\"filters\":\"{\\\"status\\\":\\\"HOT\\\"}\","
                                + "\"sortField\":\"score\",\"sortDirection\":\"DESC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Hot Leads"))
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andReturn();
        String viewId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "LEAD"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Hot Leads"));

        // A different entity type must not see this view at all.
        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "CONTACT"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(authed(put("/api/v1/saved-views/" + viewId), ownerToken)
                        .content("{\"name\":\"Hottest Leads\",\"filters\":\"{\\\"status\\\":\\\"HOT\\\"}\","
                                + "\"sortField\":\"score\",\"sortDirection\":\"DESC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hottest Leads"));

        mockMvc.perform(authed(delete("/api/v1/saved-views/" + viewId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "LEAD"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void noPermissionCatalogEntry_anyMemberCanFullyManageTheirOwnViews() throws Exception {
        String ownerToken = registerOwner("views-member-owner");
        String[] rep = inviteAndLogin(ownerToken, "views-member-rep");

        // rep has zero explicit grants (a bare MEMBER) yet every saved-views endpoint works for
        // their own data, since the module never checks the permission catalog at all.
        mockMvc.perform(authed(post("/api/v1/saved-views"), rep[1])
                        .content("{\"entityType\":\"CONTACT\",\"name\":\"My Contacts\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "CONTACT"), rep[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void teammatesView_isInvisibleAndUnreachable_404sNot403s() throws Exception {
        String ownerToken = registerOwner("views-isolation-owner");
        String[] rep = inviteAndLogin(ownerToken, "views-isolation-rep");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"ACCOUNT\",\"name\":\"Owner's View\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerViewId = readField(createResult, "data", "id");

        // The rep never sees the owner's view in their own list...
        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "ACCOUNT"), rep[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // ...and direct attempts to touch it by id 404, exactly like a nonexistent id would -
        // never 403, since a 403 would confirm the row exists and belongs to someone else.
        mockMvc.perform(authed(put("/api/v1/saved-views/" + ownerViewId), rep[1])
                        .content("{\"name\":\"Hijacked\",\"filters\":\"{}\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(delete("/api/v1/saved-views/" + ownerViewId), rep[1])).andExpect(status().isNotFound());
        mockMvc.perform(authed(patch("/api/v1/saved-views/" + ownerViewId + "/default"), rep[1])).andExpect(status().isNotFound());

        // The owner's own view is untouched by all of the rep's failed attempts.
        mockMvc.perform(authed(get("/api/v1/saved-views").param("entityType", "ACCOUNT"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Owner's View"));
    }

    @Test
    void setDefault_secondViewAsDefault_unsetsThePreviousOne() throws Exception {
        String ownerToken = registerOwner("views-default-owner");

        MvcResult firstResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"OPPORTUNITY\",\"name\":\"First\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = readField(firstResult, "data", "id");

        MvcResult secondResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"OPPORTUNITY\",\"name\":\"Second\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = readField(secondResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/saved-views/" + firstId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        mockMvc.perform(authed(patch("/api/v1/saved-views/" + secondId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // Reading the list back confirms exactly one default survives at the database level -
        // the partial unique index never saw two simultaneous true rows to reject.
        MvcResult listResult = mockMvc
                .perform(authed(get("/api/v1/saved-views").param("entityType", "OPPORTUNITY"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data");
        int defaultCount = 0;
        for (JsonNode node : data) {
            if (node.get("isDefault").asBoolean()) defaultCount++;
        }
        org.assertj.core.api.Assertions.assertThat(defaultCount).isEqualTo(1);
        mockMvc.perform(authed(get("/api/v1/saved-views/" + firstId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void setDefault_differentEntityTypes_eachTracksItsOwnDefaultIndependently() throws Exception {
        String ownerToken = registerOwner("views-multitype-owner");

        MvcResult leadViewResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"LEAD\",\"name\":\"Lead View\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String leadViewId = readField(leadViewResult, "data", "id");

        MvcResult ticketViewResult = mockMvc
                .perform(authed(post("/api/v1/saved-views"), ownerToken)
                        .content("{\"entityType\":\"TICKET\",\"name\":\"Ticket View\",\"filters\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketViewId = readField(ticketViewResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/saved-views/" + leadViewId + "/default"), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/saved-views/" + ticketViewId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // Setting the TICKET default must not have touched the unrelated LEAD default.
        mockMvc.perform(authed(get("/api/v1/saved-views/" + leadViewId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    private String registerOwner(String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
        RegisterRequest registerRequest = new RegisterRequest(email, "Str0ng!Passw0rd", "Owner", "Person", "Acme Rockets");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(registerResult, "data", "accessToken");
    }

    /** @return {userId, accessToken} for a freshly invited MEMBER teammate in the caller's org. */
    private String[] inviteAndLogin(String ownerToken, String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(email, "New", "Teammate", null))))
                .andExpect(status().isCreated());

        String password = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(teammate);

        MvcResult loginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return new String[] {readField(loginResult, "data", "userId"), readField(loginResult, "data", "accessToken")};
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON);
    }

    private String readField(MvcResult result, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String segment : path) {
            node = node.get(segment);
            if (node == null) return "";
        }
        return node.asText();
    }
}
