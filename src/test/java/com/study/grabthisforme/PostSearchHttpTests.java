package com.study.grabthisforme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.repository.PostRepository;
import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.PostService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PostSearchHttpTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired PostService posts;
    @Autowired PostRepository repository;
    String token;
    long userId;
    String keyword;

    @BeforeEach
    void setUp() {
        var account = auth.register("search_" + UUID.randomUUID(), "Testpass123", "Searcher", null, null);
        token = "Bearer " + account.token();
        userId = account.user().id();
        keyword = UUID.randomUUID().toString();
    }

    PostEntity post(String content, String category, List<String> tags, Double lat, Double lon) {
        var created = posts.createPost(userId, content, List.of(), category, tags, lat, lon, "", "", "", "", "");
        return repository.findById(created.postId()).orElseThrow();
    }

    @Test
    void searchesContentAndTagsAndCombinesCategoryWithoutDuplicateRows() throws Exception {
        String tag = "标签" + UUID.randomUUID().toString().substring(0, 5);
        var first = post(tag + " " + keyword, "SHARE", List.of(tag), null, null);
        var second = post(keyword, "FUNNY", List.of(tag), null, null);
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", "  " + tag + "  ")
                .param("categoryKey", "share"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].postId").value(first.postId));
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword.toUpperCase()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        // A tag-only hit must be returned even when the body does not contain that tag.
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", tag).param("categoryKey", "FUNNY"))
            .andExpect(jsonPath("$.data.items[0].postId").value(second.postId));
    }

    @Test
    void paginatesSameTimestampWithoutMissingOrRepeatingPosts() throws Exception {
        var a = post(keyword, "SHARE", List.of(), null, null);
        var b = post(keyword, "SHARE", List.of(), null, null);
        a.createTime = b.createTime = 1700000000000L;
        repository.saveAll(List.of(a, b));
        var response = mvc.perform(get("/api/posts/search").header("Authorization", token)
                .param("keyword", keyword).param("limit", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(true)).andReturn();
        var page = json.readTree(response.getResponse().getContentAsString()).path("data");
        String firstId = page.path("items").get(0).path("postId").asText();
        var next = mvc.perform(get("/api/posts/search").header("Authorization", token)
                .param("keyword", keyword).param("limit", "1")
                .param("beforeTime", page.path("nextBeforeTime").asText())
                .param("beforeId", page.path("nextBeforeId").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.hasMore").value(false)).andReturn();
        assertThat(json.readTree(next.getResponse().getContentAsString()).path("data").path("items").get(0)
            .path("postId").asText()).isNotEqualTo(firstId);
    }

    @Test
    void treatsSqlWildcardsLiterallyAndReturnsRealEmptyResults() throws Exception {
        var literal = post(keyword + "%_!", "SHARE", List.of(), null, null);
        post(keyword + "other", "SHARE", List.of(), null, null);
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword + "%_!"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].postId").value(literal.postId));
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword + "missing"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void nearbyFiltersBeforePaginationAndExcludesMissingCoordinates() throws Exception {
        var near = post(keyword, "SHARE", List.of(), 23.129, 113.264);
        post(keyword, "SHARE", List.of(), 39.904, 116.407);
        post(keyword, "SHARE", List.of(), null, null);
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword)
                .param("latitude", "23.13").param("longitude", "113.26").param("limit", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].postId").value(near.postId))
            .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void rejectsUnauthenticatedAndMalformedRequests() throws Exception {
        mvc.perform(get("/api/posts/search").param("keyword", keyword)).andExpect(status().isUnauthorized());
        for (String value : List.of(" ", "x".repeat(81))) {
            mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", value))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword)
                .param("beforeTime", "123")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/search").header("Authorization", token).param("keyword", keyword)
                .param("latitude", "91").param("longitude", "0")).andExpect(status().isBadRequest());
    }
}
