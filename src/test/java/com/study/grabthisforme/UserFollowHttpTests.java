package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.UserFollowService;
import com.study.grabthisforme.persistence.repository.UserStatisticsRepository;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserFollowHttpTests {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserFollowService follows;
    @Autowired UserStatisticsRepository statistics;

    @Test
    void persistsIndependentRelationAndKeepsRepeatedWritesIdempotent() throws Exception {
        var a = auth.register("follow_" + UUID.randomUUID(), "Testpass123", "A", null, null);
        var b = auth.register("follow_" + UUID.randomUUID(), "Testpass123", "B", null, null);
        String token = "Bearer " + a.token();
        String path = "/api/users/" + b.user().id() + "/follow";
        mvc.perform(get(path).header("Authorization", token)).andExpect(jsonPath("$.data").value(false));
        for (int i = 0; i < 2; i++)
            mvc.perform(put(path).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(get(path).header("Authorization", token)).andExpect(jsonPath("$.data").value(true));
        assertThat(follows.isFollowing(b.user().id(), a.user().id())).isFalse();
        assertThat(statistics.findById(a.user().id()).orElseThrow().followCount).isEqualTo(1L);
        assertThat(statistics.findById(b.user().id()).orElseThrow().fanCount).isEqualTo(1L);
        for (int i = 0; i < 2; i++)
            mvc.perform(delete(path).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
        assertThat(follows.isFollowing(a.user().id(), b.user().id())).isFalse();
        assertThat(statistics.findById(a.user().id()).orElseThrow().followCount).isZero();
        assertThat(statistics.findById(b.user().id()).orElseThrow().fanCount).isZero();
        mvc.perform(put("/api/users/" + a.user().id() + "/follow").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/9223372036854775807/follow").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(put(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentFollowRequestsDoNotDuplicateOrInflateCounts() throws Exception {
        var a = auth.register("follow_" + UUID.randomUUID(), "Testpass123", "A", null, null);
        var b = auth.register("follow_" + UUID.randomUUID(), "Testpass123", "B", null, null);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> follows.setFollowing(a.user().id(), b.user().id(), true));
            for (var result : executor.invokeAll(tasks, 20, TimeUnit.SECONDS)) assertThat(result.get()).isTrue();
            assertThat(statistics.findById(a.user().id()).orElseThrow().followCount).isEqualTo(1L);
            assertThat(statistics.findById(b.user().id()).orElseThrow().fanCount).isEqualTo(1L);
        } finally { executor.shutdownNow(); }
    }
}
