package com.study.grabthisforme;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.MessageEntity;
import com.study.grabthisforme.persistence.repository.MessageRepository;
import com.study.grabthisforme.service.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:message-actions;MODE=MySQL;DB_CLOSE_DELAY=-1")
class MessageActionsTests {
    static final Path MEDIA;
    static { try { MEDIA = Files.createTempDirectory("message-actions-media-"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("grabthisforme.media.directory", MEDIA::toString); }
    @Autowired AuthService auth;
    @Autowired ConversationService chats;
    @Autowired MessageActionsService actions;
    @Autowired MessageRepository messages;
    @Autowired SocialService social;
    @Autowired MediaService media;
    @Autowired ConversationMembershipService membership;
    long user() { return auth.register("action_" + UUID.randomUUID(), "Testpass123", "测试用户", null, null).user().id(); }

    @Test void recallBoundaryUsesServerClockAndOwner() {
        var m = new MessageEntity(); m.senderId = 7L; m.timestamp = 1000L; m.type = "TEXT";
        assertThatCode(() -> MessageActionPolicy.requireRecallOwnerAndTime(m, 7, 181000)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MessageActionPolicy.requireRecallOwnerAndTime(m, 7, 181001)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MessageActionPolicy.requireRecallOwnerAndTime(m, 8, 1001)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MessageActionPolicy.requireRecallOwnerAndTime(m, 7, 999)).isInstanceOf(ApiException.class);
    }
    @Test void recallIsIdempotentAndQuotesDoNotExposeRecalledText() {
        long a=user(), b=user(), outsider=user();
        String cid=chats.createSingleConversation(a,b).conversationId();
        var source=chats.sendMessage(a,cid,"original","TEXT","private original",null);
        var reply=chats.sendMessage(b,cid,"reply","TEXT","答复",null,source.messageId());
        assertThat(reply.replyPreview()).isEqualTo("private original");
        assertThatThrownBy(() -> actions.recall(b,cid,source.messageId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> actions.recall(outsider,cid,source.messageId())).isInstanceOf(ApiException.class);
        var recalled=actions.recall(a,cid,source.messageId());
        assertThat(recalled.type()).isEqualTo("SYSTEM");
        assertThat(recalled.content()).doesNotContain("private original");
        assertThat(recalled.mediaUrl()).isNull();
        assertThat(actions.recall(a,cid,source.messageId()).recalledAt()).isEqualTo(recalled.recalledAt());
        assertThat(actions.updates(b,cid,0,"")).extracting(v->v.messageId()).contains(source.messageId());
        assertThat(chats.listMessages(b,cid,null,100).stream().filter(v->v.messageId().equals(reply.messageId())).findFirst().orElseThrow().replyPreview()).isEqualTo("原消息已撤回");
        assertThatThrownBy(() -> chats.sendMessage(b,cid,"invalid-reply","TEXT","x",null,source.messageId())).isInstanceOf(ApiException.class);
        String other=chats.createSingleConversation(a,outsider).conversationId();
        assertThatThrownBy(() -> chats.sendMessage(a,other,"cross-reply","TEXT","x",null,reply.messageId())).isInstanceOf(ApiException.class);
    }
    @Test void serverRejectsExpiredRecallAndForgedSystemMessages() {
        long a=user(), b=user(); String cid=chats.createSingleConversation(a,b).conversationId();
        var sent=chats.sendMessage(a,cid,"old","TEXT","旧消息",null);
        var row=messages.findById(sent.messageId()).orElseThrow(); row.timestamp=System.currentTimeMillis()-180001; messages.save(row);
        assertThatThrownBy(() -> actions.recall(a,cid,sent.messageId())).isInstanceOf(ApiException.class).hasMessageContaining("3 分钟");
        assertThatThrownBy(() -> chats.sendMessage(a,cid,"fake","SYSTEM","fake system",null)).isInstanceOf(ApiException.class);
    }
    @Test void systemEventsAreNotDuplicatedByOpeningOrRetrying() {
        long a=user(), b=user(), c=user();
        String cid=chats.createSingleConversation(a,b).conversationId();
        chats.createSingleConversation(a,b);
        assertThat(chats.listMessages(a,cid,null,100).stream().filter(m->"TEMPORARY_CONVERSATION".equals(m.systemEvent()))).hasSize(1);
        social.addFriend(a,b); var request=social.listFriendRequests(b).getFirst();
        social.acceptFriendRequest(b,request.requestId()); social.acceptFriendRequest(b,request.requestId());
        assertThat(chats.listMessages(a,cid,null,100).stream().filter(m->"FRIEND_ACCEPTED".equals(m.systemEvent()))).hasSize(1);
        var group=chats.createGroupConversation(a,"测试群",List.of(b),"group-once");
        chats.createGroupConversation(a,"测试群",List.of(b),"group-once");
        membership.join(c,group.groupId()); membership.join(c,group.groupId());
        assertThat(chats.listMessages(a,group.conversationId(),null,100).stream().map(m->m.systemEvent())).containsExactlyInAnyOrder("GROUP_CREATED","MEMBER_JOINED");
    }
    @Test void forwardingPreservesPrivacyAndRetryDoesNotDuplicate() throws Exception {
        long a=user(), b=user(), c=user();
        String source=chats.createSingleConversation(a,b).conversationId(), target=chats.createSingleConversation(b,c).conversationId();
        var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",bytes);
        var upload=media.upload(a,new MockMultipartFile("file","test.png","image/png",bytes.toByteArray()),source);
        var sent=chats.sendMessage(a,source,"photo","IMAGE",null,upload.url());
        var forwarded=actions.forward(b,target,source,sent.messageId(),"forward-once");
        assertThat(forwarded.mediaUrl()).isNotEqualTo(sent.mediaUrl());
        String assetId=forwarded.mediaUrl().substring(forwarded.mediaUrl().lastIndexOf('/')+1);
        assertThat(media.accessible(assetId,c).conversationId).isEqualTo(target);
        assertThatThrownBy(() -> media.accessible(assetId,a)).isInstanceOf(ApiException.class);
        assertThat(actions.forward(b,target,source,sent.messageId(),"forward-once").messageId()).isEqualTo(forwarded.messageId());
        assertThatThrownBy(() -> actions.forward(c,target,source,sent.messageId(),"stolen")).isInstanceOf(ApiException.class);
        actions.recall(a,source,sent.messageId());
        assertThatThrownBy(() -> actions.forward(b,target,source,sent.messageId(),"after-recall")).isInstanceOf(ApiException.class);
        assertThat(Files.isRegularFile(media.file(media.accessible(assetId,c)))).isTrue();
    }
}
