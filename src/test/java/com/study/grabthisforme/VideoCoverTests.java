package com.study.grabthisforme;

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
class VideoCoverTests {
    static final Path DIRECTORY;
    static { try { DIRECTORY = Files.createTempDirectory("video-cover-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("grabthisforme.media.directory", DIRECTORY::toString); }
    @Autowired AuthService auth;
    @Autowired MediaService media;
    @Autowired ConversationService conversations;
    @Autowired MockMvc mvc;
    AuthService.AuthResult user() { return auth.register("cover_"+UUID.randomUUID(),"Testpass123","Tester",null,null); }
    MockMultipartFile video() {
        byte[] bytes=ByteBuffer.allocate(44).putInt(20).put("ftypisom".getBytes()).putInt(0).put("mp42".getBytes())
            .putInt(12).put("moov".getBytes()).putInt(1).putInt(12).put("mdat".getBytes()).putInt(1).array();
        return new MockMultipartFile("file","v.mp4","video/mp4",bytes);
    }
    MockMultipartFile cover() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB),"jpeg",out);
        return new MockMultipartFile("cover","cover.jpg","image/jpeg",out.toByteArray());
    }
    @Test void coverUsesVideoPermissionsAndDoesNotAlterVideoBytes() throws Exception {
        var a=user(); var b=user(); var outsider=user();
        String cid=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        var uploaded=media.uploadVideo(a.user().id(),video(),cid,cover());
        assertThat(Files.readAllBytes(media.file(media.accessible(uploaded.mediaId(),a.user().id())))).isEqualTo(video().getBytes());
        String url="/"+uploaded.url()+"/cover";
        mvc.perform(get(url).header("Authorization","Bearer "+b.token())).andExpect(status().isOk())
            .andExpect(header().string("Content-Type","image/jpeg")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization","Bearer "+outsider.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/media/"+uploaded.mediaId()+"/cover")).andExpect(status().isUnauthorized());
        mvc.perform(get("/"+uploaded.url()).header("Authorization","Bearer "+b.token()).header("Range","bytes=0-11"))
            .andExpect(status().isPartialContent());
    }
    @Test void oldVideoCanGainCoverOnRetryAndOldClientsRemainSupported() throws Exception {
        var a=user();
        var uploaded=media.uploadVideo(a.user().id(),video(),null);
        mvc.perform(get("/"+uploaded.url()+"/cover")).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/media/videos").file(video()).file(cover()).header("Authorization","Bearer "+a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.mediaId").value(uploaded.mediaId()));
        mvc.perform(get("/"+uploaded.url()+"/cover")).andExpect(status().isOk()).andExpect(header().string("Content-Type","image/jpeg"));
        assertThat(media.uploadVideo(a.user().id(),video(),null).mediaId()).isEqualTo(uploaded.mediaId());
        try (var files=Files.list(DIRECTORY)) { assertThat(files.noneMatch(p->p.toString().endsWith(".upload"))).isTrue(); }
    }
    @Test void forgedCoverIsRejected() throws Exception {
        var a=user();
        mvc.perform(multipart("/api/media/videos").file(video())
            .file(new MockMultipartFile("cover","cover.jpg","image/jpeg","not-image".getBytes()))
            .header("Authorization","Bearer "+a.token())).andExpect(status().isBadRequest());
    }
}
