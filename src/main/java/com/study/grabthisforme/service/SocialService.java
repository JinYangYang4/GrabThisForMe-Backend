package com.study.grabthisforme.service;

import com.study.grabthisforme.common.*;
import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.service.view.*;
import jakarta.transaction.Transactional;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SocialService {
  private final UserFriendRelationRepository friends;
  private final FriendRequestRepository requests;
  private final ConversationMembershipService membership;
  private final ConversationParticipantRepository participants;
  private final ChatGroupRepository groups;
  private final ViewAssembler views;
  private final PushService push;
  private final MessageRepository messages;
  private final ConversationRepository conversations;

  public SocialService(
      UserFriendRelationRepository f,
      FriendRequestRepository r,
      ConversationMembershipService m,
      ConversationParticipantRepository p,
      ChatGroupRepository g,
      ViewAssembler v,
      PushService s,
      MessageRepository msg,
      ConversationRepository c) {
    friends = f;
    requests = r;
    membership = m;
    participants = p;
    groups = g;
    views = v;
    push = s;
    messages = msg;
    conversations = c;
  }

  public List<FriendView> listFriends(long uid) {
    var relations = friends.findAllByUserId(uid);
    var ids = relations.stream().map(f -> f.friendUserId).toList();
    var users = views.getUserBriefViews(ids);
    return relations.stream().filter(f -> users.containsKey(f.friendUserId))
        .map(f -> new FriendView(users.get(f.friendUserId), f.remark)).toList();
  }

  public List<FriendRequestView> listFriendRequests(
      long uid, Long before, String beforeId, int limit) {
    if (before != null && beforeId == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, 40061, "Complete cursor required");
    var page =
        requests.page(uid, before, beforeId, PageRequest.of(0, Math.min(100, Math.max(1, limit))));
    var users =
        views.getUserBriefViews(
            page.stream().map(r -> r.senderId == uid ? r.receiverId : r.senderId).toList());
    return page.stream()
        .map(r -> view(r, uid, users.get(r.senderId == uid ? r.receiverId : r.senderId)))
        .toList();
  }

  public List<FriendRequestView> listFriendRequests(long uid) {
    return listFriendRequests(uid, null, null, 100);
  }

  @Transactional
  public void addFriend(long uid, long peer) {
    if (uid == peer) throw new ApiException(HttpStatus.BAD_REQUEST, 40051, "Cannot add yourself");
    membership.lockUsers(uid, peer);
    if (friends.findByUserIdAndFriendUserId(uid, peer).isPresent()) return;
    String pair = Math.min(uid, peer) + ":" + Math.max(uid, peer);
    var pending = requests.findByPendingPair(pair);
    if (pending.isPresent()) {
      if (pending.get().receiverId == uid) acceptLocked(uid, pending.get());
      return;
    }
    var r = new FriendRequestEntity();
    r.requestId = UUID.randomUUID().toString();
    r.senderId = uid;
    r.receiverId = peer;
    r.status = "PENDING";
    r.createdAt = System.currentTimeMillis();
    r.pendingPair = pair;
    requests.save(r);
    notifyAfterCommit(peer, "friend.request.received", view(r, peer, views.getUserBriefView(uid)));
  }

  @Transactional
  public void acceptFriendRequest(long uid, String requestId) {
    var first =
        requests
            .findById(requestId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, 40471, "Friend request not found"));
    membership.lockUsers(first.senderId, first.receiverId);
    // Reload after acquiring pair locks; another transaction may have handled the request.
    entityManager.refresh(first);
    acceptLocked(uid, first);
  }

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  private void acceptLocked(long uid, FriendRequestEntity r) {
    if (r.receiverId != uid)
      throw new ApiException(HttpStatus.FORBIDDEN, 40371, "Only receiver may accept");
    if ("ACCEPTED".equals(r.status)) return;
    if (!"PENDING".equals(r.status))
      throw new ApiException(HttpStatus.CONFLICT, 40971, "Request already handled");
    long now = System.currentTimeMillis();
    r.status = "ACCEPTED";
    r.handledAt = now;
    r.pendingPair = null;
    requests.save(r);
    friends.save(new UserFriendRelationEntity(uid, r.senderId, now));
    friends.save(new UserFriendRelationEntity(r.senderId, uid, now));
    var c = membership.direct(uid, r.senderId);
    membership.announceFriendship(c.conversationId, uid, r.requestId);
    notifyAfterCommit(
        uid, "friend.request.accepted", view(r, uid, views.getUserBriefView(r.senderId)));
    notifyAfterCommit(
        r.senderId, "friend.request.accepted", view(r, r.senderId, views.getUserBriefView(uid)));
  }

  @Transactional
  public void rejectFriendRequest(long uid, String id) {
    var r =
        requests
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40471, "Request not found"));
    membership.lockUsers(r.senderId, r.receiverId);
    entityManager.refresh(r);
    if (r.receiverId != uid)
      throw new ApiException(HttpStatus.FORBIDDEN, 40371, "Only receiver may reject");
    if ("REJECTED".equals(r.status)) return;
    if (!"PENDING".equals(r.status))
      throw new ApiException(HttpStatus.CONFLICT, 40971, "Request already handled");
    r.status = "REJECTED";
    r.handledAt = System.currentTimeMillis();
    r.pendingPair = null;
    requests.save(r);
  }

  public List<GroupView> listGroups(long uid) {
    var ids = participants.findAllByUserId(uid).stream().map(p -> p.conversationId).toList();
    return groups.findAllByConversationIdIn(ids).stream()
        .map(g -> views.toGroupView(g.groupId, uid))
        .toList();
  }

  public List<GroupView> searchGroups(String keyword) {
    return (keyword == null || keyword.isBlank()
            ? groups.findAll()
            : groups.findAllByGroupNameContainingIgnoreCaseOrderByCreateTimeDesc(keyword.trim()))
        .stream().map(g -> views.toGroupView(g.groupId)).toList();
  }

  @Transactional
  public void joinGroup(long uid, long gid) {
    membership.join(uid, gid);
  }

  @Transactional
  public GroupView createGroup(long uid, String name, List<Long> ids, String key) {
    return views.toGroupView(membership.createGroup(uid, name, ids, key).groupId, uid);
  }

  @Transactional
  public GroupView createGroup(long uid, String name, List<Long> ids) {
    return createGroup(uid, name, ids, UUID.randomUUID().toString());
  }

  private FriendRequestView view(FriendRequestEntity r, long uid, UserBriefView user) {
    return new FriendRequestView(
        r.requestId,
        r.senderId,
        r.receiverId,
        r.status,
        r.createdAt,
        r.handledAt,
        r.senderId == uid ? r.receiverId : r.senderId,
        user);
  }

  private void notifyAfterCommit(long uid, String type, FriendRequestView view) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              public void afterCommit() {
                push.pushToUser(uid, type, Map.of("type", type, "friendRequest", view));
              }
            });
  }
}
