package com.aitrainercrm.platform.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.aitrainercrm.platform.webhook.dto.CreateWebhookSubscriptionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
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
 * End-to-end coverage for webhook dispatch: a real (if tiny) HTTP server
 * plays the role of the subscriber, listening on localhost. Creating an
 * Account through the ordinary CRM API - not calling anything
 * webhook-specific - should, moments later, produce a real signed POST at
 * that server, proving the whole chain works: CrmAuditEvents.RecordCreated
 * gets published by AccountService, WebhookDispatchListener picks it up
 * with no coupling between the two, finds the matching subscription, and
 * delivers a correctly HMAC-signed payload. A second subscription scoped
 * to an event type that never fires proves the per-subscription
 * {@code eventType} filter actually filters.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void creatingAnAccount_deliversASignedWebhook_toAMatchingSubscriptionOnly() throws Exception {
        // The subscription below listens for *every* event, so it also receives
        // its own creation, the second subscription's creation, and the teammate
        // invite - all of which reach this server before the Account_CREATED
        // event we actually care about. Only complete the future on the event
        // this test is asserting about; every other delivery still gets a 200
        // (so it's a legitimate successful delivery, just not the one we're
        // waiting for) but doesn't resolve the future early.
        CompletableFuture<CapturedRequest> captured = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String signature = exchange.getRequestHeaders().getFirst("X-Webhook-Signature");
            String bodyText = new String(body, StandardCharsets.UTF_8);
            if (!captured.isDone()) {
                try {
                    JsonNode node = objectMapper.readTree(bodyText);
                    if ("Account_CREATED".equals(node.path("event").asText())) {
                        captured.complete(new CapturedRequest(bodyText, signature));
                    }
                } catch (Exception ignored) {
                    // Not JSON this test cares about distinguishing - fall through and still ack it.
                }
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String hookUrl = "http://localhost:" + server.getAddress().getPort() + "/hook";

        String ownerEmail = "webhook-owner-%d@example.com".formatted(System.nanoTime());
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Webhooks"))))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- Subscribe to every event ---
        MvcResult subscribeResult = mockMvc
                .perform(authed(post("/api/v1/webhooks"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateWebhookSubscriptionRequest(hookUrl, null))))
                .andExpect(status().isCreated())
                .andReturn();
        String secret = readField(subscribeResult, "data", "secret");

        // --- A second subscription for an event type that will never fire in this test ---
        mockMvc.perform(authed(post("/api/v1/webhooks"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateWebhookSubscriptionRequest(hookUrl, "Contact_CREATED"))))
                .andExpect(status().isCreated());

        // --- INTEGRATION isn't a core CRM resource - a default MEMBER can't create a subscription ---
        String teammateEmail = "webhook-teammate-%d@example.com".formatted(System.nanoTime());
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
        mockMvc.perform(authed(post("/api/v1/webhooks"), teammateToken)
                        .content(objectMapper.writeValueAsString(new CreateWebhookSubscriptionRequest(hookUrl, null))))
                .andExpect(status().isForbidden());

        // --- Trigger a real domain event through the ordinary CRM API ---
        mockMvc.perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated());

        // --- The listener runs @Async - wait for the real HTTP delivery rather than assuming timing ---
        CapturedRequest delivery = captured.get(5, TimeUnit.SECONDS);
        JsonNode payload = objectMapper.readTree(delivery.body());
        assertThat(payload.get("event").asText()).isEqualTo("Account_CREATED");
        assertThat(payload.get("resourceType").asText()).isEqualTo("Account");
        assertThat(delivery.signatureHeader()).isEqualTo("sha256=" + hmacSha256Hex(secret, delivery.body()));

        // --- The second subscription (Contact_CREATED) never matched an Account event - confirm it never fired ---
        Thread.sleep(200); // give the async dispatch a moment to have touched every matching subscription
        MvcResult listResult = mockMvc.perform(authed(get("/api/v1/webhooks"), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode subscriptions = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data").get("content");
        boolean anyContactSubscriptionFired = false;
        for (JsonNode subscription : subscriptions) {
            if ("Contact_CREATED".equals(textOrNull(subscription.get("eventType")))) {
                anyContactSubscriptionFired = !subscription.get("lastTriggeredAt").isNull();
            }
        }
        assertThat(anyContactSubscriptionFired).isFalse();
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String hmacSha256Hex(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private record CapturedRequest(String body, String signatureHeader) {}

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
