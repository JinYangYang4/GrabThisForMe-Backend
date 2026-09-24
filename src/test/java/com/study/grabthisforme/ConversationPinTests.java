package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.ConversationService;
import com.study.grabthisforme.persistence.entity.ConversationMemberId;
import com.study.grabthisforme.persistence.repository.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:conversation-pin;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ConversationPinTests {
    @Autowired AuthService auth;
    @Autowired ConversationService chats;
    @Autowired ConversationUserStateRepository states;
    @Autowired ConversationRepository conversations;
    @Autowired ConversationParticipantRepository members;
    @Autowired MockMvc mvc;
    long user() { return auth.register("pin_" + UUID.randomUUID(), "Testpass123", "Tester", null, null).user().id(); }

    @Test void pinIsPerAccountIdempotentAndSurvivesReadHideAndMessages() {
        long a = user(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        var time = chats.setPinned(a, id, true).pinnedAt();
        assertThat(time).isPositive();
        assertThat(chats.setPinned(a, id, true).pinnedAt()).isEqualTo(time);
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().pinnedAt).isNull();
        chats.setHidden(a, id, true);
        chats.markRead(a, id, 123L);
        chats.sendMessage(b, id, "pin-message", "TEXT", "new message", null);
        assertThat(chats.listConversations(a).getFirst().pinnedAt()).isEqualTo(time);
        chats.setPinned(b, id, true);
        assertThat(chats.setPinned(a, id, false).pinnedAt()).isNull();
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().pinnedAt).isNotNull();
    }

    @Test void messageTimeRanksBeforePinTimeButPinnedGroupIsAlwaysFirst() {
        long a = user();
        String older = chats.createSingleConversation(a, user()).conversationId();
        String newer = chats.createSingleConversation(a, user()).conversationId();
        String normal = chats.createSingleConversation(a, user()).conversationId();
        setTimes(a, older, 100L, 900L);
        setTimes(a, newer, 200L, 800L);
        setTimes(a, normal, 999L, null);
        assertThat(chats.listConversations(a)).extracting(v -> v.conversationId()).containsExactly(newer, older, normal);
        setTimes(a, older, 200L, 900L);
        assertThat(chats.listConversations(a)).extracting(v -> v.conversationId()).containsExactly(older, newer, normal);
        chats.setPinned(a, older, false);
        assertThat(chats.listConversations(a)).extracting(v -> v.conversationId()).containsExactly(newer, normal, older);
    }

    @Test void nonMembersAndFormerMembersCannotPinButOrdinaryGroupMembersCan() {
        long owner = user(), member = user(), outsider = user();
        String id = chats.createGroupConversation(owner, "pin group", java.util.List.of(member), UUID.randomUUID().toString()).conversationId();
        assertThat(chats.setPinned(member, id, true).pinnedAt()).isNotNull();
        assertThatThrownBy(() -> chats.setPinned(outsider, id, true)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        members.deleteById(new ConversationMemberId(id, member));
        assertThatThrownBy(() -> chats.setPinned(member, id, false)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
    }

    @Test void httpUsesAuthenticatedAccountAndRejectsMissingBoolean() throws Exception {
        var login = auth.register("pin_" + UUID.randomUUID(), "Testpass123", "Tester", null, null);
        long a = login.user().id(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        String path = "/api/conversations/" + id + "/pinned";
        mvc.perform(post(path).contentType("application/json").content("{\"pinned\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"pinned\":true,\"userId\":" + b + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(a)).andExpect(jsonPath("$.data.pinnedAt").isNumber());
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().pinnedAt).isNull();
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + login.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].pinnedAt").isNumber());
        mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json").content("{\"pinned\":false}"))
            .andExpect(status().isOk());
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().pinnedAt).isNull();
    }

    @Test void additiveSqlPreservesExistingStateAndDefaultsToUnpinned() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:pin-upgrade;MODE=MySQL"); var sql = db.createStatement()) {
            sql.execute("CREATE TABLE conversation_user_state(conversation_id VARCHAR, user_id BIGINT, unread_count INT, is_hidden BOOLEAN, last_read_time BIGINT, PRIMARY KEY(conversation_id,user_id))");
            sql.execute("INSERT INTO conversation_user_state VALUES('c', 1, 3, true, 456)");
            sql.execute(Files.readString(Path.of("docs/sql/add-conversation-pinned-at.sql")));
            try (var rows = sql.executeQuery("SELECT * FROM conversation_user_state")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("pinned_at")).isNull();
                assertThat(rows.getInt("unread_count")).isEqualTo(3);
                assertThat(rows.getBoolean("is_hidden")).isTrue();
                assertThat(rows.getLong("last_read_time")).isEqualTo(456);
            }
        }
    }

    private void setTimes(long user, String id, long lastTime, Long pinTime) {
        var conversation = conversations.findById(id).orElseThrow();
        conversation.lastTime = lastTime;
        conversations.save(conversation);
        var state = states.findByConversationIdAndUserId(id, user).orElseThrow();
        state.pinnedAt = pinTime;
        states.save(state);
    }
}
