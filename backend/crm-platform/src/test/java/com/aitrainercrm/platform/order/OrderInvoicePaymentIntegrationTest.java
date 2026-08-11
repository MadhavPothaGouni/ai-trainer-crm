package com.aitrainercrm.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.order.dto.CreateOrderFromQuoteRequest;
import com.aitrainercrm.platform.order.dto.CreateOrderRequest;
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
 * End-to-end coverage for the order-to-cash flow this module adds on top of
 * ProductQuoteIntegrationTest's Quote: Account -&gt; Opportunity -&gt; Quote
 * (with a line item) -&gt; convert to Order -&gt; confirm -&gt; generate Invoice
 * -&gt; issue -&gt; record two partial Payments that together flip the invoice to
 * PAID, plus the illegal-transition and DRAFT-payment-rejection edge cases
 * that are the actual business logic worth a real request-response round
 * trip rather than a unit test in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderInvoicePaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void quoteToOrderToInvoiceToPayment_flowsEndToEnd_withCorrectStatusTransitions() throws Exception {
        String ownerEmail = "order-owner-%d@example.com".formatted(System.nanoTime());
        RegisterRequest registerRequest =
                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Rockets");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- Account + Opportunity + Product + Quote (one line item) ---
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

        CreateProductRequest createProduct =
                new CreateProductRequest("Rocket Skates", "SKU-100", "Pair of rocket skates", new BigDecimal("49.99"), "USD");
        MvcResult productResult = mockMvc
                .perform(authed(post("/api/v1/products"), ownerToken).content(objectMapper.writeValueAsString(createProduct)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID productId = UUID.fromString(readField(productResult, "data", "id"));

        CreateQuoteRequest createQuote =
                new CreateQuoteRequest(opportunityId, "Q3 Rocket Order", "USD", null, new BigDecimal("10.00"), new BigDecimal("5.00"), null);
        MvcResult quoteResult = mockMvc
                .perform(authed(post("/api/v1/quotes"), ownerToken).content(objectMapper.writeValueAsString(createQuote)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID quoteId = UUID.fromString(readField(quoteResult, "data", "id"));

        // 2 x 49.99 = 99.98; subtotal 99.98 - 10 discount + 5 tax = 94.98
        CreateQuoteLineItemRequest quoteLine = new CreateQuoteLineItemRequest(productId, "Rocket Skates", 2, new BigDecimal("49.99"));
        mockMvc.perform(authed(post("/api/v1/quotes/" + quoteId + "/line-items"), ownerToken).content(objectMapper.writeValueAsString(quoteLine)))
                .andExpect(status().isCreated());

        // --- Convert the quote to a DRAFT order: line item + discount/tax cloned verbatim ---
        CreateOrderFromQuoteRequest fromQuote = new CreateOrderFromQuoteRequest("ORD-1001");
        MvcResult orderResult = mockMvc
                .perform(authed(post("/api/v1/orders/from-quote/" + quoteId), ownerToken).content(objectMapper.writeValueAsString(fromQuote)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.quoteId").value(quoteId.toString()))
                .andExpect(jsonPath("$.data.lineItems.length()").value(1))
                .andReturn();
        UUID orderId = UUID.fromString(readField(orderResult, "data", "id"));
        assertThat(readDecimal(orderResult, "data", "subtotal")).isEqualByComparingTo("99.98");
        assertThat(readDecimal(orderResult, "data", "totalAmount")).isEqualByComparingTo("94.98");

        // --- Trying to fulfil a DRAFT order (skipping CONFIRMED) is rejected ---
        mockMvc.perform(patchStatus(orderId, "FULFILLED", ownerToken))
                .andExpect(status().isConflict());

        // --- Confirm: DRAFT -> CONFIRMED via the APPROVE-gated endpoint ---
        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/confirm"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // --- Confirming an already-CONFIRMED order is rejected (409, not a silent no-op) ---
        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/confirm"), ownerToken))
                .andExpect(status().isConflict());

        // --- Generate an invoice from the confirmed order: line item + totals cloned again ---
        MvcResult invoiceResult = mockMvc
                .perform(authed(post("/api/v1/invoices/from-order/" + orderId), ownerToken)
                        .content("{\"invoiceNumber\":\"INV-2001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.lineItems.length()").value(1))
                .andReturn();
        UUID invoiceId = UUID.fromString(readField(invoiceResult, "data", "id"));
        assertThat(readDecimal(invoiceResult, "data", "totalAmount")).isEqualByComparingTo("94.98");
        assertThat(readDecimal(invoiceResult, "data", "balanceDue")).isEqualByComparingTo("94.98");

        // --- Recording a payment against a DRAFT invoice (not yet issued) is rejected ---
        mockMvc.perform(authed(post("/api/v1/invoices/" + invoiceId + "/payments"), ownerToken)
                        .content("{\"amount\":\"50.00\",\"method\":\"CASH\"}"))
                .andExpect(status().isConflict());

        // --- Issue: DRAFT -> SENT via the APPROVE-gated endpoint ---
        mockMvc.perform(authed(post("/api/v1/invoices/" + invoiceId + "/issue"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // --- First partial payment: 50.00 of 94.98 - invoice stays SENT, balanceDue drops ---
        MvcResult firstPaymentResult = mockMvc
                .perform(authed(post("/api/v1/invoices/" + invoiceId + "/payments"), ownerToken)
                        .content("{\"amount\":\"50.00\",\"method\":\"CASH\",\"reference\":\"cash-drawer-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(readDecimal(firstPaymentResult, "data", "amount")).isEqualByComparingTo("50.00");

        MvcResult afterFirstPaymentResult = mockMvc
                .perform(authed(get("/api/v1/invoices/" + invoiceId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andReturn();
        assertThat(readDecimal(afterFirstPaymentResult, "data", "amountPaid")).isEqualByComparingTo("50.00");
        assertThat(readDecimal(afterFirstPaymentResult, "data", "balanceDue")).isEqualByComparingTo("44.98");

        // --- Second payment covers the rest: 44.98 - invoice flips to PAID automatically ---
        MvcResult secondPaymentResult = mockMvc
                .perform(authed(post("/api/v1/invoices/" + invoiceId + "/payments"), ownerToken)
                        .content("{\"amount\":\"44.98\",\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondPaymentId = UUID.fromString(readField(secondPaymentResult, "data", "id"));

        MvcResult afterFullyPaidResult = mockMvc
                .perform(authed(get("/api/v1/invoices/" + invoiceId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andReturn();
        assertThat(readDecimal(afterFullyPaidResult, "data", "amountPaid")).isEqualByComparingTo("94.98");
        assertThat(readDecimal(afterFullyPaidResult, "data", "balanceDue")).isEqualByComparingTo("0");

        // --- A PAID invoice can't be voided ---
        mockMvc.perform(authed(post("/api/v1/invoices/" + invoiceId + "/void"), ownerToken))
                .andExpect(status().isConflict());

        // --- Removing the second payment drops the invoice back out of PAID ---
        mockMvc.perform(authed(delete("/api/v1/payments/" + secondPaymentId), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/invoices/" + invoiceId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // --- Fulfilling the order now that its invoice is underway: CONFIRMED -> FULFILLED ---
        mockMvc.perform(patchStatus(orderId, "FULFILLED", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FULFILLED"));

        // --- A FULFILLED order can no longer be cancelled (CANCELLED only reachable from DRAFT/CONFIRMED) ---
        mockMvc.perform(patchStatus(orderId, "CANCELLED", ownerToken))
                .andExpect(status().isConflict());

        // --- A MEMBER teammate: ORDER isn't a core CRM resource (see RoleService#isCoreCrmResource,
        // same as PRODUCT/INVOICE/PAYMENT), so the default MEMBER role holds no ORDER:CREATE
        // authority at all - proves V2's "shared finance record, not individually-owned" design
        // intent actually holds for Order the same way ProductQuoteIntegrationTest already proved
        // it for Product. ---
        String teammateEmail = "order-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/orders"), teammateToken)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest("ORD-9999", null, null, null))))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder patchStatus(UUID orderId, String status, String accessToken) {
        return authed(patch("/api/v1/orders/" + orderId + "/status"), accessToken).content("{\"status\":\"" + status + "\"}");
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

    private BigDecimal readDecimal(MvcResult result, String... path) throws Exception {
        return new BigDecimal(readField(result, path));
    }
}
