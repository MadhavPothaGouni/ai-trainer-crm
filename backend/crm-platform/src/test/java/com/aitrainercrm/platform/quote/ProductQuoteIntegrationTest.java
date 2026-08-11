package com.aitrainercrm.platform.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.product.dto.CreateProductRequest;
import com.aitrainercrm.platform.quote.dto.CreateQuoteLineItemRequest;
import com.aitrainercrm.platform.quote.dto.CreateQuoteRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * End-to-end coverage for Products and Quotes, following the same real-database
 * pattern as CrmDomainIntegrationTest/ActivityIntegrationTest: a product catalog
 * entry, a quote against a real opportunity, line items (one from the catalog,
 * one custom), and proof that totals recompute correctly through add/edit/remove -
 * that's the one piece of real business logic in this module a unit test in
 * isolation wouldn't catch as convincingly as a full request-response round trip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProductQuoteIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void productCatalogAndQuoteLineItems_totalsRecomputeCorrectly_endToEnd() throws Exception {
        String ownerEmail = "quote-owner-%d@example.com".formatted(System.nanoTime());
        RegisterRequest registerRequest =
                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Rockets");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- Account + Opportunity to quote against ---
        CreateAccountRequest createAccount = new CreateAccountRequest(
                "Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null);
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));

        CreateOpportunityRequest createOpportunity =
                new CreateOpportunityRequest(accountId, null, "Acme Rocket Order", null, null, null, null, null);
        MvcResult opportunityResult = mockMvc
                .perform(authed(post("/api/v1/opportunities"), ownerToken).content(objectMapper.writeValueAsString(createOpportunity)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID opportunityId = UUID.fromString(readField(opportunityResult, "data", "id"));

        // --- Product ---
        CreateProductRequest createProduct = new CreateProductRequest("Rocket Skates", "SKU-100", "Pair of rocket skates", new BigDecimal("49.99"), "USD");
        MvcResult productResult = mockMvc
                .perform(authed(post("/api/v1/products"), ownerToken).content(objectMapper.writeValueAsString(createProduct)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        UUID productId = UUID.fromString(readField(productResult, "data", "id"));

        // --- Quote, starting empty ---
        CreateQuoteRequest createQuote = new CreateQuoteRequest(opportunityId, "Q3 Rocket Order", "USD", null, new BigDecimal("10.00"), new BigDecimal("5.00"), null);
        MvcResult quoteResult = mockMvc
                .perform(authed(post("/api/v1/quotes"), ownerToken).content(objectMapper.writeValueAsString(createQuote)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        UUID quoteId = UUID.fromString(readField(quoteResult, "data", "id"));
        assertThat(readDecimal(quoteResult, "data", "subtotal")).isEqualByComparingTo("0");
        // 0 - 10 discount + 5 tax = -5, proving totals recompute from discount/tax even with no line items yet
        assertThat(readDecimal(quoteResult, "data", "totalAmount")).isEqualByComparingTo("-5");

        // --- Line item from the catalog: 2 x 49.99 = 99.98 ---
        CreateQuoteLineItemRequest catalogLine = new CreateQuoteLineItemRequest(productId, "Rocket Skates", 2, new BigDecimal("49.99"));
        MvcResult catalogLineResult = mockMvc
                .perform(authed(post("/api/v1/quotes/" + quoteId + "/line-items"), ownerToken).content(objectMapper.writeValueAsString(catalogLine)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID catalogLineId = UUID.fromString(readField(catalogLineResult, "data", "id"));
        assertThat(readDecimal(catalogLineResult, "data", "lineTotal")).isEqualByComparingTo("99.98");

        // --- Custom line item, no product: 1 x 25.00 = 25.00 ---
        CreateQuoteLineItemRequest customLine = new CreateQuoteLineItemRequest(null, "Custom engraving", 1, new BigDecimal("25.00"));
        MvcResult customLineResult = mockMvc
                .perform(authed(post("/api/v1/quotes/" + quoteId + "/line-items"), ownerToken).content(objectMapper.writeValueAsString(customLine)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(readDecimal(customLineResult, "data", "lineTotal")).isEqualByComparingTo("25.00");

        // subtotal = 99.98 + 25.00 = 124.98; total = 124.98 - 10 + 5 = 119.98
        MvcResult afterTwoLinesResult = mockMvc
                .perform(authed(get("/api/v1/quotes/" + quoteId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineItems.length()").value(2))
                .andReturn();
        assertThat(readDecimal(afterTwoLinesResult, "data", "subtotal")).isEqualByComparingTo("124.98");
        assertThat(readDecimal(afterTwoLinesResult, "data", "totalAmount")).isEqualByComparingTo("119.98");

        // --- Bump the catalog line's quantity to 3: 3 x 49.99 = 149.97 ---
        CreateQuoteLineItemRequest updatedCatalogLine = new CreateQuoteLineItemRequest(productId, "Rocket Skates", 3, new BigDecimal("49.99"));
        MvcResult updatedLineResult = mockMvc
                .perform(put("/api/v1/quotes/" + quoteId + "/line-items/" + catalogLineId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedCatalogLine)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readDecimal(updatedLineResult, "data", "lineTotal")).isEqualByComparingTo("149.97");

        // subtotal = 149.97 + 25.00 = 174.97; total = 174.97 - 10 + 5 = 169.97
        MvcResult afterBumpResult = mockMvc
                .perform(authed(get("/api/v1/quotes/" + quoteId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readDecimal(afterBumpResult, "data", "subtotal")).isEqualByComparingTo("174.97");
        assertThat(readDecimal(afterBumpResult, "data", "totalAmount")).isEqualByComparingTo("169.97");

        // --- Remove the catalog line: subtotal drops back to just the custom 25.00 line ---
        mockMvc.perform(delete("/api/v1/quotes/" + quoteId + "/line-items/" + catalogLineId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        MvcResult afterRemoveResult = mockMvc
                .perform(authed(get("/api/v1/quotes/" + quoteId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineItems.length()").value(1))
                .andReturn();
        assertThat(readDecimal(afterRemoveResult, "data", "subtotal")).isEqualByComparingTo("25.00");

        // --- Status transition ---
        mockMvc.perform(authed(patch("/api/v1/quotes/" + quoteId + "/status"), ownerToken).content("{\"status\":\"SENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // --- A MEMBER teammate: PRODUCT isn't a core CRM resource (see RoleService#isCoreCrmResource),
        // so the default MEMBER role holds no PRODUCT:CREATE authority at all - proves the "shared
        // catalog, admin-managed" design intent from V5's migration comment actually holds. ---
        String teammateEmail = "quote-teammate-%d@example.com".formatted(System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated());
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

        mockMvc.perform(authed(post("/api/v1/products"), teammateToken)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest("Unauthorized Widget", null, null, BigDecimal.TEN, null))))
                .andExpect(status().isForbidden());

        // MEMBER does hold QUOTE:CREATE:OWN/TEAM though (QUOTE is a core CRM resource) - null
        // ownerId defaults to themselves, and OWN/TEAM-scope list filtering applies exactly like
        // it already does for accounts/activities.
        CreateQuoteRequest teammateQuote = new CreateQuoteRequest(opportunityId, "Teammate's own quote", null, null, null, null, null);
        MvcResult teammateQuoteResult = mockMvc
                .perform(authed(post("/api/v1/quotes"), teammateToken).content(objectMapper.writeValueAsString(teammateQuote)))
                .andExpect(status().isCreated())
                .andReturn();
        String teammateQuoteId = readField(teammateQuoteResult, "data", "id");

        // OWNER holds QUOTE:READ:ORGANIZATION - sees both quotes for this opportunity.
        mockMvc.perform(authed(get("/api/v1/quotes").param("opportunityId", opportunityId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Teammate's highest granted scope is TEAM, degrading to themselves (no team assignment
        // exists yet) - sees only their own quote.
        mockMvc.perform(authed(get("/api/v1/quotes"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(teammateQuoteId));

        // --- Delete the original quote ---
        mockMvc.perform(authed(delete("/api/v1/quotes/" + quoteId), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/quotes/" + quoteId), ownerToken))
                .andExpect(status().isNotFound());
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

    /**
     * BigDecimal fields round-trip through JSON as numbers that may or may not have a decimal
     * point depending on scale (BigDecimal.ZERO serializes as "0", not "0.00") - jsonPath's
     * .value(double) comparison is brittle against that (Integer vs Double type mismatches), so
     * numeric assertions in this test go through here instead: read the raw text and compare via
     * BigDecimal#compareTo, which is scale-independent.
     */
    private BigDecimal readDecimal(MvcResult result, String... path) throws Exception {
        return new BigDecimal(readField(result, path));
    }
}
