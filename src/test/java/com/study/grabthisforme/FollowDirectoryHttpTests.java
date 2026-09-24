package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.UserFollowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class FollowDirectoryHttpTests {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserFollowService service;
    @Autowired ObjectMapper json;
    @Test
    void myListsAreDirectionalAndAccountScopedAndSupportFollowBack() throws Exception {
        var a = auth.register("dir_" + UUID.randomUUID(), "Testpass123", "A", null, null);
        var b = auth.register("dir_" + UUID.randomUUID(), "Testpass123", "B", null, null);
        String token = "Bearer " + a.token();
        service.setFollowing(b.user().id(), a.user().id(), true);
        mvc.perform(get("/api/users/me/followers").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(b.user().id()))
            .andExpect(jsonPath("$.data.items[0].following").value(false));
        mvc.perform(get("/api/users/me/following").header("Authorization", token))
            .andExpect(jsonPath("$.data.items.length()").value(0));
        service.setFollowing(a.user().id(), b.user().id(), true);
        mvc.perform(get("/api/users/me/followers").header("Authorization", token))
            .andExpect(jsonPath("$.data.items[0].following").value(true));
        mvc.perform(get("/api/users/me/follow-counts").header("Authorization", token))
            .andExpect(jsonPath("$.data.following").value(1)).andExpect(jsonPath("$.data.followers").value(1));
        service.setFollowing(a.user().id(), b.user().id(), false);
        mvc.perform(get("/api/users/me/follow-counts").header("Authorization", token))
            .andExpect(jsonPath("$.data.following").value(0)).andExpect(jsonPath("$.data.followers").value(1));
        mvc.perform(get("/api/users/me/following")).andExpect(status().isUnauthorized());
    }
    @Test
    void cursorDoesNotSkipRemainingPeopleAfterUnfollow() throws Exception {
        var a = auth.register("dir_" + UUID.randomUUID(), "Testpass123", "A", null, null);
        for (int i=0; i<3; i++) {
            var user = auth.register("dir_" + UUID.randomUUID(), "Testpass123", "Person", null, null);
            service.setFollowing(a.user().id(), user.user().id(), true);
        }
        String token = "Bearer " + a.token();
        var result = mvc.perform(get("/api/users/me/following").param("limit", "2").header("Authorization", token))
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.hasMore").value(true)).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).path("data");
        long removed = data.path("items").get(0).path("id").asLong();
        long cursor = data.path("nextBeforeId").asLong();
        service.setFollowing(a.user().id(), removed, false);
        var page = service.list(a.user().id(), false, data.path("nextBeforeTime").asLong(), cursor, 2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isLessThan(cursor);
        assertThat(page.hasMore()).isFalse();
    }
}
