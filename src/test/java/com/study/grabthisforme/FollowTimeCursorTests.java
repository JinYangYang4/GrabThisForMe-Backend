package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.UserFollowService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FollowTimeCursorTests {
    @Autowired AuthService auth;
    @Autowired UserFollowService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Test
    void timeOrderAndTiesSurviveRemovalAndNewFollowInBothDirections() throws Exception {
        var viewer = auth.register("cursor_" + UUID.randomUUID(), "Testpass123", "Viewer", null, null);
        long me = viewer.user().id();
        String token = "Bearer " + viewer.token();
        var ids = new ArrayList<Long>();
        long[] outgoing = {4000, 3000, 3000, 1000};
        long[] incoming = {1000, 2000, 4000, 3000};
        for (int i=0; i<4; i++) {
            long id = auth.register("cursor_" + UUID.randomUUID(), "Testpass123", "Person", null, null).user().id();
            ids.add(id);
            service.setFollowing(me, id, true);
            service.setFollowing(id, me, true);
            jdbc.update("update user_follow set created_at=? where user_id=? and target_id=?", outgoing[i], me, id);
            jdbc.update("update user_follow set created_at=? where user_id=? and target_id=?", incoming[i], id, me);
        }
        var first = service.list(me, false, null, null, 2);
        assertThat(first.items().stream().map(UserFollowService.FollowUser::id).toList()).containsExactly(ids.get(0), ids.get(2));
        assertThat(first.nextBeforeTime()).isEqualTo(3000L);
        mvc.perform(get("/api/users/me/following").param("beforeTime", "3000").param("beforeId", ids.get(2).toString()).param("limit", "2").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(ids.get(1)))
            .andExpect(jsonPath("$.data.items[0].mutual").value(true)).andExpect(jsonPath("$.data.items[0].followedAt").value(3000));
        service.setFollowing(me, ids.get(0), false);
        service.setFollowing(me, ids.get(0), true); // New event belongs above the existing cursor.
        var second = service.list(me, false, first.nextBeforeTime(), first.nextBeforeId(), 2);
        assertThat(second.items().stream().map(UserFollowService.FollowUser::id).toList()).containsExactly(ids.get(1), ids.get(3));
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextBeforeTime()).isNull();
        assertThat(service.list(me, false, null, null, 1).items().get(0).id()).isEqualTo(ids.get(0));
        var fans = service.list(me, true, null, null, 2);
        assertThat(fans.items().stream().map(UserFollowService.FollowUser::id).toList()).containsExactly(ids.get(2), ids.get(3));
        assertThat(service.list(me, true, fans.nextBeforeTime(), fans.nextBeforeId(), 2).items().stream().map(UserFollowService.FollowUser::id).toList())
            .containsExactly(ids.get(1), ids.get(0));
        mvc.perform(delete("/api/users/" + ids.get(2) + "/follow/relationship").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.following").value(false))
            .andExpect(jsonPath("$.data.followsMe").value(true)).andExpect(jsonPath("$.data.mutual").value(false));
        mvc.perform(put("/api/users/" + ids.get(2) + "/follow/relationship").header("Authorization", token))
            .andExpect(jsonPath("$.data.mutual").value(true));
        mvc.perform(get("/api/users/me/following").param("beforeTime", "3000").header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me/followers").param("beforeId", "1").header("Authorization", token))
            .andExpect(status().isBadRequest());
    }
}
