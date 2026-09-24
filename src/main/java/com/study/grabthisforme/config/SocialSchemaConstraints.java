package com.study.grabthisforme.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Database FKs without JPA object associations: entities use scalar IDs and batch queries. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SocialSchemaConstraints implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  public SocialSchemaConstraints(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (String table : new String[] {"conversation_participant", "conversation_user_state"}) {
      foreignKey(table, "conversation", "conversation_id", "conversation", "conversation_id");
      foreignKey(table, "user", "user_id", "user_account", "user_id");
    }
    foreignKey("chat_group", "conversation", "conversation_id", "conversation", "conversation_id");
    foreignKey(
        "direct_conversation",
        "conversation",
        "conversation_id",
        "conversation",
        "conversation_id");
    foreignKey("direct_conversation", "low", "user_low_id", "user_account", "user_id");
    foreignKey("direct_conversation", "high", "user_high_id", "user_account", "user_id");
    foreignKey("user_friend_relation", "owner", "user_id", "user_account", "user_id");
    foreignKey("user_friend_relation", "peer", "friend_user_id", "user_account", "user_id");
    foreignKey("friend_request", "sender", "sender_id", "user_account", "user_id");
    foreignKey("friend_request", "receiver", "receiver_id", "user_account", "user_id");
  }

  private void foreignKey(
      String table, String suffix, String column, String target, String targetColumn) {
    jdbc.execute(
        "ALTER TABLE "
            + table
            + " ADD CONSTRAINT IF NOT EXISTS fk_"
            + table
            + "_"
            + suffix
            + " FOREIGN KEY("
            + column
            + ") REFERENCES "
            + target
            + "("
            + targetColumn
            + ")");
  }
}
