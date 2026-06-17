package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import com.study.grabthisforme.persistence.entity.UserFriendRelationEntity;
import com.study.grabthisforme.persistence.entity.UserGroupRelationEntity;
import com.study.grabthisforme.persistence.repository.ChatGroupRepository;
import com.study.grabthisforme.persistence.repository.UserFriendRelationRepository;
import com.study.grabthisforme.persistence.repository.UserGroupRelationRepository;
import com.study.grabthisforme.service.view.GroupView;
import com.study.grabthisforme.service.view.UserView;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SocialService {

    private final UserFriendRelationRepository userFriendRelationRepository;
    private final UserGroupRelationRepository userGroupRelationRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public SocialService(
        UserFriendRelationRepository userFriendRelationRepository,
        UserGroupRelationRepository userGroupRelationRepository,
        ChatGroupRepository chatGroupRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.userFriendRelationRepository = userFriendRelationRepository;
        this.userGroupRelationRepository = userGroupRelationRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<UserView> listFriends(long userId) {
        return userFriendRelationRepository.findAllByUserId(userId).stream()
            .map(entity -> viewAssembler.getUserView(entity.friendUserId))
            .toList();
    }

    public List<GroupView> listGroups(long userId) {
        return userGroupRelationRepository.findAllByUserId(userId).stream()
            .map(entity -> viewAssembler.toGroupView(entity.groupId))
            .toList();
    }

    @Transactional
    public void addFriend(long userId, long friendUserId) {
        if (userId == friendUserId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40051, "Cannot add yourself");
        }
        if (viewAssembler.getUserView(friendUserId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40401, "Friend user not found");
        }
        if (userFriendRelationRepository.findByUserIdAndFriendUserId(userId, friendUserId).isEmpty()) {
            long now = System.currentTimeMillis();
            userFriendRelationRepository.save(new UserFriendRelationEntity(userId, friendUserId, "FRIEND", now));
            userFriendRelationRepository.save(new UserFriendRelationEntity(friendUserId, userId, "FRIEND", now));
        }
    }

    @Transactional
    public GroupView createGroup(long userId, String groupName, List<Long> memberIds) {
        long groupId = idGenerator.nextLongId();
        ChatGroupEntity group = new ChatGroupEntity();
        group.groupId = groupId;
        group.groupName = groupName;
        group.createTime = System.currentTimeMillis();
        chatGroupRepository.save(group);

        Set<Long> distinctMembers = new LinkedHashSet<>();
        distinctMembers.add(userId);
        if (memberIds != null) {
            distinctMembers.addAll(memberIds);
        }
        for (Long memberId : distinctMembers) {
            if (viewAssembler.getUserView(memberId) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, 40401, "Member user not found: " + memberId);
            }
            userGroupRelationRepository.save(new UserGroupRelationEntity(
                memberId,
                groupId,
                memberId.equals(userId) ? "OWNER" : "MEMBER",
                System.currentTimeMillis()
            ));
        }
        return viewAssembler.toGroupView(groupId);
    }
}
