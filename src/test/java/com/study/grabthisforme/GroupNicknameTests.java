package com.study.grabthisforme;

import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:group-nicknames;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GroupNicknameTests {
    @Autowired AuthService auth;
    @Autowired SocialService social;
    @Autowired ConversationService chats;
    @Autowired GroupNicknameService nicknames;
    @Autowired PrivateRemarkService remarks;
    @Autowired ConversationUserStateRepository states;
    @Autowired ConversationParticipantRepository members;
    @Autowired ViewAssembler views;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PushService push;

    long user() { return auth.register("nick_" + UUID.randomUUID(), "Testpass123", "Original", null, null).user().id(); }
    String group(long a, long b) {
        social.addFriend(a, b); social.addFriend(b, a);
        return chats.createGroupConversation(a, "Original group", List.of(b), UUID.randomUUID().toString()).conversationId();
    }
    ConversationParticipantEntity member(String id, long uid) { return members.findById(new ConversationMemberId(id, uid)).orElseThrow(); }

    @Test void memberCanOnlyChangeOwnNicknameInThisGroupWithoutChangingHistoryOrPrivateSettings() {
        long a = user(), b = user();
        String id = group(a, b);
        String other = chats.createGroupConversation(a, "Second group", List.of(b), UUID.randomUUID().toString()).conversationId();
        chats.sendMessage(b, id, "original-message", "TEXT", "Original body", null);
        chats.setPinned(b, id, true);
        chats.setHidden(b, id, true);
        remarks.setGroupRemark(b, id, "My private group");
        remarks.setFriendRemark(a, b, "My private friend");
        var before = member(id, b);
        var beforeState = states.findByConversationIdAndUserId(id, b).orElseThrow();
        var saved = nicknames.setMyNickname(b, id, "  Group name  ");
        assertThat(saved.nickname()).isEqualTo("Group name");
        assertThat(saved.userId()).isEqualTo(b);
        assertThat(saved.conversationId()).isEqualTo(id);
        assertThat(member(id, a).nickname).isNull();
        assertThat(member(other, b).nickname).isNull();
        assertThat(member(id, b)).usingRecursiveComparison().ignoringFields("nickname").isEqualTo(before);
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow()).usingRecursiveComparison().isEqualTo(beforeState);
        assertThat(views.getUserBriefView(b).name()).isEqualTo("Original");
        var snapshot = chats.listConversations(a).stream().filter(c -> c.conversationId().equals(id)).findFirst().orElseThrow();
        assertThat(snapshot.memberships().stream().filter(m -> m.userId() == b).findFirst().orElseThrow().nickname()).isEqualTo("Group name");
        assertThat(snapshot.lastMessage().content()).isEqualTo("Original body");
        assertThat(snapshot.groupName()).isEqualTo("Original group");
        assertThat(snapshot.participants().stream().filter(u -> u.id() == b).findFirst().orElseThrow().name()).isEqualTo("Original");
        assertThat(social.listGroups(a).stream().filter(g -> g.conversationId().equals(id)).findFirst().orElseThrow()
            .members().stream().filter(m -> m.userId() == b).findFirst().orElseThrow().nickname()).isEqualTo("Group name");
        assertThat(views.toGroupView(snapshot.groupId()).members()).allMatch(m -> m.nickname() == null);
        assertThat(views.toGroupView(snapshot.groupId(), user()).members()).allMatch(m -> m.nickname() == null);
        assertThat(social.listFriends(a).getFirst().remark()).isEqualTo("My private friend");
    }

    @Test void outsidersFormerMembersAndDirectChatCannotWrite() {
        long a = user(), b = user(), outsider = user();
        String id = group(a, b);
        assertThatThrownBy(() -> nicknames.setMyNickname(outsider, id, "Forbidden")).isInstanceOf(ApiException.class);
        members.deleteById(new ConversationMemberId(id, b));
        assertThatThrownBy(() -> nicknames.setMyNickname(b, id, "Forbidden")).isInstanceOf(ApiException.class);
        String direct = chats.createSingleConversation(a, b).conversationId();
        assertThatThrownBy(() -> nicknames.setMyNickname(a, direct, "Forbidden")).isInstanceOf(ApiException.class);
        assertThat(member(direct, a).nickname).isNull();
    }

    @Test void validatesUnicodeAndClearWithoutChangingAnotherMember() {
        long a = user(), b = user();
        String id = group(a, b);
        String emoji = new String(Character.toChars(0x1F600));
        nicknames.setMyNickname(a, id, "Owner");
        nicknames.setMyNickname(b, id, emoji.repeat(30));
        for (String invalid : List.of("a".repeat(31), emoji.repeat(31), "one\ntwo", "one\ttwo", "one\u2028two")) {
            assertThatThrownBy(() -> nicknames.setMyNickname(b, id, invalid)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> nicknames.setMyNickname(b, id, null)).isInstanceOf(ApiException.class);
        assertThat(member(id, b).nickname).isEqualTo(emoji.repeat(30));
        assertThat(nicknames.setMyNickname(b, id, " ").nickname()).isNull();
        assertThat(nicknames.setMyNickname(b, id, "").nickname()).isNull();
        assertThat(member(id, a).nickname).isEqualTo("Owner");
    }

    @Test void httpUsesAuthenticatedOwnerAndRejectsMissingOrInvalidText() throws Exception {
        var login = auth.register("nick_" + UUID.randomUUID(), "Testpass123", "Original", null, null);
        long a = login.user().id(), b = user();
        String id = group(a, b);
        String path = "/api/conversations/" + id + "/my-nickname";
        mvc.perform(post(path).contentType("application/json").content("{\"nickname\":\"Nick\"}")).andExpect(status().isUnauthorized());
        for (String invalid : List.of("{}", "{\"nickname\":null}", "{\"nickname\":\"" + "a".repeat(31) + "\"}")) {
            mvc.perform(post(path).header("Authorization", "Bearer " + login.token())
                .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"nickname\":\"Nick\",\"userId\":" + b + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(a))
            .andExpect(jsonPath("$.data.nickname").value("Nick")).andExpect(jsonPath("$.data.conversationId").value(id));
        assertThat(member(id, b).nickname).isNull();
        mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json")
            .content("{\"nickname\":\"\"}")).andExpect(status().isOk());
        assertThat(member(id, a).nickname).isNull();
    }

    @Test void notificationIsAfterCommitMemberOnlyAndIdempotent() {
        long a = user(), b = user();
        String id = group(a, b);
        clearInvocations(push);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            nicknames.setMyNickname(b, id, "Nick");
            verifyNoInteractions(push);
        });
        var payload = Map.of("type", "conversation.members.updated", "conversationId", id);
        verify(push).pushToUser(a, "conversation.members.updated", payload);
        verify(push).pushToUser(b, "conversation.members.updated", payload);
        verifyNoMoreInteractions(push);
        clearInvocations(push);
        nicknames.setMyNickname(b, id, " Nick ");
        verifyNoInteractions(push);
    }

    @Test void rollbackDoesNotNotifyOrPersistAndPushFailureDoesNotUndoCommit() {
        long a = user(), b = user();
        String id = group(a, b);
        clearInvocations(push);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            nicknames.setMyNickname(b, id, "Rolled back");
            status.setRollbackOnly();
        });
        assertThat(member(id, b).nickname).isNull();
        verifyNoInteractions(push);
        doThrow(new IllegalStateException("Offline")).when(push).pushToUser(eq(a), eq("conversation.members.updated"), any());
        assertThat(nicknames.setMyNickname(b, id, "Committed").nickname()).isEqualTo("Committed");
        assertThat(member(id, b).nickname).isEqualTo("Committed");
        verify(push).pushToUser(eq(b), eq("conversation.members.updated"), any());
    }
}
