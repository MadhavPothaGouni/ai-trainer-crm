package com.aitrainercrm.platform.dedupe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.activity.dto.CreateActivityRequest;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.dedupe.dto.MergeDuplicateRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the one path a unit test can't reach: the real {@code @Async}
 * {@code DuplicateDetectionListener} firing off a real {@code RecordCreated} event published by
 * {@code LeadService#create}, and a real HTTP merge reassigning a real child {@link Activity} row
 * before soft-deleting the absorbed lead - same "sleep past the async listener, then assert over
 * real HTTP" shape {@code TerritoryRuleIntegrationTest} uses for its own {@code @Async} listener.
 * {@link com.aitrainercrm.platform.dedupe.service.DuplicateMatchServiceTest} already covers the
 * merge/dismiss branch logic (converted-lead guard, forbidden-on-one-side, account FK fan-out,
 * scope filtering) with mocks; this test exists only to prove the wiring between listener,
 * service, and the four real repositories actually holds together end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DuplicateMatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void twoLeadsSameEmail_autoFlagged_mergeReassignsActivityAndSoftDeletesAbsorbed() throws Exception {
        String ownerToken = registerOwner("dedupe-owner");
        String email = "dupe-lead-%d@example.com".formatted(System.nanoTime());

        MvcResult firstResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateLeadRequest("Ada", "Lovelace", email, null, "Analytical Engines", null, Lead.Source.WEBSITE, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID firstLeadId = UUID.fromString(readField(firstResult, "data", "id"));

        // A real child Activity on the first (soon-to-be-absorbed) lead, to prove merge reassigns it rather than orphaning or deleting it.
        mockMvc.perform(authed(post("/api/v1/activities"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateActivityRequest(
                                Activity.Type.CALL, "Intro call", null, null, null, Activity.RelatedToType.LEAD, firstLeadId, null))))
                .andExpect(status().isCreated());

        MvcResult secondResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateLeadRequest("Ada", "Lovelace", email, null, "Analytical Engines", null, Lead.Source.REFERRAL, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondLeadId = UUID.fromString(readField(secondResult, "data", "id"));

        // Poll for the @Async DuplicateDetectionListener's effect rather than guessing at a fixed
        // sleep - see AbstractIntegrationTest#awaitAsync.
        JsonNode matches = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/duplicates").param("entityType", "LEAD"), ownerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data"),
                data -> data.size() >= 1);
        assertThat(matches).hasSize(1);
        JsonNode match = matches.get(0);
        assertThat(match.get("matchReason").asText()).isEqualTo("EMAIL");
        assertThat(match.get("status").asText()).isEqualTo("PENDING");
        UUID matchId = UUID.fromString(match.get("id").asText());
        // firstLeadId was created before secondLeadId, so it's recordA - the normalized, order-independent pair.
        assertThat(match.get("recordAId").asText()).isEqualTo(firstLeadId.toString());
        assertThat(match.get("recordBId").asText()).isEqualTo(secondLeadId.toString());

        MvcResult mergeResult = mockMvc
                .perform(authed(post("/api/v1/duplicates/" + matchId + "/merge"), ownerToken)
                        .content(objectMapper.writeValueAsString(new MergeDuplicateRequest(secondLeadId))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode merged = objectMapper.readTree(mergeResult.getResponse().getContentAsString()).get("data");
        assertThat(merged.get("status").asText()).isEqualTo("MERGED");
        assertThat(merged.get("survivorId").asText()).isEqualTo(secondLeadId.toString());
        assertThat(merged.get("absorbedId").asText()).isEqualTo(firstLeadId.toString());

        // The absorbed lead is soft-deleted - LeadService#get only ever resolves active rows.
        mockMvc.perform(authed(get("/api/v1/leads/" + firstLeadId), ownerToken)).andExpect(status().isNotFound());

        // The activity that was on the absorbed lead now points at the survivor.
        MvcResult activitiesResult = mockMvc
                .perform(authed(get("/api/v1/activities").param("relatedToType", "LEAD").param("relatedToId", secondLeadId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode activities = objectMapper.readTree(activitiesResult.getResponse().getContentAsString()).get("data").get("content");
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).get("subject").asText()).isEqualTo("Intro call");

        // A second merge attempt on the same (now-resolved) match is rejected.
        mockMvc.perform(authed(post("/api/v1/duplicates/" + matchId + "/merge"), ownerToken)
                        .content(objectMapper.writeValueAsString(new MergeDuplicateRequest(secondLeadId))))
                .andExpect(status().isConflict());
    }

    @Test
    void dismiss_leavesBothLeadsUntouched() throws Exception {
        String ownerToken = registerOwner("dedupe-dismiss-owner");
        String email = "dupe-dismiss-%d@example.com".formatted(System.nanoTime());

        mockMvc.perform(authed(post("/api/v1/leads"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateLeadRequest("Grace", "Hopper", email, null, "Navy", null, Lead.Source.WEBSITE, null, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(post("/api/v1/leads"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateLeadRequest("Grace", "Hopper", email, null, "Navy", null, Lead.Source.REFERRAL, null, null))))
                .andExpect(status().isCreated());

        JsonNode dismissMatches = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/duplicates").param("entityType", "LEAD"), ownerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data"),
                data -> data.size() >= 1);
        UUID matchId = UUID.fromString(dismissMatches.get(0).get("id").asText());

        MvcResult dismissResult = mockMvc
                .perform(authed(post("/api/v1/duplicates/" + matchId + "/dismiss"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(dismissResult.getResponse().getContentAsString()).get("data").get("status").asText())
                .isEqualTo("DISMISSED");

        mockMvc.perform(authed(get("/api/v1/duplicates").param("entityType", "LEAD").param("status", "PENDING"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        objectMapper.readTree(result.getResponse().getContentAsString()).get("data")).isEmpty());
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
