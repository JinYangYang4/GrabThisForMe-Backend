package com.study.grabthisforme;

import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.entity.ConversationMemberId;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.controller.ConversationSocketController;
import com.study.grabthisforme.auth.StompPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:conversation-hidden;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ConversationHiddenTests {
    @Autowired AuthService auth;
    @Autowired ConversationService chats;
    @Autowired ConversationUserStateRepository states;
    @Autowired ConversationParticipantRepository members;
    @Autowired MessageRepository messages;
    @Autowired ConversationSocketController socket;
    @Autowired MockMvc mvc;
    long user() { return auth.register("hidden_" + UUID.randomUUID(), "Testpass123", "Tester", null, null).user().id(); }

    @Test void hidingOnlyChangesMyVisibilityAndPreservesMessagesPinAndUnread() {
        long a = user(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        chats.sendMessage(b, id, "preserved", "TEXT", "keep this message", null);
        Long pin = chats.setPinned(a, id, true).pinnedAt();
        var before = states.findByConversationIdAndUserId(id, a).orElseThrow();
        var history = messages.findAllByConversationIdOrderByTimestampAsc(id);
        chats.setHidden(a, id, true);
        var hidden = states.findByConversationIdAndUserId(id, a).orElseThrow();
        assertThat(hidden.isHidden).isTrue();
        assertThat(hidden.unreadCount).isEqualTo(before.unreadCount);
        assertThat(hidden.lastReadTime).isEqualTo(before.lastReadTime);
        assertThat(hidden.pinnedAt).isEqualTo(pin);
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().isHidden).isFalse();
        assertThat(messages.findAllByConversationIdOrderByTimestampAsc(id)).extracting(m -> m.messageId)
            .containsExactlyElementsOf(history.stream().map(m -> m.messageId).toList());
        assertThat(chats.listConversations(a).getFirst().isHidden()).isTrue();
        chats.setHidden(a, id, false);
        assertThat(chats.listConversations(a).getFirst().isHidden()).isFalse();
        assertThat(chats.listConversations(a).getFirst().pinnedAt()).isEqualTo(pin);
    }

    @Test void readingAndOpeningHiddenHistoryNeverMakesItVisible() {
        long a = user(), b = user();
        var group = chats.createGroupConversation(a, "hidden group", List.of(b), UUID.randomUUID().toString());
        String id = group.conversationId();
        Long pin = chats.setPinned(b, id, true).pinnedAt();
        chats.setHidden(b, id, true);
        chats.markRead(b, id, 123L);
        chats.listMessages(b, id, null, 20);
        chats.openGroupConversation(b, group.groupId());
        var state = states.findByConversationIdAndUserId(id, b).orElseThrow();
        assertThat(state.isHidden).isTrue();
        assertThat(state.unreadCount).isZero();
        assertThat(state.lastReadTime).isEqualTo(123L);
        assertThat(state.pinnedAt).isEqualTo(pin);
    }

    @Test void newMessageRestoresVisibilityForSenderAndRecipientButDuplicateDoesNot() {
        long a = user(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        Long pin = chats.setPinned(a, id, true).pinnedAt();
        chats.sendMessage(b, id, "old-message", "TEXT", "old", null);
        chats.setHidden(a, id, true);
        chats.setHidden(b, id, true);
        chats.sendMessage(b, id, "old-message", "TEXT", "old", null);
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().isHidden).isTrue();
        chats.sendMessage(b, id, "new-message", "TEXT", "new", null);
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().isHidden).isFalse();
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().isHidden).isFalse();
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().pinnedAt).isEqualTo(pin);
    }

    @Test void websocketReadUsesSameNonUnhidingRule() {
        long a = user(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        chats.setHidden(a, id, true);
        socket.markConversationRead(new ConversationSocketController.ReadConversationSocketRequest(id, 321L),
            new StompPrincipal("test-session", a));
        var state = states.findByConversationIdAndUserId(id, a).orElseThrow();
        assertThat(state.isHidden).isTrue();
        assertThat(state.lastReadTime).isEqualTo(321L);
    }

    @Test void formerMembersAndOutsidersCannotModifyVisibility() {
        long a = user(), b = user(), outsider = user();
        String id = chats.createGroupConversation(a, "hidden group", List.of(b), UUID.randomUUID().toString()).conversationId();
        assertThatThrownBy(() -> chats.setHidden(outsider, id, true)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        members.deleteById(new ConversationMemberId(id, b));
        assertThatThrownBy(() -> chats.setHidden(b, id, true)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
    }

    @Test void httpValidatesBooleanUsesAuthenticatedOwnerAndReadKeepsHidden() throws Exception {
        var login = auth.register("hidden_" + UUID.randomUUID(), "Testpass123", "Tester", null, null);
        long a = login.user().id(), b = user();
        String id = chats.createSingleConversation(a, b).conversationId();
        String path = "/api/conversations/" + id;
        mvc.perform(post(path + "/hidden").contentType("application/json").content("{\"hidden\":true}"))
            .andExpect(status().isUnauthorized());
        for (String body : List.of("{}", "{\"hidden\":null}")) {
            mvc.perform(post(path + "/hidden").header("Authorization", "Bearer " + login.token()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post(path + "/hidden").header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"hidden\":true,\"userId\":" + b + "}")).andExpect(status().isOk());
        mvc.perform(post(path + "/read").header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"lastReadTime\":123}")).andExpect(status().isOk());
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().isHidden).isTrue();
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().isHidden).isFalse();
        mvc.perform(post(path + "/hidden").header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"hidden\":false}")).andExpect(status().isOk());
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().isHidden).isFalse();
    }
}
