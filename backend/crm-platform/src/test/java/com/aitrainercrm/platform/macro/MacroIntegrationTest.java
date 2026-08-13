package com.aitrainercrm.platform.macro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for Macro/apply - see V34's migration comment for the module overview.
 * {@code MacroServiceTest} covers the description-append/truncate math and the deferred-to-
 * TicketService authorization design with mocks; this pins down what only real HTTP + real
 * Postgres + the real (not mocked) TicketService can: the actual MACRO permission grants an
 * OWNER role has, and that applying a macro really mutates a real, independently-fetchable
 * Ticket row through TicketService's real update/updateStatus path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MacroIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void macro_endToEnd_applyingAppendsDescriptionAndTransitionsStatus() throws Exception {
        String ownerToken = registerOwner("macro-flow-owner");

        MvcResult macroResult = mockMvc
                .perform(authed(post("/api/v1/macros"), ownerToken)
                        .content("{\"name\":\"Closing note\",\"body\":\"Thanks for reaching out - closing this out now.\",\"newStatus\":\"RESOLVED\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String macroId = readField(macroResult, "data", "id");

        MvcResult ticketResult = mockMvc
                .perform(authed(post("/api/v1/tickets"), ownerToken)
                        .content("{\"subject\":\"Can't log in\",\"description\":\"User reports a login failure.\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketId = readField(ticketResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/macros/" + macroId + "/apply"), ownerToken).content("{\"ticketId\":\"" + ticketId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.description").value(
                        "User reports a login failure.\n\nThanks for reaching out - closing this out now."));
    }

    @Test
    void macro_applyingAnInactiveMacro_isRejected() throws Exception {
        String ownerToken = registerOwner("macro-inactive-owner");

        MvcResult macroResult = mockMvc
                .perform(authed(post("/api/v1/macros"), ownerToken).content("{\"name\":\"Old macro\",\"body\":\"Deprecated wording.\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String macroId = readField(macroResult, "data", "id");

        // Deactivate it via the real PUT (the same endpoint the "edit macro" form uses).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/macros/" + macroId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Old macro\",\"body\":\"Deprecated wording.\",\"active\":false}"))
                .andExpect(status().isOk());

        MvcResult ticketResult = mockMvc
                .perform(authed(post("/api/v1/tickets"), ownerToken).content("{\"subject\":\"Billing question\",\"priority\":\"LOW\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String ticketId = readField(ticketResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/macros/" + macroId + "/apply"), ownerToken).content("{\"ticketId\":\"" + ticketId + "\"}"))
                .andExpect(status().isConflict());
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
