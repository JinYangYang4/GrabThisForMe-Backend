package com.study.grabthisforme;

import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.entity.MediaEntity;
import com.study.grabthisforme.persistence.repository.MediaRepository;
import com.study.grabthisforme.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
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
class ImageMediaCompatibilityTests {
    static final Path DIRECTORY;
    static { try { DIRECTORY = Files.createTempDirectory("image-compat-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("grabthisforme.media.directory", DIRECTORY::toString);
    }
    @Autowired AuthService auth;
    @Autowired MediaService media;
    @Autowired MediaRepository database;
    @Autowired ConversationService conversations;
    @Autowired PostService posts;
    @Autowired MockMvc mvc;
    AuthService.AuthResult user() { return auth.register("image_"+UUID.randomUUID(),"Testpass123","Tester",null,null); }
    byte[] picture(String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),format,out);
        return out.toByteArray();
    }
    @Test void jpegStoragePrivateMessagePublicPostAndDedupRemainCompatible() throws Exception {
        var a=user(); var b=user(); var outsider=user();
        String cid=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        var file=new MockMultipartFile("file","misleading.png","image/png",picture("jpeg"));
        var uploaded=media.upload(a.user().id(),file,cid);
        var asset=media.accessible(uploaded.mediaId(),a.user().id());
        assertThat(asset.contentType).isEqualTo("image/jpeg");
        assertThat(media.file(asset).toString()).endsWith(".jpg");
        assertThat(Files.size(media.file(asset))).isEqualTo(asset.byteSize);
        assertThat(media.upload(a.user().id(),file,cid).mediaId()).isEqualTo(uploaded.mediaId());
        mvc.perform(get("/"+uploaded.url()).header("Authorization","Bearer "+b.token()))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type","image/jpeg"));
        mvc.perform(get("/"+uploaded.url()).header("Authorization","Bearer "+outsider.token())).andExpect(status().isForbidden());
        var sent=conversations.sendMessage(a.user().id(),cid,"jpeg-message","IMAGE",null,uploaded.url());
        assertThat(sent.mediaUrl()).isEqualTo(uploaded.url());
        assertThat(conversations.sendMessage(a.user().id(),cid,"jpeg-message","IMAGE",null,uploaded.url()).messageId()).isEqualTo(sent.messageId());
        assertThatThrownBy(()->conversations.sendMessage(b.user().id(),cid,"wrong-owner","IMAGE",null,uploaded.url())).isInstanceOf(ApiException.class);
        var publicImage=media.upload(a.user().id(),file,null);
        var post=posts.createPost(a.user().id(),"图片帖子",List.of(publicImage.url()),null,"SHARE",List.of(),null,null,"","","","","");
        assertThat(posts.getPost(post.postId(),b.user().id()).images()).contains(publicImage.url());
        mvc.perform(get("/"+publicImage.url())).andExpect(status().isOk()).andExpect(header().string("Content-Type","image/jpeg"));
    }
    @Test void existingPngRowAndFileStillLoadAndCanBeSent() throws Exception {
        var a=user(); var b=user();
        String cid=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        var legacy=new MediaEntity();
        legacy.mediaId=UUID.randomUUID().toString(); legacy.ownerId=a.user().id(); legacy.conversationId=cid;
        legacy.contentType="image/png"; legacy.createdTime=System.currentTimeMillis();
        byte[] bytes=picture("png"); legacy.byteSize=(long)bytes.length;
        Files.write(DIRECTORY.resolve(legacy.mediaId+".png"),bytes);
        database.saveAndFlush(legacy);
        mvc.perform(get("/api/media/"+legacy.mediaId).header("Authorization","Bearer "+b.token()))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type","image/png")).andExpect(content().bytes(bytes));
        assertThat(conversations.sendMessage(a.user().id(),cid,"legacy-image","IMAGE",null,"api/media/"+legacy.mediaId).mediaUrl())
            .isEqualTo("api/media/"+legacy.mediaId);
    }
}
