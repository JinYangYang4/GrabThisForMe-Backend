package com.study.grabthisforme.service;

import com.study.grabthisforme.common.*;
import com.study.grabthisforme.persistence.entity.*;
import com.study.grabthisforme.persistence.repository.*;
import jakarta.transaction.Transactional;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversationMembershipService {
  private final ConversationRepository conversations;
  private final ConversationParticipantRepository members;
  private final ConversationUserStateRepository states;
  private final DirectConversationRepository directs;
  private final ChatGroupRepository groups;
  private final UserAccountRepository users;
  private final IdGenerator ids;
  @org.springframework.beans.factory.annotation.Autowired private SystemMessageService systemMessages;
  @org.springframework.beans.factory.annotation.Autowired private UserFriendRelationRepository friends;

  public ConversationMembershipService(
      ConversationRepository c,
      ConversationParticipantRepository m,
      ConversationUserStateRepository s,
      DirectConversationRepository d,
      ChatGroupRepository g,
      UserAccountRepository u,
      IdGenerator i) {
    conversations = c;
    members = m;
    states = s;
    directs = d;
    groups = g;
    users = u;
    ids = i;
  }

  public void lockUsers(long a, long b) {
    users
        .findForUpdate(Math.min(a, b))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found"));
    if (a != b)
      users
          .findForUpdate(Math.max(a, b))
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found"));
  }

  @Transactional
  public ConversationEntity direct(long a, long b) {
    if (a == b)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, 40061, "A direct conversation requires two users");
    lockUsers(a, b);
    var existing = directs.findByUserLowIdAndUserHighId(Math.min(a, b), Math.max(a, b));
    if (existing.isPresent())
      return conversations.findById(existing.get().conversationId).orElseThrow();
    var c = create("SINGLE");
    directs.save(new DirectConversationEntity(c.conversationId, a, b));
    add(c.conversationId, a, "MEMBER", c.createdAt, 0, true);
    add(c.conversationId, b, "MEMBER", c.createdAt, 1, false);
    if (friends.findByUserIdAndFriendUserId(a, b).isEmpty())
      systemMessages.publish(c.conversationId, a, "TEMPORARY_CONVERSATION", "temporary",
          "当前为临时会话，添加好友后方便再次联系");
    return c;
  }

  @Transactional
  public ChatGroupEntity createGroup(
      long owner, String name, List<Long> requested, String requestKey) {
    if (name == null || name.isBlank() || name.length() > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, 40061, "Invalid group name");
    if (requestKey == null || requestKey.isBlank() || requestKey.length() > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, 40061, "Idempotency key required");
    lockUsers(owner, owner);
    Set<Long> unique = new LinkedHashSet<>();
    unique.add(owner);
    if (requested != null) unique.addAll(requested);
    if (unique.contains(null))
      throw new ApiException(HttpStatus.BAD_REQUEST, 40061, "Invalid member");
    String key = owner + ":" + requestKey;
    String fingerprint = name + ":" + unique.stream().sorted().toList();
    var existing = groups.findByCreationKey(key);
    if (existing.isPresent()) {
      if (!fingerprint.equals(existing.get().creationFingerprint))
        throw new ApiException(
            HttpStatus.CONFLICT, 40961, "Idempotency key reused for different group");
      return existing.get();
    }
    if (users.findAllByUserIdIn(new ArrayList<>(unique)).size() != unique.size())
      throw new ApiException(HttpStatus.NOT_FOUND, 40401, "Member not found");
    var c = create("GROUP");
    var g = new ChatGroupEntity();
    g.groupId = ids.nextLongId();
    g.conversationId = c.conversationId;
    g.groupName = name;
    g.createTime = c.createdAt;
    g.creationKey = key;
    g.creationFingerprint = fingerprint;
    groups.save(g);
    int index = 0;
    for (long uid : unique)
      add(
          c.conversationId,
          uid,
          uid == owner ? "OWNER" : "MEMBER",
          c.createdAt,
          index++,
          uid == owner);
    systemMessages.publish(c.conversationId, owner, "GROUP_CREATED", "created",
        systemMessages.name(owner) + "创建了群聊");
    return g;
  }

  @Transactional
  public void join(long userId, long groupId) {
    var g = group(groupId);
    conversations.findForUpdate(g.conversationId).orElseThrow();
    if (!users.existsById(userId))
      throw new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found");
    if (!members.existsByConversationIdAndUserId(g.conversationId, userId)) {
      add(
          g.conversationId,
          userId,
          "MEMBER",
          System.currentTimeMillis(),
          (int) members.countByConversationId(g.conversationId),
          true);
      systemMessages.publish(g.conversationId, userId, "MEMBER_JOINED", UUID.randomUUID().toString(),
          systemMessages.name(userId) + "加入了群聊");
    }
  }

  public void announceFriendship(String cid, long actor, String requestId) {
    systemMessages.publish(cid, actor, "FRIEND_ACCEPTED", requestId, "你们已成为好友，可以开始聊天了");
  }

  public ChatGroupEntity group(long id) {
    return groups
        .findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40461, "Group not found"));
  }

  public void requireMember(String id, long uid) {
    if (!members.existsByConversationIdAndUserId(id, uid))
      throw new ApiException(HttpStatus.FORBIDDEN, 40361, "You are not in this conversation");
  }

  private ConversationEntity create(String type) {
    var c = new ConversationEntity();
    c.conversationId = ids.nextConversationId();
    c.conversationType = type;
    c.createdAt = System.currentTimeMillis();
    c.lastTime = c.createdAt;
    return conversations.save(c);
  }

  private void add(String id, long uid, String role, long time, int order, boolean creator) {
    members.save(new ConversationParticipantEntity(id, uid, role, time, order));
    states.save(new ConversationUserStateEntity(id, uid, 0, false, creator ? time : null));
  }
}
