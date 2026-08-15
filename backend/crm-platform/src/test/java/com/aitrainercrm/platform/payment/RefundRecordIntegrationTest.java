package com.aitrainercrm.platform.payment;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
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
 * End-to-end coverage for refunds - see V65's migration comment and {@code RefundRecordService}'s
 * javadoc. Builds a real Order -&gt; Invoice -&gt; Payment first (same short path
 * {@code OrderInvoicePaymentIntegrationTest} exercises in full) since a refund can only be issued
 * against a real {@code Payment}, then covers the one piece of real business logic -
 * {@code assertRefundNotExceedingPayment} rejecting a refund (or an edit of one) that would push
 * the payment's total refunded amount past its own amount - plus the free REQUESTED/APPROVED/
 * PROCESSED status machine with its stamp-once {@code processedAt}, and the same MEMBER-teammate
 * owner-scope split every other core-CRM occurrence resource covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RefundRecordIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void refundRecordLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("refund-record-crud");
        String paymentId = createPaidPayment(ownerToken, "100.00");

        // Against a nonexistent payment is rejected.
        mockMvc.perform(authed(post("/api/v1/refund-records"), ownerToken)
                        .content(
                                """
                                {"paymentId":"00000000-0000-0000-0000-000000000000","amount":10,"reason":"OTHER"}
                                """))
                .andExpect(status().isNotFound());

        // A refund within the payment's amount succeeds.
        MvcResult firstRefund = mockMvc
                .perform(authed(post("/api/v1/refund-records"), ownerToken)
                        .content(
                                """
                                {"paymentId":"%s","amount":40,"reason":"CUSTOMER_REQUEST","notes":"Partial refund"}
                                """
                                        .formatted(paymentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(40))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn();
        String firstRefundId = readField(firstRefund, "data", "id");
        assertThat(firstRefundId).isNotBlank();

        // A second refund that would push the total past the payment's 100.00 is rejected.
        mockMvc.perform(authed(post("/api/v1/refund-records"), ownerToken)
                        .content(
                                """
                                {"paymentId":"%s","amount":70,"reason":"OTHER"}
                                """
                                        .formatted(paymentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_RECORD_EXCEEDS_PAYMENT"));

        // A second refund that fits within the remaining 60.00 succeeds.
        MvcResult secondRefund = mockMvc
                .perform(authed(post("/api/v1/refund-records"), ownerToken)
                        .content(
                                """
                                {"paymentId":"%s","amount":60,"reason":"BILLING_ERROR"}
                                """
                                        .formatted(paymentId)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondRefundId = readField(secondRefund, "data", "id");

        // Editing the first refund up to a value that would exceed the payment (40 -> 45, total would be 105) is rejected.
        mockMvc.perform(authed(put("/api/v1/refund-records/" + firstRefundId), ownerToken)
                        .content("{\"amount\":45,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_RECORD_EXCEEDS_PAYMENT"));

        // Editing it down still fits and succeeds.
        mockMvc.perform(authed(put("/api/v1/refund-records/" + firstRefundId), ownerToken)
                        .content("{\"amount\":30,\"reason\":\"CUSTOMER_REQUEST\",\"notes\":\"Reduced\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(30))
                .andExpect(jsonPath("$.data.notes").value("Reduced"));

        // Free status machine: REQUESTED -> APPROVED -> PROCESSED, processedAt stamped once.
        mockMvc.perform(authed(patch("/api/v1/refund-records/" + firstRefundId + "/status"), ownerToken)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.processedAt").doesNotExist());

        MvcResult processedResult = mockMvc
                .perform(authed(patch("/api/v1/refund-records/" + firstRefundId + "/status"), ownerToken)
                        .content("{\"status\":\"PROCESSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andReturn();
        String firstProcessedAt = readField(processedResult, "data", "processedAt");
        assertThat(firstProcessedAt).isNotBlank();

        // Moving PROCESSED back to REQUESTED is a legitimate correction (non-linear, never blocked) and doesn't touch processedAt.
        MvcResult revertedResult = mockMvc
                .perform(authed(patch("/api/v1/refund-records/" + firstRefundId + "/status"), ownerToken)
                        .content("{\"status\":\"REQUESTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn();
        // Compared via Instant equality (not raw string equality) - the first response returns the
        // in-memory Instant.now() at full nanosecond precision, while later responses are re-fetched
        // from Postgres, whose timestamptz column only holds microsecond precision, so the two ISO
        // strings can legitimately differ in their last digit while still being the same instant.
        assertThat(Instant.parse(readField(revertedResult, "data", "processedAt")))
                .isCloseTo(Instant.parse(firstProcessedAt), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));

        // Moving to PROCESSED again does not overwrite the original processedAt stamp.
        MvcResult reprocessedResult = mockMvc
                .perform(authed(patch("/api/v1/refund-records/" + firstRefundId + "/status"), ownerToken)
                        .content("{\"status\":\"PROCESSED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(Instant.parse(readField(reprocessedResult, "data", "processedAt")))
                .isCloseTo(Instant.parse(firstProcessedAt), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));

        mockMvc.perform(authed(get("/api/v1/refund-records"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(authed(delete("/api/v1/refund-records/" + secondRefundId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/refund-records/" + secondRefundId), ownerToken)).andExpect(status().isNotFound());

        // A deleted refund's amount no longer counts toward the payment total, so a fresh 65.00 refund now fits (30 already active + 65 = 95).
        mockMvc.perform(authed(post("/api/v1/refund-records"), ownerToken)
                        .content(
                                """
                                {"paymentId":"%s","amount":65,"reason":"OTHER"}
                                """
                                        .formatted(paymentId)))
                .andExpect(status().isCreated());

        // --- A MEMBER teammate: REFUND_RECORD is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "refund-record-teammate-%d@example.com".formatted(System.nanoTime());
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

        // Only 5.00 of headroom is left on the payment (30 + 65 = 95 of 100), but that's enough to prove CREATE:OWN works.
        mockMvc.perform(authed(post("/api/v1/refund-records"), teammateToken)
                        .content(
                                """
                                {"paymentId":"%s","amount":5,"reason":"OTHER"}
                                """
                                        .formatted(paymentId)))
                .andExpect(status().isCreated());
    }

    /** Builds a minimal Order -&gt; Invoice -&gt; Payment chain and returns the new Payment's id. */
    private String createPaidPayment(String ownerToken, String amount) throws Exception {
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content("{\"name\":\"Refund Test Account\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String accountId = readField(accountResult, "data", "id");

        MvcResult opportunityResult = mockMvc
                .perform(authed(post("/api/v1/opportunities"), ownerToken)
                        .content("{\"accountId\":\"%s\",\"name\":\"Refund Test Deal\"}".formatted(accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        String opportunityId = readField(opportunityResult, "data", "id");

        MvcResult productResult = mockMvc
                .perform(authed(post("/api/v1/products"), ownerToken)
                        .content(
                                """
                                {"name":"Refund Test Product","sku":"SKU-REFUND-%d","unitPrice":%s,"currency":"USD"}
                                """
                                        .formatted(System.nanoTime(), amount)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = readField(productResult, "data", "id");

        MvcResult quoteResult = mockMvc
                .perform(authed(post("/api/v1/quotes"), ownerToken)
                        .content(
                                """
                                {"opportunityId":"%s","name":"Refund Test Quote","currency":"USD"}
                                """
                                        .formatted(opportunityId)))
                .andExpect(status().isCreated())
                .andReturn();
        String quoteId = readField(quoteResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/quotes/" + quoteId + "/line-items"), ownerToken)
                        .content(
                                """
                                {"productId":"%s","description":"Refund Test Product","quantity":1,"unitPrice":%s}
                                """
                                        .formatted(productId, amount)))
                .andExpect(status().isCreated());

        MvcResult orderResult = mockMvc
                .perform(authed(post("/api/v1/orders/from-quote/" + quoteId), ownerToken)
                        .content("{\"orderNumber\":\"ORD-REFUND-%d\"}".formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = readField(orderResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/confirm"), ownerToken)).andExpect(status().isOk());

        MvcResult invoiceResult = mockMvc
                .perform(authed(post("/api/v1/invoices/from-order/" + orderId), ownerToken)
                        .content("{\"invoiceNumber\":\"INV-REFUND-%d\"}".formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String invoiceId = readField(invoiceResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/invoices/" + invoiceId + "/issue"), ownerToken)).andExpect(status().isOk());

        MvcResult paymentResult = mockMvc
                .perform(authed(post("/api/v1/invoices/" + invoiceId + "/payments"), ownerToken)
                        .content("{\"amount\":\"%s\",\"method\":\"CASH\"}".formatted(amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(paymentResult, "data", "id");
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
