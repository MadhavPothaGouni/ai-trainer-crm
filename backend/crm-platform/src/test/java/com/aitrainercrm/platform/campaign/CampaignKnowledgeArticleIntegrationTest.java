package com.aitrainercrm.platform.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
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
 * End-to-end coverage for Campaigns (with Campaign Members linked to a Lead
 * or a Contact, and the per-status stats rollup) and Knowledge Articles
 * (slug generation/collision handling, the DRAFT -&gt; PUBLISHED -&gt; ARCHIVED
 * lifecycle, tags, and the view-count-on-read behavior) - the real business
 * logic in this module a unit test in isolation wouldn't catch as
 * convincingly as a full request-response round trip. Also exercises the
 * CSV export endpoints, the first real implementation of the :EXPORT
 * permission anywhere in this codebase (see CampaignController#export's
 * javadoc).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CampaignKnowledgeArticleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void campaignMembers_trackEngagementAndStats_endToEnd() throws Exception {
        String ownerToken = registerOwner("campaign-owner");

        CreateLeadRequest createLead = new CreateLeadRequest("Ada", "Lovelace", null, null, "Analytical Engines Inc", null, Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID leadId = UUID.fromString(readField(leadResult, "data", "id"));

        CreateContactRequest createContact = new CreateContactRequest("Grace", "Hopper", null, null, null, null, null, null);
        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content(objectMapper.writeValueAsString(createContact)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID contactId = UUID.fromString(readField(contactResult, "data", "id"));

        String createCampaignBody = """
                {"name":"Q3 Rocket Launch Webinar","type":"WEBINAR","budget":"5000.00"}""";
        MvcResult campaignResult = mockMvc
                .perform(authed(post("/api/v1/campaigns"), ownerToken).content(createCampaignBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andReturn();
        UUID campaignId = UUID.fromString(readField(campaignResult, "data", "id"));

        // --- A member request naming both a lead and a contact (or neither) is rejected ---
        mockMvc.perform(authed(post("/api/v1/campaigns/" + campaignId + "/members"), ownerToken)
                        .content("{\"leadId\":\"" + leadId + "\",\"contactId\":\"" + contactId + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/v1/campaigns/" + campaignId + "/members"), ownerToken).content("{}"))
                .andExpect(status().isBadRequest());

        // --- Add the lead, then the contact ---
        MvcResult leadMemberResult = mockMvc
                .perform(authed(post("/api/v1/campaigns/" + campaignId + "/members"), ownerToken).content("{\"leadId\":\"" + leadId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ADDED"))
                .andReturn();
        UUID leadMemberId = UUID.fromString(readField(leadMemberResult, "data", "id"));

        mockMvc.perform(authed(post("/api/v1/campaigns/" + campaignId + "/members"), ownerToken).content("{\"contactId\":\"" + contactId + "\"}"))
                .andExpect(status().isCreated());

        // --- Adding the same lead again is rejected as a duplicate ---
        mockMvc.perform(authed(post("/api/v1/campaigns/" + campaignId + "/members"), ownerToken).content("{\"leadId\":\"" + leadId + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(get("/api/v1/campaigns/" + campaignId + "/members"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // --- Move the lead member to RESPONDED - stamps respondedAt ---
        mockMvc.perform(authed(patch("/api/v1/campaigns/" + campaignId + "/members/" + leadMemberId + "/status"), ownerToken)
                        .content("{\"status\":\"RESPONDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESPONDED"))
                .andExpect(jsonPath("$.data.respondedAt").exists());

        // --- Stats: 1 RESPONDED, 1 ADDED, everything else zero, total 2 ---
        mockMvc.perform(authed(get("/api/v1/campaigns/" + campaignId + "/stats"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.countsByStatus.RESPONDED").value(1))
                .andExpect(jsonPath("$.data.countsByStatus.ADDED").value(1))
                .andExpect(jsonPath("$.data.countsByStatus.CONVERTED").value(0));

        // --- Status lifecycle: PLANNED -> ACTIVE -> COMPLETED; ACTIVE -> PLANNED is rejected ---
        mockMvc.perform(authed(patch("/api/v1/campaigns/" + campaignId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(authed(patch("/api/v1/campaigns/" + campaignId + "/status"), ownerToken).content("{\"status\":\"PLANNED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(authed(patch("/api/v1/campaigns/" + campaignId + "/status"), ownerToken).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // --- CSV export: CAMPAIGN:EXPORT, distinct from CAMPAIGN:READ - OWNER holds both, so this just proves the endpoint actually works ---
        MvcResult exportResult = mockMvc
                .perform(authed(get("/api/v1/campaigns/export"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "text/csv"))
                .andReturn();
        String csv = exportResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Q3 Rocket Launch Webinar").contains("Name,Type,Status");

        // --- Remove the lead member ---
        mockMvc.perform(authed(delete("/api/v1/campaigns/" + campaignId + "/members/" + leadMemberId), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/campaigns/" + campaignId + "/members"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void knowledgeArticles_slugAndPublishLifecycleAndViewCount_endToEnd() throws Exception {
        String ownerToken = registerOwner("kb-owner");

        String createArticleBody = """
                {"title":"Getting Started with Rockets!","content":"Step one: don't panic.","category":"onboarding","tags":["rockets","onboarding"]}""";
        MvcResult firstResult = mockMvc
                .perform(authed(post("/api/v1/knowledge-articles"), ownerToken).content(createArticleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.slug").value("getting-started-with-rockets"))
                .andExpect(jsonPath("$.data.viewCount").value(0))
                .andReturn();
        UUID firstArticleId = UUID.fromString(readField(firstResult, "data", "id"));

        // --- A second article with a title that slugifies the same gets a "-2" suffix instead of colliding ---
        String secondArticleBody = """
                {"title":"Getting Started With Rockets???","content":"A different take.","tags":[]}""";
        mockMvc.perform(authed(post("/api/v1/knowledge-articles"), ownerToken).content(secondArticleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("getting-started-with-rockets-2"));

        // --- Publishing a DRAFT works and stamps publishedAt; publishing again is rejected ---
        mockMvc.perform(authed(post("/api/v1/knowledge-articles/" + firstArticleId + "/publish"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());
        mockMvc.perform(authed(post("/api/v1/knowledge-articles/" + firstArticleId + "/publish"), ownerToken))
                .andExpect(status().isConflict());

        // --- Every GET bumps the view count ---
        mockMvc.perform(authed(get("/api/v1/knowledge-articles/" + firstArticleId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(1));
        mockMvc.perform(authed(get("/api/v1/knowledge-articles/" + firstArticleId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(2));

        // --- List rows use the summary shape - no content body ---
        mockMvc.perform(authed(get("/api/v1/knowledge-articles").param("category", "onboarding"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist());

        // --- Archive is terminal; archiving twice is rejected ---
        mockMvc.perform(authed(post("/api/v1/knowledge-articles/" + firstArticleId + "/archive"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(authed(post("/api/v1/knowledge-articles/" + firstArticleId + "/archive"), ownerToken))
                .andExpect(status().isConflict());

        // --- CSV export ---
        MvcResult exportResult = mockMvc
                .perform(authed(get("/api/v1/knowledge-articles/export"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String csv = exportResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Getting Started with Rockets!").contains("Title,Slug,Category");
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
