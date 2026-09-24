package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.PostService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PostLikePersistenceTests {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PostService posts;
    @Test
    void reopeningDetailRestoresLikeAndUnlikeWithoutDuplicatingCounts() throws Exception {
        var a = auth.register("like_" + UUID.randomUUID(), "Testpass123", "A", null, null);
        var b = auth.register("like_" + UUID.randomUUID(), "Testpass123", "B", null, null);
        var post = posts.createPost(a.user().id(), "like persistence", List.of(), "SHARE", List.of(), null, null, "", "", "", "", "");
        String path = "/api/posts/" + post.postId();
        String token = "Bearer " + a.token();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(path + "/like").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
            mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likedByCurrentUser").value(true)).andExpect(jsonPath("$.data.likeCount").value(1));
        }
        mvc.perform(get(path).header("Authorization", "Bearer " + b.token()))
            .andExpect(jsonPath("$.data.likedByCurrentUser").value(false));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(path + "/like").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"liked\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
            mvc.perform(get(path).header("Authorization", token))
                .andExpect(jsonPath("$.data.likedByCurrentUser").value(false)).andExpect(jsonPath("$.data.likeCount").value(0));
        }
    }
}
