package com.study.grabthisforme;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.service.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
class VideoMediaTests {
    static final Path MEDIA;
    static { try { MEDIA = Files.createTempDirectory("grab-video-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("grabthisforme.media.directory", MEDIA::toString);
    }
    @Autowired AuthService auth;
    @Autowired MediaService media;
    @Autowired ConversationService conversations;
    @Autowired PostService posts;
    @Autowired MockMvc mvc;

    AuthService.AuthResult user() {
        return auth.register("video_" + UUID.randomUUID(), "Testpass123", "Video tester", null, null);
    }
    // Container fixture only: these tests exercise storage/transport, not hardware video decoding.
    byte[] container() {
        return ByteBuffer.allocate(44)
            .putInt(20).put("ftypisom".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0)
            .put("mp42".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
            .putInt(12).put("moov".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(1)
            .putInt(12).put("mdat".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(1).array();
    }
    MockMultipartFile video() { return new MockMultipartFile("file", "clip.mp4", "video/mp4", container()); }

    @Test void privateVideoIsScopedSupportsRangesAndMessageRetryIsIdempotent() throws Exception {
        var a = user(); var b = user(); var outsider = user();
        String cid = conversations.createSingleConversation(a.user().id(), b.user().id()).conversationId();
        String other = conversations.createSingleConversation(a.user().id(), outsider.user().id()).conversationId();
        var asset = media.uploadVideo(a.user().id(), video(), cid);
        assertThat(media.uploadVideo(a.user().id(), video(), cid).mediaId()).isEqualTo(asset.mediaId());
        assertThatThrownBy(() -> media.uploadVideo(outsider.user().id(), video(), cid)).isInstanceOf(ApiException.class);
        mvc.perform(get("/" + asset.url()).header("Authorization", "Bearer " + b.token()).header("Range", "bytes=0-11"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string("Content-Type", "video/mp4"))
            .andExpect(header().string("Content-Range", "bytes 0-11/44"))
            .andExpect(content().bytes(java.util.Arrays.copyOf(container(), 12)));
        mvc.perform(get("/api/public/media/" + asset.mediaId()).header("Range", "bytes=0-11"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/" + asset.url()).header("Authorization", "Bearer " + outsider.token()))
            .andExpect(status().isForbidden());
        assertThatThrownBy(() -> conversations.sendMessage(a.user().id(), other, "wrong", "VIDEO", null, asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> conversations.sendMessage(b.user().id(), cid, "owner", "VIDEO", null, asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> conversations.sendMessage(a.user().id(), cid, "type", "IMAGE", null, asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> media.validatePublicVideo(a.user().id(), "api/public/media/" + asset.mediaId())).isInstanceOf(ApiException.class);
        var sent = conversations.sendMessage(a.user().id(), cid, "video-retry", "VIDEO", null, asset.url());
        assertThat(sent.type()).isEqualTo("VIDEO");
        assertThat(conversations.sendMessage(a.user().id(), cid, "video-retry", "VIDEO", null, asset.url()).messageId()).isEqualTo(sent.messageId());
    }

    @Test void communityVideoSurvivesCreateDetailAndListAndIsPubliclyPlayable() throws Exception {
        var a = user(); var b = user();
        var asset = media.uploadVideo(a.user().id(), video(), null);
        var post = posts.createPost(a.user().id(), "视频帖子", List.of(), asset.url(), "SHARE",
            List.of(), null, null, "", "", "", "", "");
        assertThat(post.videoUrl()).isEqualTo(asset.url());
        assertThat(posts.getPost(post.postId(), b.user().id()).videoUrl()).isEqualTo(asset.url());
        mvc.perform(get("/api/posts").header("Authorization", "Bearer " + b.token()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(asset.url())));
        mvc.perform(get("/" + asset.url()).header("Range", "bytes=12-"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 12-43/44"));
        assertThatThrownBy(() -> media.validatePublicVideo(b.user().id(), asset.url())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> posts.createPost(a.user().id(), "混合", List.of("photo"), asset.url(), "SHARE",
            List.of(), null, null, "", "", "", "", "")).isInstanceOf(ApiException.class);
        mvc.perform(multipart("/api/media/videos").file(video()).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value(asset.url()));
        mvc.perform(multipart("/api/media/videos").file(video())).andExpect(status().isUnauthorized());
    }

    @Test void rejectsForgedTruncatedOversizedAndImageAsVideo() throws Exception {
        long id = user().user().id();
        assertThatThrownBy(() -> media.uploadVideo(id, new MockMultipartFile("file", "x.mp4", "video/mp4", "not video".getBytes()), null))
            .isInstanceOf(ApiException.class);
        byte[] truncated = container(); ByteBuffer.wrap(truncated).putInt(100000);
        assertThatThrownBy(() -> media.uploadVideo(id, new MockMultipartFile("file", "x.mp4", "video/mp4", truncated), null))
            .isInstanceOf(ApiException.class);
        var oversized = new MockMultipartFile("file", "x.mp4", "video/mp4", container()) {
            @Override public long getSize() { return MediaService.MAX_VIDEO_BYTES + 1; }
        };
        assertThatThrownBy(() -> media.uploadVideo(id, oversized, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> media.upload(id, video(), null)).isInstanceOf(ApiException.class);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        var image = media.upload(id, new MockMultipartFile("file", "i.png", "image/png", png.toByteArray()), null);
        assertThatThrownBy(() -> media.validatePublicVideo(id, image.url())).isInstanceOf(ApiException.class);
        try (var paths = Files.list(MEDIA)) { assertThat(paths.noneMatch(p -> p.toString().endsWith(".upload"))).isTrue(); }
    }
}
