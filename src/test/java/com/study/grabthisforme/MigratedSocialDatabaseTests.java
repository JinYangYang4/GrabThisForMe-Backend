package com.study.grabthisforme;

import com.study.grabthisforme.service.ConversationService;
import com.study.grabthisforme.service.SocialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Opt-in startup verification on an isolated restored database, never the original file. */
@EnabledIfSystemProperty(named="social.rehearsal.url",matches=".+")
@SpringBootTest(properties={
    "spring.datasource.url=${social.rehearsal.url}",
    "spring.jpa.hibernate.ddl-auto=update",
    "grabthisforme.chat.cleanup-enabled=false"
})
class MigratedSocialDatabaseTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConversationService conversations;
    @Autowired SocialService social;

    @Test void migratedDatabaseStartsAndAllLegacyRelationshipsRemainUsable() {
        assertThat(jdbc.queryForObject("select count(*) from message_content",Long.class)).isEqualTo(103);
        assertThat(jdbc.queryForObject("select count(*) from direct_conversation",Long.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select count(*) from conversation",Long.class)).isEqualTo(26);
        var groups=jdbc.queryForList("select group_id,conversation_id from chat_group");
        for(var group:groups) {
            long gid=((Number)group.get("group_id")).longValue();
            var members=jdbc.queryForList("select user_id from conversation_participant where conversation_id=?",Long.class,group.get("conversation_id"));
            for(long uid:members) {
                assertThat(conversations.openGroupConversation(uid,gid).groupId()).isEqualTo(gid);
                assertThat(social.listGroups(uid)).anyMatch(g->g.groupId()==gid);
                assertThat(conversations.listConversations(uid)).anyMatch(c->c.conversationId().equals(group.get("conversation_id")));
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from social_v1_backup_conversation_user_state o join conversation_user_state n on o.conversation_id=n.conversation_id and o.user_id=n.user_id where o.unread_count is distinct from n.unread_count or o.is_hidden is distinct from n.is_hidden or o.last_read_time is distinct from n.last_read_time",Long.class)).isZero();
    }
}
