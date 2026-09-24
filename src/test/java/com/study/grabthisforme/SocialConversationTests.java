package com.study.grabthisforme;
import com.study.grabthisforme.service.*;
import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:social-model;MODE=MySQL;DB_CLOSE_DELAY=-1")
class SocialConversationTests {
 @Autowired AuthService auth;
 @Autowired ConversationService chats;
 @Autowired SocialService social;
 @Autowired ConversationParticipantRepository members;
 @Autowired ConversationUserStateRepository states;
 @Autowired FriendRequestRepository requests;
 @Autowired UserFriendRelationRepository friends;
 @Autowired DirectConversationRepository directs;
 @Autowired MessageRepository messages;
 long user(){return auth.register("social_"+UUID.randomUUID(),"Testpass123","Tester",null,null).user().id();}
 @Test void simultaneousDirectCreationReturnsOneSharedIdentity() throws Exception {
  long a=user(),b=user();var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)) {
   var calls=new ArrayList<Future<String>>();
   for(int i=0;i<6;i++){final boolean reverse=i%2==0;calls.add(pool.submit(()->{start.await();return chats.createSingleConversation(reverse?a:b,reverse?b:a).conversationId();}));}
   start.countDown();var ids=new HashSet<String>();for(var f:calls)ids.add(f.get(20,TimeUnit.SECONDS));
   assertThat(ids).hasSize(1);var id=ids.iterator().next();
   assertThat(members.countByConversationId(id)).isEqualTo(2);
   assertThat(directs.findByUserLowIdAndUserHighId(Math.min(a,b),Math.max(a,b))).isPresent();
   chats.setHidden(a,id,true);assertThat(chats.listConversations(b).getFirst().isHidden()).isFalse();
  }
 }
 @Test void openingGroupNeverRewritesMembershipAndPermissionsUseSameTable() {
  long a=user(),b=user(),outsider=user();
  var first=chats.createGroupConversation(a,"group",List.of(b),"request-one");
  var again=chats.createGroupConversation(a,"group",List.of(b),"request-one");
  assertThat(first.conversationId()).isEqualTo(again.conversationId());
  var before=members.findById(new ConversationMemberId(first.conversationId(),a)).orElseThrow();
  for(int i=0;i<3;i++)chats.openGroupConversation(a,first.groupId());
  var after=members.findById(new ConversationMemberId(first.conversationId(),a)).orElseThrow();
  assertThat(after.joinedAt).isEqualTo(before.joinedAt);assertThat(after.role).isEqualTo("OWNER");
  assertThatThrownBy(()->chats.openGroupConversation(outsider,first.groupId())).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
  chats.setHidden(b,first.conversationId(),true);chats.openGroupConversation(a,first.groupId());
  assertThat(states.findByConversationIdAndUserId(first.conversationId(),b).orElseThrow().isHidden).isTrue();
  members.deleteById(new ConversationMemberId(first.conversationId(),b));
  assertThat(chats.listConversations(b)).isEmpty();
  assertThatThrownBy(()->chats.listMessages(b,first.conversationId(),null,20)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
  assertThatThrownBy(()->chats.sendMessage(b,first.conversationId(),"x","TEXT","hi",null)).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
  assertThatThrownBy(()->chats.createGroupConversation(a,"changed",List.of(b),"request-one")).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
 }
 @Test void requestHasOneRowBothViewsAndIdempotentAuthorizedAcceptance() {
  long a=user(),b=user(),c=user();social.addFriend(a,b);social.addFriend(a,b);
  var av=social.listFriendRequests(a).getFirst();var bv=social.listFriendRequests(b).getFirst();
  assertThat(av.requestId()).isEqualTo(bv.requestId());assertThat(av.senderId()).isEqualTo(a);
  assertThatThrownBy(()->social.acceptFriendRequest(c,av.requestId())).isInstanceOf(com.study.grabthisforme.common.ApiException.class);
  social.acceptFriendRequest(b,av.requestId());social.acceptFriendRequest(b,av.requestId());
  assertThat(friends.findByUserIdAndFriendUserId(a,b)).isPresent();assertThat(friends.findByUserIdAndFriendUserId(b,a)).isPresent();
  assertThat(chats.listConversations(a)).hasSize(1);
  assertThat(requests.findById(av.requestId()).orElseThrow().pendingPair).isNull();
 }
 @Test void oppositeRequestsConvergeAndRejectedRequestCanBeRetried() throws Exception {
  long a=user(),b=user();var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var f=pool.submit(()->{start.await();social.addFriend(a,b);return true;});
   var g=pool.submit(()->{start.await();social.addFriend(b,a);return true;});start.countDown();f.get(20,TimeUnit.SECONDS);g.get(20,TimeUnit.SECONDS);
  }
  assertThat(friends.findByUserIdAndFriendUserId(a,b)).isPresent();assertThat(social.listFriendRequests(a)).hasSize(1);
  long c=user();social.addFriend(a,c);var r=social.listFriendRequests(c).getFirst();social.rejectFriendRequest(c,r.requestId());social.addFriend(a,c);
  assertThat(social.listFriendRequests(c)).hasSize(2);
 }
 @Test void requestCursorIncludesIdForEqualTimestamps() {
  long a=user(),b=user(),c=user();social.addFriend(a,b);social.addFriend(c,a);
  var page=social.listFriendRequests(a);for(var v:page){var r=requests.findById(v.requestId()).orElseThrow();r.createdAt=123L;requests.save(r);}
  var first=social.listFriendRequests(a,null,null,1).getFirst();
  var second=social.listFriendRequests(a,first.createdAt(),first.requestId(),1).getFirst();
  assertThat(first.requestId()).isNotEqualTo(second.requestId());
 }
 @Test void messageCursorDoesNotSkipEqualTimestamps() {
  long a=user(),b=user();String cid=chats.createSingleConversation(a,b).conversationId();
  // Isolate the equal-timestamp fixture from the automatic conversation-created event.
  messages.deleteAll(messages.findAllByConversationIdOrderByTimestampAsc(cid));
  for(int i=0;i<3;i++){var v=chats.sendMessage(a,cid,"same-time-"+i,"TEXT","hello",null);var m=messages.findById(v.messageId()).orElseThrow();m.timestamp=100L;messages.save(m);}
  var newest=chats.listMessages(b,cid,null,null,1).getFirst();
  var rest=chats.listMessages(b,cid,newest.timestamp(),newest.messageId(),20);
  assertThat(rest).hasSize(2);assertThat(rest.stream().map(v->v.messageId())).doesNotContain(newest.messageId());
 }
}
