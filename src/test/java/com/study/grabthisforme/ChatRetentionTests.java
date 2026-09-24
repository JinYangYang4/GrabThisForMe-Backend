package com.study.grabthisforme;

import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.service.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ChatRetentionTests {
    static final long DAY = 86_400_000L;
    static final Path DIRECTORY;
    static { try { DIRECTORY = Files.createTempDirectory("chat-retention-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("grabthisforme.media.directory", DIRECTORY::toString);
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:chat-retention-test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        r.add("grabthisforme.chat.cleanup-enabled", () -> "false");
    }
    @Autowired AuthService auth;
    @Autowired MediaService media;
    @Autowired ConversationService chats;
    @Autowired ChatRetentionService retention;
    @Autowired MediaRepository assets;
    @Autowired MessageRepository messages;
    @Autowired ConversationRepository conversations;
    @Autowired ConversationUserStateRepository states;
    @Autowired MockMvc mvc;

    record Pair(AuthService.AuthResult a, AuthService.AuthResult b, String cid) {}
    AuthService.AuthResult user() { return auth.register("ttl_"+UUID.randomUUID(), "Testpass123", "Tester", null, null); }
    Pair pair() {
        var a=user(); var b=user();
        return new Pair(a,b,chats.createSingleConversation(a.user().id(),b.user().id()).conversationId());
    }
    String send(Pair p, String type, String url) {
        return chats.sendMessage(p.a.user().id(),p.cid,UUID.randomUUID().toString(),type,
            "TEXT".equals(type)?"retention test":null,url).messageId();
    }
    void messageTime(String id,long time) { var m=messages.findById(id).orElseThrow(); m.timestamp=time; messages.saveAndFlush(m); }
    void assetTime(String id,long time) {
        var m=assets.findById(id).orElseThrow(); m.createdTime=time; m.retentionTime=null; assets.saveAndFlush(m);
    }
    MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32,20,BufferedImage.TYPE_INT_ARGB),"png",out);
        return new MockMultipartFile("file","image.png","image/png",out.toByteArray());
    }
    MockMultipartFile video() {
        byte[] bytes=ByteBuffer.allocate(44).putInt(20).put("ftypisom".getBytes()).putInt(0).put("mp42".getBytes())
            .putInt(12).put("moov".getBytes()).putInt(1).putInt(12).put("mdat".getBytes()).putInt(1).array();
        return new MockMultipartFile("file","v.mp4","video/mp4",bytes);
    }
    MockMultipartFile cover() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64,36,BufferedImage.TYPE_INT_RGB),"jpeg",out);
        return new MockMultipartFile("cover","cover.jpg","image/jpeg",out.toByteArray());
    }

    @Test void thirtyDayBoundaryRepairsLastMessageAndUnreadWithoutDeletingConversation() {
        var p=pair(); long now=System.currentTimeMillis(); long cutoff=now-30*DAY;
        // The newly-created temporary-conversation event is part of retained history too.
        messages.findAllByConversationIdOrderByTimestampAsc(p.cid).forEach(m -> messageTime(m.messageId, cutoff-1));
        String old=send(p,"TEXT",null), boundary=send(p,"TEXT",null), fresh=send(p,"TEXT",null);
        messageTime(old,cutoff-1); messageTime(boundary,cutoff); messageTime(fresh,now);
        retention.cleanupAt(now);
        assertThat(messages.existsById(old)).isFalse();
        assertThat(messages.existsById(boundary)).isTrue();
        assertThat(messages.existsById(fresh)).isTrue();
        assertThat(conversations.findById(p.cid).orElseThrow().lastMessageId).isEqualTo(fresh);
        assertThat(states.findByConversationIdAndUserId(p.cid,p.b.user().id()).orElseThrow().unreadCount).isEqualTo(2);
        messageTime(boundary,cutoff-1); messageTime(fresh,cutoff-1);
        retention.cleanupAt(now);
        assertThat(conversations.findById(p.cid).orElseThrow().lastMessageId).isNull();
        assertThat(states.findByConversationIdAndUserId(p.cid,p.b.user().id()).orElseThrow().unreadCount).isZero();
        assertThat(chats.listMessages(p.a.user().id(),p.cid,null,100)).isEmpty();
    }

    @Test void expiresPrivateImagesVideosAndCoversButNeverPublicMedia() throws Exception {
        var p=pair(); long now=System.currentTimeMillis();
        var photo=media.upload(p.a.user().id(),image(),p.cid);
        var movie=media.uploadVideo(p.a.user().id(),video(),p.cid,cover());
        var publicPhoto=media.upload(p.a.user().id(),image(),null);
        var publicMovie=media.uploadVideo(p.a.user().id(),video(),null,cover());
        String photoMessage=send(p,"IMAGE",photo.url()), videoMessage=send(p,"VIDEO",movie.url());
        messageTime(photoMessage,now-31*DAY); messageTime(videoMessage,now-31*DAY);
        for (var m:java.util.List.of(photo,movie,publicPhoto,publicMovie)) assetTime(m.mediaId(),now-31*DAY);
        Path photoFile=media.file(assets.findById(photo.mediaId()).orElseThrow());
        Path videoFile=media.file(assets.findById(movie.mediaId()).orElseThrow());
        Path coverFile=media.coverFile(movie.mediaId(),p.a.user().id());
        retention.cleanupAt(now);
        assertThat(assets.existsById(photo.mediaId())).isFalse();
        assertThat(assets.existsById(movie.mediaId())).isFalse();
        assertThat(Files.exists(photoFile)).isFalse(); assertThat(Files.exists(videoFile)).isFalse();
        assertThat(Files.exists(coverFile)).isFalse();
        mvc.perform(get("/"+photo.url()).header("Authorization","Bearer "+p.a.token())).andExpect(status().isNotFound());
        mvc.perform(get("/"+publicPhoto.url())).andExpect(status().isOk());
        mvc.perform(get("/"+publicMovie.url()+"/cover")).andExpect(status().isOk());
        mvc.perform(get("/"+publicMovie.url()).header("Range","bytes=0-11")).andExpect(status().isPartialContent());
    }

    @Test void keepsAttachmentWhileAnyMessageStillReferencesItIncludingLegacyAbsoluteUrls() throws Exception {
        var p=pair(); long now=System.currentTimeMillis();
        var photo=media.upload(p.a.user().id(),image(),p.cid);
        String old=send(p,"IMAGE",photo.url()), recent=send(p,"IMAGE",photo.url());
        messageTime(old,now-31*DAY);
        var m=messages.findById(recent).orElseThrow(); m.mediaUrl="https://old-host.example/"+photo.url();
        messages.saveAndFlush(m); assetTime(photo.mediaId(),now-31*DAY);
        retention.cleanupAt(now);
        assertThat(messages.existsById(old)).isFalse(); assertThat(messages.existsById(recent)).isTrue();
        assertThat(assets.existsById(photo.mediaId())).isTrue();
        messageTime(recent,now-31*DAY); retention.cleanupAt(now);
        assertThat(assets.existsById(photo.mediaId())).isFalse();
    }

    @Test void reuploadRenewsOrphanGracePeriodAndSendRemainsIdempotent() throws Exception {
        var p=pair(); long now=System.currentTimeMillis();
        var photo=media.upload(p.a.user().id(),image(),p.cid); assetTime(photo.mediaId(),now-31*DAY);
        assertThat(media.upload(p.a.user().id(),image(),p.cid).mediaId()).isEqualTo(photo.mediaId());
        retention.cleanupAt(now); assertThat(assets.existsById(photo.mediaId())).isTrue();
        String clientId=UUID.randomUUID().toString();
        var sent=chats.sendMessage(p.a.user().id(),p.cid,clientId,"IMAGE",null,photo.url());
        assertThat(chats.sendMessage(p.a.user().id(),p.cid,clientId,"IMAGE",null,photo.url()).messageId()).isEqualTo(sent.messageId());
        assertThatThrownBy(() -> media.validateChatAttachment(p.b.user().id(),p.cid,photo.url())).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
    }

    @Test void failedFileDeletionKeepsDurableTombstoneAndRetriesSafely() throws Exception {
        var p=pair(); long now=System.currentTimeMillis();
        var photo=media.upload(p.a.user().id(),image(),p.cid); assetTime(photo.mediaId(),now-31*DAY);
        // A nonempty directory deterministically makes deleting the sidecar fail on every OS.
        Path blocked=Files.createDirectory(DIRECTORY.resolve(photo.mediaId()+".cover.jpg"));
        Path blocker=Files.createFile(blocked.resolve("test-blocker"));
        assertThat(retention.cleanupAt(now).failures()).isGreaterThan(0);
        assertThat(assets.findById(photo.mediaId()).orElseThrow().deletionPending).isTrue();
        mvc.perform(get("/"+photo.url()).header("Authorization","Bearer "+p.a.token())).andExpect(status().isGone());
        assertThatThrownBy(() -> media.upload(p.a.user().id(),image(),p.cid)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
        Files.delete(blocker); Files.delete(blocked);
        retention.cleanupAt(now);
        assertThat(assets.existsById(photo.mediaId())).isFalse();
        retention.cleanupAt(now); // Missing bytes and repeated sweeps are harmless.
        assertThat(media.upload(p.a.user().id(),image(),p.cid).mediaId()).isEqualTo(photo.mediaId());
    }

    @Test void cleansMoreThanOnePageWithoutSkippingRowsAndKeepsUnknownAge() throws Exception {
        long now=System.currentTimeMillis();
        var p=pair(); var unknown=media.upload(p.a.user().id(),image(),p.cid);
        var unknownRow=assets.findById(unknown.mediaId()).orElseThrow(); unknownRow.createdTime=null; assets.saveAndFlush(unknownRow);
        var ids=new java.util.ArrayList<String>();
        for(int i=0;i<105;i++) {
            String id="ttl-batch-"+UUID.randomUUID(); ids.add(id);
            var c=new ConversationEntity(); c.conversationId=id; c.conversationType="SINGLE"; c.lastTime=now-31*DAY;
            c.lastMessageId=id; conversations.save(c);
            var m=new MessageEntity(); m.messageId=id; m.conversationId=id; m.timestamp=now-31*DAY;
            m.senderId=p.a.user().id(); m.type="TEXT"; messages.save(m);
            var a=new MediaEntity(); a.mediaId=id; a.ownerId=p.a.user().id(); a.conversationId=id;
            a.contentType="image/png"; a.createdTime=now-31*DAY; assets.save(a); // Missing file is a valid retry case.
        }
        retention.cleanupAt(now);
        for(String id:ids) { assertThat(messages.existsById(id)).isFalse(); assertThat(assets.existsById(id)).isFalse(); }
        assertThat(assets.existsById(unknown.mediaId())).isTrue();
    }

    @Test void schedulerCanBeDisabledWithoutRunningCleanup() {
        var p=pair(); String id=send(p,"TEXT",null); messageTime(id,System.currentTimeMillis()-31*DAY);
        retention.cleanup();
        assertThat(messages.existsById(id)).isTrue();
    }
}
