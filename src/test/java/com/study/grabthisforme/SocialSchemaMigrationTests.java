package com.study.grabthisforme;
import com.study.grabthisforme.migration.SocialSchemaMigration;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SocialSchemaMigrationTests {
 Connection legacy() throws Exception {
  var c=DriverManager.getConnection("jdbc:h2:mem:migration_"+UUID.randomUUID()+";MODE=MySQL","sa","");
  String[] sql={
   "create table user_account(user_id bigint primary key)","insert into user_account values(1),(2),(3)",
   "create table conversation(conversation_id varchar primary key,conversation_type varchar,target_id bigint,last_message_id varchar,last_time bigint)",
   "insert into conversation values('C','SINGLE',2,'M',100)",
   "create table conversation_participant(id varchar primary key,conversation_id varchar,user_id bigint,role varchar,joined_at bigint,sort_order int)",
   "insert into conversation_participant values('C:1','C',1,'',10,0),('C:2','C',2,'',20,1)",
   "create table conversation_user_state(id varchar primary key,conversation_id varchar,user_id bigint,unread_count int,is_hidden boolean,last_read_time bigint)",
   "insert into conversation_user_state values('C:1','C',1,7,true,80),('C:2','C',2,0,false,100)",
   "create table message_content(message_id varchar primary key,conversation_id varchar,timestamp bigint)","insert into message_content values('M','C',100)",
   "create table chat_group(group_id bigint primary key,group_name varchar,create_time bigint)","insert into chat_group values(9,'group',30)",
   "create table user_group_relation(id varchar primary key,group_id bigint,user_id bigint,role varchar,joined_time bigint)","insert into user_group_relation values('1:9',9,1,'OWNER',30),('3:9',9,3,'MEMBER',40)",
   "create table user_friend_relation(id varchar primary key,user_id bigint,friend_user_id bigint,status varchar,added_time bigint)","insert into user_friend_relation values('1:2',1,2,'FRIEND',5),('2:1',2,1,'FRIEND',5),('1:3',1,3,'PENDING_SENT',9),('3:1',3,1,'PENDING_RECEIVED',9)"};
  try(var s=c.createStatement()){for(var q:sql)s.execute(q);}return c;
 }
 long scalar(Connection c,String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
 @Test void retainsMessagesStatesRolesAndRequestDirectionAndCanRunTwice() throws Exception {
  try(var c=legacy()) {var m=new SocialSchemaMigration(c);m.apply();m.apply();
   assertThat(scalar(c,"select count(*) from message_content")).isEqualTo(1);
   assertThat(scalar(c,"select unread_count from conversation_user_state where conversation_id='C' and user_id=1 and is_hidden=true")).isEqualTo(7);
   assertThat(scalar(c,"select joined_at from conversation_participant where conversation_id='GROUP_MIGRATED_9' and user_id=1 and role='OWNER'")).isEqualTo(30);
   assertThat(scalar(c,"select count(*) from friend_request where sender_id=1 and receiver_id=3 and status='PENDING'")).isEqualTo(1);
   assertThat(scalar(c,"select count(*) from user_friend_relation")).isEqualTo(2);
  }
 }
 @Test void duplicateDirectPairsAbortBeforeSchemaChanges() throws Exception {
  try(var c=legacy();var s=c.createStatement()) {
   s.execute("insert into conversation values('D','SINGLE',2,null,100)");s.execute("insert into conversation_participant values('D:1','D',1,'',10,0),('D:2','D',2,'',20,1)");
   assertThatThrownBy(()->new SocialSchemaMigration(c).apply()).isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate direct pairs");
   assertThat(scalar(c,"select count(*) from information_schema.tables where table_name='DIRECT_CONVERSATION'")).isZero();
  }
 }
}
