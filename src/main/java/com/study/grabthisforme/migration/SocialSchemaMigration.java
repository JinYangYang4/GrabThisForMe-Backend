package com.study.grabthisforme.migration;

import java.sql.*;
import java.util.*;

/**
 * Explicit offline migration. Run only against a verified backup first; never runs at app startup.
 */
public final class SocialSchemaMigration {
  private final Connection db;

  public SocialSchemaMigration(Connection db) {
    this.db = db;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Set.of("audit", "apply").contains(args[1]))
      throw new IllegalArgumentException(
          "Usage: <jdbc-url> audit|apply (credentials via DB_USER/DB_PASSWORD)");
    try (var c =
        DriverManager.getConnection(
            args[0],
            System.getenv().getOrDefault("DB_USER", "sa"),
            System.getenv().getOrDefault("DB_PASSWORD", ""))) {
      var migration = new SocialSchemaMigration(c);
      System.out.println(migration.audit());
      if (args[1].equals("apply")) {
        migration.apply();
        System.out.println("social-v1 verified");
      }
    }
  }

  private long count(String sql) throws SQLException {
    try (var s = db.createStatement();
        var r = s.executeQuery(sql)) {
      r.next();
      return r.getLong(1);
    }
  }

  private List<Map<String, Object>> rows(String sql) throws SQLException {
    var result = new ArrayList<Map<String, Object>>();
    try (var s = db.createStatement();
        var r = s.executeQuery(sql)) {
      while (r.next()) {
        var row = new HashMap<String, Object>();
        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
          row.put(r.getMetaData().getColumnLabel(i).toLowerCase(), r.getObject(i));
        result.add(row);
      }
    }
    return result;
  }

  private void sql(String sql, Object... values) throws SQLException {
    try (var s = db.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
      s.execute();
    }
  }

  private boolean column(String table, String column) throws SQLException {
    return count(
            "select count(*) from information_schema.columns where table_name='"
                + table.toUpperCase()
                + "' and column_name='"
                + column.toUpperCase()
                + "'")
        > 0;
  }

  private boolean table(String name) throws SQLException {
    return count(
            "select count(*) from information_schema.tables where table_schema='PUBLIC' and"
                + " table_name='"
                + name.toUpperCase()
                + "'")
        > 0;
  }

  private boolean done() throws SQLException {
    return table("social_schema_version")
        && count("select count(*) from social_schema_version where version=1") > 0;
  }

  public Map<String, Long> audit() throws SQLException {
    var result = new LinkedHashMap<String, Long>();
    for (var t :
        List.of(
            "conversation",
            "conversation_participant",
            "conversation_user_state",
            "message_content",
            "chat_group",
            "user_friend_relation")) result.put(t, count("select count(*) from " + t));
    if (done()) {
      result.put("version", 1L);
      return result;
    }
    var problems = new ArrayList<String>();
    check(
        problems,
        "invalid direct members",
        "select count(*) from (select c.conversation_id from conversation c left join"
            + " conversation_participant p on c.conversation_id=p.conversation_id where"
            + " c.conversation_type='SINGLE' group by c.conversation_id having count(distinct"
            + " p.user_id)<>2)");
    check(
        problems,
        "duplicate direct pairs",
        "select count(*) from (select lo,hi from (select c.conversation_id,min(p.user_id)"
            + " lo,max(p.user_id) hi from conversation c join conversation_participant p on"
            + " c.conversation_id=p.conversation_id where c.conversation_type='SINGLE' group by"
            + " c.conversation_id) group by lo,hi having count(*)>1)");
    check(
        problems,
        "duplicate group conversations",
        "select count(*) from (select target_id from conversation where conversation_type='GROUP'"
            + " group by target_id having count(*)>1)");
    check(
        problems,
        "group owners",
        "select count(*) from (select g.group_id from chat_group g left join user_group_relation r"
            + " on r.group_id=g.group_id group by g.group_id having sum(case when r.role='OWNER'"
            + " then 1 else 0 end)<>1)");
    check(
        problems,
        "dangling group conversation",
        "select count(*) from conversation c left join chat_group g on g.group_id=c.target_id where"
            + " c.conversation_type='GROUP' and g.group_id is null");
    check(
        problems,
        "orphan messages",
        "select count(*) from message_content m left join conversation c on"
            + " c.conversation_id=m.conversation_id where c.conversation_id is null");
    check(
        problems,
        "orphan participants",
        "select count(*) from conversation_participant p left join conversation c on"
            + " c.conversation_id=p.conversation_id left join user_account u on u.user_id=p.user_id"
            + " where c.conversation_id is null or u.user_id is null");
    check(
        problems,
        "orphan group members",
        "select count(*) from user_group_relation r left join chat_group g on r.group_id=g.group_id"
            + " left join user_account u on r.user_id=u.user_id where g.group_id is null or"
            + " u.user_id is null");
    check(
        problems,
        "orphan states",
        "select count(*) from conversation_user_state s left join conversation c on"
            + " c.conversation_id=s.conversation_id left join user_account u on u.user_id=s.user_id"
            + " where c.conversation_id is null or u.user_id is null");
    check(
        problems,
        "unpaired pending requests",
        "select count(*) from user_friend_relation a left join user_friend_relation b on"
            + " a.user_id=b.friend_user_id and a.friend_user_id=b.user_id where"
            + " (a.status='PENDING_SENT' and (b.status is null or b.status<>'PENDING_RECEIVED')) or"
            + " (a.status='PENDING_RECEIVED' and (b.status is null or b.status<>'PENDING_SENT'))");
    result.put(
        "states_without_membership_preserved",
        count(
            "select count(*) from conversation_user_state s left join conversation_participant p on"
                + " p.conversation_id=s.conversation_id and p.user_id=s.user_id where p.user_id is"
                + " null"));
    if (!problems.isEmpty())
      throw new IllegalStateException(
          "Migration stopped before writes; explicit repair mapping needed: " + problems);
    return result;
  }

  private void check(List<String> errors, String label, String query) throws SQLException {
    long n = count(query);
    if (n > 0) errors.add(label + "=" + n);
  }

  public void apply() throws SQLException {
    if (done()) return;
    audit();
    // H2 DDL commits implicitly. A failed migration must be restored from the offline backup.
    for (var t :
        List.of(
            "conversation",
            "conversation_participant",
            "conversation_user_state",
            "chat_group",
            "user_friend_relation",
            "user_group_relation"))
      sql("create table if not exists social_v1_backup_" + t + " as select * from " + t);
    sql(
        "create table if not exists social_schema_version(version integer primary key, applied_at"
            + " bigint not null)");
    sql("alter table conversation add column if not exists created_at bigint");
    sql(
        "update conversation c set created_at=coalesce((select min(p.joined_at) from"
            + " conversation_participant p where"
            + " p.conversation_id=c.conversation_id),c.last_time,0) where created_at is null");
    sql(
        "create table if not exists direct_conversation(conversation_id varchar(255) primary key"
            + " references conversation(conversation_id),user_low_id bigint not null references"
            + " user_account(user_id),user_high_id bigint not null references"
            + " user_account(user_id),constraint uk_direct_pair"
            + " unique(user_low_id,user_high_id),constraint ck_direct_order"
            + " check(user_low_id<user_high_id))");
    sql(
        "insert into direct_conversation select c.conversation_id,min(p.user_id),max(p.user_id)"
            + " from conversation c join conversation_participant p on"
            + " p.conversation_id=c.conversation_id where c.conversation_type='SINGLE' and not"
            + " exists(select 1 from direct_conversation d where"
            + " d.conversation_id=c.conversation_id) group by c.conversation_id");
    sql("alter table chat_group add column if not exists conversation_id varchar(255)");
    sql("alter table chat_group add column if not exists creation_key varchar(255)");
    sql("alter table chat_group add column if not exists creation_fingerprint varchar(10000)");
    for (var g : rows("select * from chat_group")) {
      long gid = ((Number) g.get("group_id")).longValue();
      var found =
          rows(
              "select conversation_id from conversation where conversation_type='GROUP' and"
                  + " target_id="
                  + gid);
      String cid =
          found.isEmpty()
              ? "GROUP_MIGRATED_" + gid
              : found.get(0).get("conversation_id").toString();
      if (found.isEmpty())
        sql(
            "insert into"
                + " conversation(conversation_id,conversation_type,last_time,created_at,target_id)"
                + " values(?,'GROUP',?,?,?)",
            cid,
            g.get("create_time"),
            g.get("create_time"),
            gid);
      sql("update chat_group set conversation_id=? where group_id=?", cid, gid);
      // Archive captures obsolete participant rows; personal states remain untouched.
      sql("delete from conversation_participant where conversation_id=?", cid);
      int index = 0;
      for (var m :
          rows(
              "select * from user_group_relation where group_id="
                  + gid
                  + " order by joined_time,user_id")) {
        Object uid = m.get("user_id");
        sql(
            "insert into"
                + " conversation_participant(id,conversation_id,user_id,role,joined_at,sort_order)"
                + " values(?,?,?,?,?,?)",
            cid + ":" + uid,
            cid,
            uid,
            m.get("role"),
            m.get("joined_time"),
            index++);
      }
    }
    sql(
        "update conversation_participant set role='MEMBER' where conversation_id in(select"
            + " conversation_id from direct_conversation)");
    sql(
        "insert into"
            + " conversation_user_state(id,conversation_id,user_id,unread_count,is_hidden,last_read_time)"
            + " select p.conversation_id||':'||p.user_id,p.conversation_id,p.user_id,0,false,null"
            + " from conversation_participant p where not exists(select 1 from"
            + " conversation_user_state s where s.conversation_id=p.conversation_id and"
            + " s.user_id=p.user_id)");
    sql(
        "create table if not exists friend_request(request_id varchar(255) primary key,sender_id"
            + " bigint not null references user_account(user_id),receiver_id bigint not null"
            + " references user_account(user_id),status varchar(255) not null,created_at bigint not"
            + " null,handled_at bigint,pending_pair varchar(255) unique,constraint ck_request_users"
            + " check(sender_id<>receiver_id))");
    sql(
        "insert into"
            + " friend_request(request_id,sender_id,receiver_id,status,created_at,pending_pair)"
            + " select"
            + " 'legacy:'||user_id||':'||friend_user_id,user_id,friend_user_id,'PENDING',coalesce(added_time,0),least(user_id,friend_user_id)||':'||greatest(user_id,friend_user_id)"
            + " from user_friend_relation where status='PENDING_SENT'");
    // Rejected direction/time cannot be inferred reliably: retain exact original rows in backup.
    sql("delete from user_friend_relation where status<>'FRIEND' or status is null");
    for (var t :
        List.of("conversation_participant", "conversation_user_state", "user_friend_relation")) {
      String keys =
          t.equals("user_friend_relation") ? "user_id,friend_user_id" : "conversation_id,user_id";
      sql("alter table " + t + " drop primary key");
      sql("alter table " + t + " alter column id drop not null");
      for (String key : keys.split(","))
        sql("alter table " + t + " alter column " + key + " set not null");
      sql("alter table " + t + " add primary key(" + keys + ")");
      sql(
          "create index if not exists idx_"
              + t
              + "_user on "
              + t
              + "(user_id"
              + (t.equals("user_friend_relation") ? ",friend_user_id" : ",conversation_id")
              + ")");
    }
    sql("alter table chat_group alter column conversation_id set not null");
    sql("alter table chat_group add constraint uk_group_conversation unique(conversation_id)");
    sql("alter table chat_group add constraint uk_group_creation unique(creation_key)");
    sql(
        "alter table chat_group add constraint fk_group_conversation foreign key(conversation_id)"
            + " references conversation(conversation_id)");
    for (var t : List.of("conversation_participant", "conversation_user_state")) {
      sql(
          "alter table "
              + t
              + " add constraint fk_"
              + t
              + "_conversation foreign key(conversation_id) references"
              + " conversation(conversation_id)");
      sql(
          "alter table "
              + t
              + " add constraint fk_"
              + t
              + "_user foreign key(user_id) references user_account(user_id)");
    }
    sql(
        "create index if not exists idx_request_sender on"
            + " friend_request(sender_id,created_at,request_id)");
    sql(
        "create index if not exists idx_request_receiver on"
            + " friend_request(receiver_id,created_at,request_id)");
    sql(
        "create index if not exists idx_message_page on"
            + " message_content(conversation_id,timestamp,message_id)");
    sql("insert into social_schema_version values(1,?)", System.currentTimeMillis());
  }
}
