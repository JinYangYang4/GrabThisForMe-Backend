package com.study.grabthisforme;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.study.grabthisforme.auth.*;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.service.*;
import com.study.grabthisforme.service.sms.SmsVerificationSender;
import com.study.grabthisforme.persistence.repository.PasswordResetRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
class AccountAndMediaTests {
    static final Path MEDIA;
    static { try { MEDIA=Files.createTempDirectory("grab-media-test-"); } catch(Exception e) {throw new ExceptionInInitializerError(e);} }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {r.add("grabthisforme.media.directory",MEDIA::toString);}
    @Autowired AuthService auth;
    @Autowired TokenService tokens;
    @Autowired PasswordService passwords;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordResetRepository resets;
    @Autowired MediaService media;
    @Autowired ConversationService conversations;
    @Autowired MockMvc mvc;
    @MockitoBean SmsVerificationSender sms;

    AuthService.AuthResult user() {return user(null);}
    AuthService.AuthResult user(String phone) {return auth.register("test_"+UUID.randomUUID(),"Testpass123","Tester",phone,null);}
    MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","image.png","image/png",out.toByteArray());
    }
    String capturedCode(String phone) {
        var code=ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq(phone),code.capture());
        return code.getValue();
    }

    @Test void wrongOldPasswordDoesNotRevokeButChangeAndLogoutDo() throws Exception {
        var a=user();
        assertThatThrownBy(()->auth.changePassword(a.user().id(),"Wrong123","Changed123")).isInstanceOf(ApiException.class);
        assertThat(tokens.parse(a.token()).userId()).isEqualTo(a.user().id());
        var changed=auth.changePassword(a.user().id(),"Testpass123","Changed123");
        assertThatThrownBy(()->tokens.parse(a.token())).isInstanceOf(ApiException.class);
        assertThat(tokens.parse(changed.token()).userId()).isEqualTo(a.user().id());
        assertThatThrownBy(()->auth.login(a.user().accountName(),"Testpass123")).isInstanceOf(ApiException.class);
        auth.logout(a.user().id());
        assertThatThrownBy(()->tokens.parse(changed.token())).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/users/me/password").contentType("application/json")
            .content("{\"currentPassword\":\"Changed123\",\"newPassword\":\"Another123\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void resetConsumesChallengeAndRevokesSessions() {
        when(sms.available()).thenReturn(true);
        String phone="13900000001"; var a=user(phone);
        var c=recovery.request(a.user().accountName(),phone); String code=capturedCode(phone);
        assertThat(resets.findById(c.challengeId()).orElseThrow().codeHash).isNotEqualTo(code);
        recovery.reset(c.challengeId(),code,"Changed123");
        assertThatThrownBy(()->tokens.parse(a.token())).isInstanceOf(ApiException.class);
        assertThat(auth.login(a.user().accountName(),"Changed123").user().id()).isEqualTo(a.user().id());
        assertThatThrownBy(()->recovery.reset(c.challengeId(),code,"Another123")).isInstanceOf(ApiException.class);
    }

    @Test void failedAttemptsAreCommittedAndLimitBlocksCorrectCode() {
        when(sms.available()).thenReturn(true);
        String phone="13900000002"; var a=user(phone); var c=recovery.request(a.user().accountName(),phone);
        String code=capturedCode(phone), wrong=code.equals("000000")?"000001":"000000";
        for (int i=0;i<5;i++) assertThatThrownBy(()->recovery.reset(c.challengeId(),wrong,"Changed123")).isInstanceOf(ApiException.class);
        assertThat(resets.findById(c.challengeId()).orElseThrow().attempts).isEqualTo(5);
        assertThatThrownBy(()->recovery.reset(c.challengeId(),code,"Changed123")).isInstanceOf(ApiException.class);
        assertThat(tokens.parse(a.token()).userId()).isEqualTo(a.user().id());
    }

    @Test void passwordChangeInvalidatesPreviouslyIssuedResetChallenge() {
        when(sms.available()).thenReturn(true);
        String phone="13900000003"; var a=user(phone); var c=recovery.request(a.user().accountName(),phone);
        String code=capturedCode(phone); auth.changePassword(a.user().id(),"Testpass123","Changed123");
        assertThatThrownBy(()->recovery.reset(c.challengeId(),code,"Another123")).isInstanceOf(ApiException.class);
        assertThat(auth.login(a.user().accountName(),"Changed123").user().id()).isEqualTo(a.user().id());
    }

    @Test void expiredChallengeAndRepeatedSendAreRejected() {
        when(sms.available()).thenReturn(true);
        String phone="13900000004"; var a=user(phone); var c=recovery.request(a.user().accountName(),phone);
        String code=capturedCode(phone);
        assertThatThrownBy(()->recovery.request(a.user().accountName(),phone)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(429));
        var entity=resets.findById(c.challengeId()).orElseThrow(); entity.expiresAt=0; resets.saveAndFlush(entity);
        assertThatThrownBy(()->recovery.reset(c.challengeId(),code,"Changed123")).isInstanceOf(ApiException.class);
    }

    @Test void unknownAccountDoesNotSendAndCannotReset() {
        when(sms.available()).thenReturn(true);
        var c=recovery.request("missing_"+UUID.randomUUID(),"13900000005");
        assertThat(c.challengeId()).isNotBlank(); verify(sms,never()).send(anyString(),anyString());
        assertThatThrownBy(()->recovery.reset(c.challengeId(),"123456","Changed123")).isInstanceOf(ApiException.class);
    }

    @Test void mediaRetryIsStableAndPrivateAccessRequiresMembership() throws Exception {
        var a=user(); var b=user(); var outsider=user();
        String cid=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        var asset=media.upload(a.user().id(),image(),cid);
        assertThat(media.upload(a.user().id(),image(),cid).mediaId()).isEqualTo(asset.mediaId());
        assertThat(media.accessible(asset.mediaId(),b.user().id()).mediaId).isEqualTo(asset.mediaId());
        assertThatThrownBy(()->media.accessible(asset.mediaId(),outsider.user().id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->media.upload(outsider.user().id(),image(),cid)).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/public/media/"+asset.mediaId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/media/"+asset.mediaId()).header("Authorization","Bearer "+b.token()))
            .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/media/"+asset.mediaId()).header("Authorization","Bearer "+outsider.token())).andExpect(status().isForbidden());
        var publicAsset=media.upload(a.user().id(),image(),null);
        assertThat(publicAsset.mediaId()).isNotEqualTo(asset.mediaId());
        mvc.perform(get("/api/public/media/"+publicAsset.mediaId())).andExpect(status().isOk());
    }

    @Test void messageAttachmentMustBelongToSenderAndConversation() throws Exception {
        var a=user();var b=user();var c=user();
        String first=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        String second=conversations.createSingleConversation(a.user().id(),c.user().id()).conversationId();
        var asset=media.upload(a.user().id(),image(),first);
        assertThatThrownBy(()->conversations.sendMessage(a.user().id(),second,"wrong-scope","IMAGE",null,asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->conversations.sendMessage(b.user().id(),first,"wrong-owner","IMAGE",null,asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->conversations.sendMessage(a.user().id(),first,"local-uri","IMAGE",null,"content://local/photo")).isInstanceOf(ApiException.class);
        var sent=conversations.sendMessage(a.user().id(),first,"valid-image","IMAGE",null,"https://untrusted.example/"+asset.url());
        assertThat(sent.mediaUrl()).isEqualTo(asset.url());
        assertThat(conversations.sendMessage(a.user().id(),first,"valid-image","IMAGE",null,asset.url()).messageId()).isEqualTo(sent.messageId());
    }

    @Test void forgedOversizedAndPixelBombImagesAreRejected() throws Exception {
        long id=user().user().id();
        assertThatThrownBy(()->media.upload(id,new MockMultipartFile("file","fake.png","image/png","<html>bad</html>".getBytes()),null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->media.upload(id,new MockMultipartFile("file","huge.png","image/png",new byte[8*1024*1024+1]),null)).isInstanceOf(ApiException.class);
        byte[] bomb=image().getBytes();
        java.nio.ByteBuffer.wrap(bomb,16,8).putInt(50000).putInt(50000);
        var crc=new java.util.zip.CRC32(); crc.update(bomb,12,17);
        java.nio.ByteBuffer.wrap(bomb,29,4).putInt((int)crc.getValue());
        assertThatThrownBy(()->media.upload(id,new MockMultipartFile("file","bomb.png","image/png",bomb),null))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getMessage()).contains("megapixels"));
    }
}
