package com.aitrainercrm.platform.notification;

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
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end coverage for the Notification module. Unlike every integration test since Ticket
 * (V14), there is deliberately no scope-ladder assertion here (no "OWNER sees everything, MEMBER
 * sees only their own") - see NotificationService's javadoc for why that concept doesn't apply.
 * What this suite proves instead is the thing that actually matters for a self-scoped resource:
 * one teammate can never read, mark-read, or delete a notification addressed to someone else, in
 * the same org or a different one, full stop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void sendListMarkReadDeleteAndUnreadCount_endToEnd() throws Exception {
        String ownerToken = registerOwner("notif-owner");
        String[] teammate = inviteAndLogin(ownerToken, "notif-teammate");
        String teammateId = teammate[0];
        String teammateToken = teammate[1];

        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammateId + "\",\"type\":\"GENERAL\",\"title\":\"Welcome aboard\",\"body\":\"Glad to have you.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Welcome aboard"))
                .andExpect(jsonPath("$.data.readAt").doesNotExist())
                .andReturn();
        String notificationId = readField(createResult, "data", "id");

        // The sender never sees it in their own list - it belongs to the recipient's inbox, not the sender's.
        mockMvc.perform(authed(get("/api/v1/notifications"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(authed(get("/api/v1/notifications"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(notificationId));

        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(authed(patch("/api/v1/notifications/" + notificationId + "/read"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(authed(get("/api/v1/notifications").param("unreadOnly", "true"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(authed(delete("/api/v1/notifications/" + notificationId), teammateToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/notifications"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void markAllRead_marksEveryUnreadNotificationForThatRecipientOnly() throws Exception {
        String ownerToken = registerOwner("notif-markall-owner");
        String[] teammateA = inviteAndLogin(ownerToken, "notif-markall-a");
        String[] teammateB = inviteAndLogin(ownerToken, "notif-markall-b");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                            .content("{\"recipientUserId\":\"" + teammateA[0] + "\",\"type\":\"GENERAL\",\"title\":\"Update " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammateB[0] + "\",\"type\":\"GENERAL\",\"title\":\"For B only\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(patch("/api/v1/notifications/read-all"), teammateA[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("3 notification(s) marked read"));

        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateA[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // Marking A's notifications read never touched B's.
        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateB[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void aTeammateCannotReadMarkReadOrDeleteAnotherTeammatesNotification() throws Exception {
        String ownerToken = registerOwner("notif-isolation-owner");
        String[] teammateA = inviteAndLogin(ownerToken, "notif-isolation-a");
        String[] teammateB = inviteAndLogin(ownerToken, "notif-isolation-b");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammateA[0] + "\",\"type\":\"MENTION\",\"title\":\"Private to A\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String notificationId = readField(createResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/notifications/" + notificationId + "/read"), teammateB[1])).andExpect(status().isNotFound());
        mockMvc.perform(authed(delete("/api/v1/notifications/" + notificationId), teammateB[1])).andExpect(status().isNotFound());
        mockMvc.perform(authed(get("/api/v1/notifications"), teammateB[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // B's mark-all-read is a no-op against A's inbox - the update is scoped by recipientUserId, not just organizationId.
        mockMvc.perform(authed(patch("/api/v1/notifications/read-all"), teammateB[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("0 notification(s) marked read"));
        mockMvc.perform(authed(get("/api/v1/notifications/unread-count"), teammateA[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void sendToUserInAnotherOrganization_returns404() throws Exception {
        String ownerToken = registerOwner("notif-crossorg-owner");
        String otherOrgOwnerToken = registerOwner("notif-crossorg-other");
        String otherOrgUserId = readField(
                mockMvc.perform(authed(get("/api/v1/users/me"), otherOrgOwnerToken)).andExpect(status().isOk()).andReturn(), "data", "id");

        mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + otherOrgUserId + "\",\"type\":\"GENERAL\",\"title\":\"Nope\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendWithUnknownRelatedRecord_returns404() throws Exception {
        String ownerToken = registerOwner("notif-badrelated-owner");
        String[] teammate = inviteAndLogin(ownerToken, "notif-badrelated");

        mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammate[0] + "\",\"type\":\"ASSIGNMENT\",\"title\":\"Ghost ticket\","
                                + "\"relatedToType\":\"TICKET\",\"relatedToId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendWithRelatedTypeButNoRelatedId_returns400() throws Exception {
        String ownerToken = registerOwner("notif-partialrelated-owner");
        String[] teammate = inviteAndLogin(ownerToken, "notif-partialrelated");

        mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammate[0] + "\",\"type\":\"GENERAL\",\"title\":\"Bad\",\"relatedToType\":\"ACCOUNT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendWithRealRelatedAccount_succeeds() throws Exception {
        String ownerToken = registerOwner("notif-goodrelated-owner");
        String[] teammate = inviteAndLogin(ownerToken, "notif-goodrelated");

        CreateAccountRequest createAccount = new CreateAccountRequest(
                "Initech Corp", null, null, null, null, null, null, null, null, null, null, null, null);
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                .andExpect(status().isCreated())
                .andReturn();
        String accountId = readField(accountResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/notifications"), ownerToken)
                        .content("{\"recipientUserId\":\"" + teammate[0] + "\",\"type\":\"GENERAL\",\"title\":\"Check this account\","
                                + "\"relatedToType\":\"ACCOUNT\",\"relatedToId\":\"" + accountId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.relatedToType").value("ACCOUNT"))
                .andExpect(jsonPath("$.data.relatedToId").value(accountId));
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
