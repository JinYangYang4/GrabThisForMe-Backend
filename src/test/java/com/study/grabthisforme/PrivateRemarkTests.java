package com.study.grabthisforme;

import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:private-remarks;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PrivateRemarkTests {
    @Autowired AuthService auth;
    @Autowired SocialService social;
    @Autowired ConversationService chats;
    @Autowired PrivateRemarkService remarks;
    @Autowired UserFriendRelationRepository friends;
    @Autowired ConversationUserStateRepository states;
    @Autowired ConversationParticipantRepository members;
    @Autowired ViewAssembler views;
    @Autowired MockMvc mvc;

    long user() { return auth.register("remark_" + UUID.randomUUID(), "Testpass123", "Original", null, null).user().id(); }
    void connect(long a, long b) { social.addFriend(a, b); social.addFriend(b, a); }

    @Test void reciprocalRemarksRemainPrivateAndNeverChangeProfilesOrFriendship() {
        long a = user(), b = user();
        connect(a, b);
        var before = friends.findByUserIdAndFriendUserId(a, b).orElseThrow();
        assertThat(remarks.setFriendRemark(a, b, "  Classmate  ").remark()).isEqualTo("Classmate");
        remarks.setFriendRemark(b, a, "Roommate");
        assertThat(social.listFriends(a).getFirst().remark()).isEqualTo("Classmate");
        assertThat(social.listFriends(b).getFirst().remark()).isEqualTo("Roommate");
        assertThat(social.listFriends(a).getFirst().name()).isEqualTo("Original");
        assertThat(views.getUserBriefView(b).name()).isEqualTo("Original");
        assertThat(friends.findByUserIdAndFriendUserId(a, b).orElseThrow().addedTime).isEqualTo(before.addedTime);
        assertThat(friends.findAllByUserId(a)).hasSize(1);
        assertThat(friends.findAllByUserId(b)).hasSize(1);
    }

    @Test void anyGroupMemberCanSetOwnRemarkWithoutChangingGroupNameOrOtherSettings() {
        long a = user(), b = user();
        connect(a, b);
        var group = chats.createGroupConversation(a, "Original group", List.of(b), UUID.randomUUID().toString());
        String id = group.conversationId();
        chats.sendMessage(a, id, "new", "TEXT", "keep", null);
        chats.setPinned(b, id, true);
        chats.setHidden(b, id, true);
        var before = states.findByConversationIdAndUserId(id, b).orElseThrow();
        remarks.setGroupRemark(b, id, "My group");
        var after = states.findByConversationIdAndUserId(id, b).orElseThrow();
        assertThat(after.groupRemark).isEqualTo("My group");
        assertThat(after.pinnedAt).isEqualTo(before.pinnedAt);
        assertThat(after.isHidden).isEqualTo(before.isHidden);
        assertThat(after.unreadCount).isEqualTo(before.unreadCount);
        assertThat(after.lastReadTime).isEqualTo(before.lastReadTime);
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().groupRemark).isNull();
        assertThat(chats.listConversations(b).stream().filter(c -> c.conversationId().equals(id)).findFirst().orElseThrow().groupRemark()).isEqualTo("My group");
        assertThat(chats.openGroupConversation(a, group.groupId()).groupRemark()).isNull();
        assertThat(views.toGroupView(group.groupId()).groupName()).isEqualTo("Original group");
        chats.markRead(b, id, 123L);
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().groupRemark).isEqualTo("My group");
    }

    @Test void clearingIsIdempotentAndDoesNotClearTheOtherPersonsRemark() {
        long a = user(), b = user();
        connect(a, b);
        remarks.setFriendRemark(a, b, "Mine");
        remarks.setFriendRemark(b, a, "Theirs");
        remarks.setFriendRemark(a, b, " ");
        remarks.setFriendRemark(a, b, "");
        assertThat(social.listFriends(a).getFirst().remark()).isNull();
        assertThat(social.listFriends(b).getFirst().remark()).isEqualTo("Theirs");
        String id = chats.createGroupConversation(a, "Original group", List.of(b), UUID.randomUUID().toString()).conversationId();
        remarks.setGroupRemark(a, id, "Group");
        remarks.setGroupRemark(a, id, "");
        assertThat(states.findByConversationIdAndUserId(id, a).orElseThrow().groupRemark).isNull();
    }

    @Test void lengthCountsUnicodeCodePointsAndInvalidInputDoesNotOverwriteSavedValue() {
        long a = user(), b = user();
        connect(a, b);
        String emoji = new String(Character.toChars(0x1F600));
        String allowed = emoji.repeat(30);
        remarks.setFriendRemark(a, b, allowed);
        for (String invalid : List.of("a".repeat(31), emoji.repeat(31), "one\ntwo", "one\ttwo")) {
            assertThatThrownBy(() -> remarks.setFriendRemark(a, b, invalid)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        }
        assertThatThrownBy(() -> remarks.setFriendRemark(a, b, null)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        assertThat(social.listFriends(a).getFirst().remark()).isEqualTo(allowed);
    }

    @Test void outsidersFormerMembersAndNonFriendsCannotWriteRemarks() {
        long a = user(), b = user(), outsider = user();
        connect(a, b);
        assertThatThrownBy(() -> remarks.setFriendRemark(outsider, b, "forbidden")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        String id = chats.createGroupConversation(a, "Original group", List.of(b), UUID.randomUUID().toString()).conversationId();
        assertThatThrownBy(() -> remarks.setGroupRemark(outsider, id, "forbidden")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        members.deleteById(new ConversationMemberId(id, b));
        assertThatThrownBy(() -> remarks.setGroupRemark(b, id, "forbidden")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        String direct = chats.createSingleConversation(a, b).conversationId();
        assertThatThrownBy(() -> remarks.setGroupRemark(a, direct, "not a group")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        friends.deleteById(new FriendRelationId(a, b));
        assertThatThrownBy(() -> remarks.setFriendRemark(a, b, "forbidden")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        assertThat(states.findByConversationIdAndUserId(direct, a).orElseThrow().groupRemark).isNull();
    }

    @Test void httpRequiresAuthValidatesTextAndIgnoresSpoofedOwner() throws Exception {
        var login = auth.register("remark_" + UUID.randomUUID(), "Testpass123", "Original", null, null);
        long a = login.user().id(), b = user();
        connect(a, b);
        String friendPath = "/api/social/friends/" + b + "/remark";
        String id = chats.createGroupConversation(a, "Original group", List.of(b), UUID.randomUUID().toString()).conversationId();
        for (String path : List.of(friendPath, "/api/conversations/" + id + "/group-remark")) {
            mvc.perform(post(path).contentType("application/json").content("{\"remark\":\"secret\"}"))
                .andExpect(status().isUnauthorized());
            for (String invalid : List.of("{}", "{\"remark\":null}", "{\"remark\":\"" + "a".repeat(31) + "\"}")) {
                mvc.perform(post(path).header("Authorization", "Bearer " + login.token())
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
            }
            mvc.perform(post(path).header("Authorization", "Bearer " + login.token()).contentType("application/json")
                .content("{\"remark\":\"secret\",\"userId\":" + b + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.remark").value("secret"));
        }
        assertThat(friends.findByUserIdAndFriendUserId(b, a).orElseThrow().remark).isNull();
        assertThat(states.findByConversationIdAndUserId(id, b).orElseThrow().groupRemark).isNull();
        mvc.perform(get("/api/social/friends").header("Authorization", "Bearer " + login.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].remark").value("secret"))
            .andExpect(jsonPath("$.data[0].name").value("Original"));
    }
}
