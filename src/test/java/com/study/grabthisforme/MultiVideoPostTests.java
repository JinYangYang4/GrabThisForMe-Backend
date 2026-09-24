package com.study.grabthisforme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.persistence.repository.PostRepository;
import com.study.grabthisforme.service.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MultiVideoPostTests {
    static final Path MEDIA;
    static { try { MEDIA=Files.createTempDirectory("multi-video-post-test-"); }
        catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("grabthisforme.media.directory",MEDIA::toString); }
    @Autowired AuthService auth;
    @Autowired MediaService media;
    @Autowired PostService posts;
    @Autowired ConversationService conversations;
    @Autowired PostRepository repository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    AuthService.AuthResult user() { return auth.register("multi_"+UUID.randomUUID(),"Testpass123","Tester",null,null); }
    MockMultipartFile video(int n) {
        var bytes=ByteBuffer.allocate(44).putInt(20).put("ftypisom".getBytes()).putInt(0).put("mp42".getBytes())
            .putInt(12).put("moov".getBytes()).putInt(1).putInt(12).put("mdat".getBytes()).putInt(n).array();
        return new MockMultipartFile("file","video.mp4","video/mp4",bytes);
    }
    @Test void publishesOrderedVideosAndReturnsThemInDetailListAndSearch() throws Exception {
        var a=user();
        var first=media.uploadVideo(a.user().id(),video(1),null);
        var second=media.uploadVideo(a.user().id(),video(2),null);
        String body=json.writeValueAsString(Map.of("content","多视频检索样例","categoryKey","SHARE",
            "videoUrl",first.url(),"videoUrls",List.of(second.url(),first.url(),second.url())));
        var response=mvc.perform(post("/api/posts").contentType("application/json").header("Authorization","Bearer "+a.token()).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.videoUrls.length()").value(2))
            .andExpect(jsonPath("$.data.videoUrls[0]").value(second.url()))
            .andExpect(jsonPath("$.data.videoUrl").value(second.url())).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("data").path("postId").asText();
        assertThat(posts.getPost(id,a.user().id()).videoUrls()).containsExactly(second.url(),first.url());
        assertThat(repository.findById(id).orElseThrow().videoUrlsJson).contains(first.url(),second.url());
        mvc.perform(get("/api/posts/"+id).header("Authorization","Bearer "+a.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.videoUrls[1]").value(first.url()));
        mvc.perform(get("/api/posts").header("Authorization","Bearer "+a.token())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("videoUrls")));
        mvc.perform(get("/api/posts/search").param("keyword","多视频检索样例").header("Authorization","Bearer "+a.token()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(second.url())));
    }
    @Test void legacySingleVideoAndLegacyDatabaseRowsStillProduceAPlaylist() throws Exception {
        var a=user(); var asset=media.uploadVideo(a.user().id(),video(3),null);
        var created=posts.createPost(a.user().id(),"旧客户端",List.of(),asset.url(),"SHARE",List.of(),null,null,"","","","","");
        assertThat(created.videoUrls()).containsExactly(asset.url());
        var row=repository.findById(created.postId()).orElseThrow(); row.videoUrlsJson=null; repository.saveAndFlush(row);
        assertThat(posts.getPost(created.postId(),a.user().id()).videoUrls()).containsExactly(asset.url());
    }
    @Test void rejectsOverLimitAndBlankMediaWithoutCreatingPost() throws Exception {
        var a=user(); var asset=media.uploadVideo(a.user().id(),video(4),null);
        long before=repository.count();
        for (var request:List.of(
            Map.of("content","超限","videoUrls",Collections.nCopies(10,asset.url())),
            Map.of("content","空地址","videoUrls",List.of("")),
            Map.of("content","混合","images",Collections.nCopies(9,"image"),"videoUrls",List.of(asset.url())))) {
            mvc.perform(post("/api/posts").header("Authorization","Bearer "+a.token()).contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isEqualTo(before);
    }
    @Test void mixedMediaSurvivesPublishAndReload() throws Exception {
        var a=user(); var asset=media.uploadVideo(a.user().id(),video(8),null);
        var response=mvc.perform(post("/api/posts").header("Authorization","Bearer "+a.token())
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "content","Mixed media","images",List.of("image-a","image-b"),"videoUrls",List.of(asset.url())))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.images.length()").value(2))
            .andExpect(jsonPath("$.data.videoUrls[0]").value(asset.url())).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("data").path("postId").asText();
        mvc.perform(get("/api/posts/"+id).header("Authorization","Bearer "+a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.images[1]").value("image-b"))
            .andExpect(jsonPath("$.data.videoUrls[0]").value(asset.url()));
    }
    @Test void everyVideoMustBelongToAuthorAndBePublic() throws Exception {
        var a=user(); var b=user();
        var mine=media.uploadVideo(a.user().id(),video(5),null);
        var other=media.uploadVideo(b.user().id(),video(6),null);
        var cid=conversations.createSingleConversation(a.user().id(),b.user().id()).conversationId();
        var privateVideo=media.uploadVideo(a.user().id(),video(7),cid);
        long before=repository.count();
        for (String rejected:List.of(other.url(),"api/public/media/"+privateVideo.mediaId())) {
            mvc.perform(post("/api/posts").header("Authorization","Bearer "+a.token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("content","权限校验","videoUrls",List.of(mine.url(),rejected)))))
                .andExpect(status().isForbidden());
        }
        assertThat(repository.count()).isEqualTo(before);
    }
}
