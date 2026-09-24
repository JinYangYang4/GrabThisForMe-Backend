package com.study.grabthisforme;

import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.UserFollowService;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.jpa.properties.hibernate.session_factory.statement_inspector=com.study.grabthisforme.FollowCounterTests$SqlCapture")
class FollowCounterTests {
    public static class SqlCapture implements StatementInspector {
        static final List<String> sql = new CopyOnWriteArrayList<>();
        @Override public String inspect(String statement) { sql.add(statement.toLowerCase(Locale.ROOT)); return statement; }
    }
    @Autowired AuthService auth;
    @Autowired UserFollowService service;
    @Autowired PlatformTransactionManager transactions;
    long user() { return auth.register("count_"+UUID.randomUUID(), "Testpass123", "User", null, null).user().id(); }

    @Test void readsCountersAndUpdatesWithoutAggregatingRelations() {
        long a=user(), b=user();
        SqlCapture.sql.clear();
        service.counts(a);
        assertThat(SqlCapture.sql).noneMatch(sql -> sql.contains("user_follow"));
        SqlCapture.sql.clear();
        service.setFollowing(a,b,true);
        service.setFollowing(a,b,true);
        assertThat(service.counts(a).following()).isEqualTo(1);
        assertThat(service.counts(b).followers()).isEqualTo(1);
        service.setFollowing(a,b,false);
        service.setFollowing(a,b,false);
        assertThat(service.counts(a).following()).isZero();
        assertThat(service.counts(b).followers()).isZero();
        assertThat(SqlCapture.sql).noneMatch(sql -> sql.contains("count(") && sql.contains("user_follow"));
    }

    @Test void relationAndCountersRollBackTogether() {
        long a=user(), b=user();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            service.setFollowing(a,b,true);
            throw new IllegalStateException("rollback probe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.isFollowing(a,b)).isFalse();
        assertThat(service.counts(a).following()).isZero();
        assertThat(service.counts(b).followers()).isZero();
    }

    @Test void distinctConcurrentFollowersDoNotLoseIncrementsOrDecrements() throws Exception {
        long target=user();
        var actors = new ArrayList<Long>();
        for (int i=0;i<8;i++) actors.add(user());
        var executor=Executors.newFixedThreadPool(4);
        try {
            var adds = actors.stream().<Callable<Boolean>>map(id -> () -> service.setFollowing(id,target,true)).toList();
            for (var result:executor.invokeAll(adds,20,TimeUnit.SECONDS)) assertThat(result.get()).isTrue();
            assertThat(service.counts(target).followers()).isEqualTo(8);
            var removes = actors.stream().<Callable<Boolean>>map(id -> () -> service.setFollowing(id,target,false)).toList();
            for (var result:executor.invokeAll(removes,20,TimeUnit.SECONDS)) assertThat(result.get()).isFalse();
            assertThat(service.counts(target).followers()).isZero();
            for (long id: actors) assertThat(service.counts(id).following()).isZero();
        } finally {executor.shutdownNow();}
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void oneTimeRepairRestoresLegacyCountersWithoutChangingLikes() {
        long a=user(), b=user();
        service.setFollowing(a,b,true);
        jdbc.update("update user_statistics set follow_count=99,fan_count=88,like_count=7 where user_id=?", a);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.FileSystemResource("docs/sql/reconcile-follow-counts.sql"))
            .execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        assertThat(service.counts(a).following()).isEqualTo(1);
        assertThat(service.counts(a).followers()).isZero();
        assertThat(service.counts(b).followers()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select like_count from user_statistics where user_id=?", Long.class, a)).isEqualTo(7);
    }
}
