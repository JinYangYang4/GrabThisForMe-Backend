package com.study.grabthisforme.service;

import com.study.grabthisforme.common.Jsons;
import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import com.study.grabthisforme.persistence.entity.ConversationEntity;
import com.study.grabthisforme.persistence.entity.ConversationParticipantEntity;
import com.study.grabthisforme.persistence.entity.ConversationUserStateEntity;
import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import com.study.grabthisforme.persistence.entity.GoodsPriceEntity;
import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import com.study.grabthisforme.persistence.entity.GoodsUiEntity;
import com.study.grabthisforme.persistence.entity.MessageEntity;
import com.study.grabthisforme.persistence.entity.OrderEntity;
import com.study.grabthisforme.persistence.entity.PostCommentEntity;
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.entity.PostReplyEntity;
import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import com.study.grabthisforme.persistence.entity.SecondhandTradeEntity;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryItemEntity;
import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import com.study.grabthisforme.persistence.entity.UserGroupRelationEntity;
import com.study.grabthisforme.persistence.entity.UserPostEntity;
import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import com.study.grabthisforme.persistence.entity.UserStatisticsEntity;
import com.study.grabthisforme.persistence.repository.ChatGroupRepository;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.persistence.repository.GoodsPriceRepository;
import com.study.grabthisforme.persistence.repository.GoodsStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsUiRepository;
import com.study.grabthisforme.persistence.repository.MessageRepository;
import com.study.grabthisforme.persistence.repository.PostCommentRepository;
import com.study.grabthisforme.persistence.repository.PostCustomTagRepository;
import com.study.grabthisforme.persistence.repository.PostReplyRepository;
import com.study.grabthisforme.persistence.repository.PostStatsRepository;
import com.study.grabthisforme.persistence.repository.SecondhandTradeRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryItemRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryRepository;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.StoreTagRepository;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.persistence.repository.UserGroupRelationRepository;
import com.study.grabthisforme.persistence.repository.UserLikedPostRepository;
import com.study.grabthisforme.persistence.repository.UserPostRepository;
import com.study.grabthisforme.persistence.repository.UserProfileRepository;
import com.study.grabthisforme.persistence.repository.UserStatisticsRepository;
import com.study.grabthisforme.service.view.ConversationView;
import com.study.grabthisforme.service.view.GoodsView;
import com.study.grabthisforme.service.view.GroupView;
import com.study.grabthisforme.service.view.MessageView;
import com.study.grabthisforme.service.view.OrderView;
import com.study.grabthisforme.service.view.PostView;
import com.study.grabthisforme.service.view.StoreView;
import com.study.grabthisforme.service.view.UserView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ViewAssembler {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserStatisticsRepository userStatisticsRepository;
    private final UserPostRepository userPostRepository;
    private final UserLikedPostRepository userLikedPostRepository;
    private final GoodsBaseRepository goodsBaseRepository;
    private final GoodsPriceRepository goodsPriceRepository;
    private final GoodsUiRepository goodsUiRepository;
    private final GoodsStateRepository goodsStateRepository;
    private final SecondhandTradeRepository secondhandTradeRepository;
    private final StoreRepository storeRepository;
    private final StoreTagRepository storeTagRepository;
    private final StoreGoodsCategoryRepository storeGoodsCategoryRepository;
    private final StoreGoodsCategoryItemRepository storeGoodsCategoryItemRepository;
    private final MessageRepository messageRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReplyRepository postReplyRepository;
    private final PostCustomTagRepository postCustomTagRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final UserGroupRelationRepository userGroupRelationRepository;

    public ViewAssembler(
        UserAccountRepository userAccountRepository,
        UserProfileRepository userProfileRepository,
        UserStatisticsRepository userStatisticsRepository,
        UserPostRepository userPostRepository,
        UserLikedPostRepository userLikedPostRepository,
        GoodsBaseRepository goodsBaseRepository,
        GoodsPriceRepository goodsPriceRepository,
        GoodsUiRepository goodsUiRepository,
        GoodsStateRepository goodsStateRepository,
        SecondhandTradeRepository secondhandTradeRepository,
        StoreRepository storeRepository,
        StoreTagRepository storeTagRepository,
        StoreGoodsCategoryRepository storeGoodsCategoryRepository,
        StoreGoodsCategoryItemRepository storeGoodsCategoryItemRepository,
        MessageRepository messageRepository,
        PostStatsRepository postStatsRepository,
        PostCommentRepository postCommentRepository,
        PostReplyRepository postReplyRepository,
        PostCustomTagRepository postCustomTagRepository,
        ChatGroupRepository chatGroupRepository,
        UserGroupRelationRepository userGroupRelationRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.userStatisticsRepository = userStatisticsRepository;
        this.userPostRepository = userPostRepository;
        this.userLikedPostRepository = userLikedPostRepository;
        this.goodsBaseRepository = goodsBaseRepository;
        this.goodsPriceRepository = goodsPriceRepository;
        this.goodsUiRepository = goodsUiRepository;
        this.goodsStateRepository = goodsStateRepository;
        this.secondhandTradeRepository = secondhandTradeRepository;
        this.storeRepository = storeRepository;
        this.storeTagRepository = storeTagRepository;
        this.storeGoodsCategoryRepository = storeGoodsCategoryRepository;
        this.storeGoodsCategoryItemRepository = storeGoodsCategoryItemRepository;
        this.messageRepository = messageRepository;
        this.postStatsRepository = postStatsRepository;
        this.postCommentRepository = postCommentRepository;
        this.postReplyRepository = postReplyRepository;
        this.postCustomTagRepository = postCustomTagRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.userGroupRelationRepository = userGroupRelationRepository;
    }

    public UserView getUserView(Long userId) {
        if (userId == null) {
            return null;
        }
        return getUserViews(List.of(userId)).get(userId);
    }

    public Map<Long, UserView> getUserViews(Collection<Long> userIds) {
        List<Long> distinctIds = userIds == null ? List.of() : userIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, UserAccountEntity> accounts = userAccountRepository.findAllByUserIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.userId, entity -> entity));
        Map<Long, UserProfileEntity> profiles = userProfileRepository.findAllByUserIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.userId, entity -> entity));
        Map<Long, UserStatisticsEntity> statistics = userStatisticsRepository.findAllByUserIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.userId, entity -> entity));

        Map<Long, UserView> result = new LinkedHashMap<>();
        for (Long userId : distinctIds) {
            UserAccountEntity account = accounts.get(userId);
            if (account == null) {
                continue;
            }
            UserProfileEntity profile = profiles.get(userId);
            UserStatisticsEntity stat = statistics.get(userId);
            result.put(userId, new UserView(
                userId,
                account.accountName,
                profile == null ? account.accountName : profile.displayName,
                profile == null ? "" : profile.avatarUrl,
                profile == null ? null : profile.phone,
                profile == null ? null : profile.email,
                profile == null ? 0 : profile.gender,
                profile != null && Boolean.TRUE.equals(profile.isVip),
                profile == null ? null : profile.signature,
                account.createTime,
                account.lastLoginTime,
                new UserView.UserStatisticsView(
                    stat == null ? 0L : stat.likeCount,
                    stat == null ? 0L : stat.fanCount,
                    stat == null ? 0L : stat.followCount
                )
            ));
        }
        return result;
    }

    public GoodsView getGoodsView(Long goodsId) {
        if (goodsId == null) {
            return null;
        }
        return getGoodsViews(List.of(goodsId)).get(goodsId);
    }

    public Map<Long, GoodsView> getGoodsViews(Collection<Long> goodsIds) {
        List<Long> distinctIds = goodsIds == null ? List.of() : goodsIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, GoodsBaseEntity> baseMap = goodsBaseRepository.findAllById(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, GoodsPriceEntity> priceMap = goodsPriceRepository.findAllByGoodsIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, GoodsUiEntity> uiMap = goodsUiRepository.findAllByGoodsIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, GoodsStateEntity> stateMap = goodsStateRepository.findAllByGoodsIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, SecondhandTradeEntity> secondhandMap = secondhandTradeRepository.findAllByGoodsIdIn(distinctIds)
            .stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));

        Map<Long, GoodsView> result = new LinkedHashMap<>();
        for (Long goodsId : distinctIds) {
            GoodsBaseEntity base = baseMap.get(goodsId);
            if (base == null) {
                continue;
            }
            GoodsPriceEntity price = priceMap.get(goodsId);
            GoodsUiEntity ui = uiMap.get(goodsId);
            GoodsStateEntity state = stateMap.get(goodsId);
            SecondhandTradeEntity secondhand = secondhandMap.get(goodsId);
            result.put(goodsId, new GoodsView(
                base.goodsId,
                base.storeId,
                base.name,
                base.message,
                base.categoryKey,
                new GoodsView.PriceView(
                    price == null ? 0.0 : price.price,
                    price == null ? 0.0 : price.discountPrice,
                    price == null ? "" : price.discountTag
                ),
                new GoodsView.UiView(
                    ui == null ? "" : ui.pic,
                    ui == null ? "" : ui.tag,
                    ui == null ? "" : ui.unit
                ),
                new GoodsView.StateView(
                    state == null ? 0L : state.saleNumber,
                    state == null ? 0 : state.stock,
                    state != null && Boolean.TRUE.equals(state.isSoldOut),
                    state != null && Boolean.TRUE.equals(state.isHot),
                    state == null ? 0 : state.purchaseStatus,
                    state == null ? 0L : state.soldCount
                ),
                secondhand == null ? null : new GoodsView.SecondhandView(
                    secondhand.saleUserId,
                    secondhand.originalPrice,
                    secondhand.quality,
                    secondhand.usedTime,
                    secondhand.tradeStatus,
                    secondhand.negotiable
                )
            ));
        }
        return result;
    }

    public StoreView getStoreView(Long storeId, boolean includeCategories) {
        if (storeId == null) {
            return null;
        }
        Optional<StoreEntity> storeOptional = storeRepository.findById(storeId);
        if (storeOptional.isEmpty()) {
            return null;
        }
        StoreEntity store = storeOptional.get();
        List<String> tags = storeTagRepository.findAllByStoreId(storeId).stream()
            .sorted(Comparator.comparing(entity -> entity.sortOrder == null ? 0 : entity.sortOrder))
            .map(entity -> entity.tag)
            .toList();

        List<StoreView.StoreGoodsCategoryView> categories = List.of();
        if (includeCategories) {
            List<StoreGoodsCategoryEntity> groupEntities = storeGoodsCategoryRepository.findAllByStoreIdOrderBySortOrderAsc(storeId);
            List<Long> groupIds = groupEntities.stream().map(entity -> entity.groupId).toList();
            List<StoreGoodsCategoryItemEntity> items = groupIds.isEmpty()
                ? List.of()
                : storeGoodsCategoryItemRepository.findAllByGroupIdIn(groupIds);
            Map<Long, List<StoreGoodsCategoryItemEntity>> itemMap = items.stream()
                .collect(Collectors.groupingBy(entity -> entity.groupId));
            List<Long> goodsIds = items.stream().map(entity -> entity.goodsId).distinct().toList();
            Map<Long, GoodsView> goodsViewMap = getGoodsViews(goodsIds);
            List<StoreView.StoreGoodsCategoryView> categoryViews = new ArrayList<>();
            for (StoreGoodsCategoryEntity groupEntity : groupEntities) {
                List<GoodsView> goodsViews = itemMap.getOrDefault(groupEntity.groupId, List.of()).stream()
                    .sorted(Comparator.comparing(entity -> entity.sortOrder == null ? 0 : entity.sortOrder))
                    .map(entity -> goodsViewMap.get(entity.goodsId))
                    .filter(Objects::nonNull)
                    .toList();
                categoryViews.add(new StoreView.StoreGoodsCategoryView(
                    groupEntity.groupId,
                    groupEntity.category,
                    groupEntity.sortOrder,
                    goodsViews
                ));
            }
            categories = categoryViews;
        }

        return new StoreView(
            store.storeId,
            store.ownerId,
            store.name,
            store.type,
            store.address,
            store.latitude,
            store.longitude,
            store.phone,
            store.businessHours,
            store.minOrderAmount,
            store.deliveryFee,
            store.isOpen,
            store.pic,
            store.rating,
            store.salesVolume,
            tags,
            categories
        );
    }

    public MessageView toMessageView(MessageEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MessageView(
            entity.messageId,
            entity.conversationId,
            entity.senderId,
            entity.type,
            entity.content,
            entity.mediaUrl,
            entity.timestamp,
            entity.status
        );
    }

    public OrderView toOrderView(OrderEntity entity) {
        return new OrderView(
            entity.orderId,
            getUserView(entity.senderId),
            getUserView(entity.buyerId),
            getGoodsView(entity.goodsId),
            entity.shelfNumber,
            entity.aimPosition,
            entity.atPosition,
            entity.startTime,
            entity.endTime,
            entity.orderStatus,
            entity.isAccepted
        );
    }

    public PostView.PostSummaryView toPostSummaryView(PostEntity entity) {
        UserPostEntity userPostEntity = userPostRepository.findByPostId(entity.postId);
        Long authorId = userPostEntity == null ? null : userPostEntity.userId;
        UserView author = getUserView(authorId);
        PostStatsEntity postStats = postStatsRepository.findById(entity.postId).orElse(null);

        return new PostView.PostSummaryView(
            entity.postId,
            entity.content,
            Jsons.readStringList(entity.imagesJson),
            entity.createTime,
            entity.categoryKey == null ? "" : entity.categoryKey,
            getPostCustomTags(entity.postId),
            authorId == null ? 0L : authorId,
            author == null ? "" : author.name(),
            author == null ? "" : author.headPic(),
            postStats == null ? 0 : postStats.likeCount,
            postStats == null ? 0 : postStats.commentCount
        );
    }

    public PostView toPostView(PostEntity entity, Long currentUserId) {
        UserPostEntity userPostEntity = userPostRepository.findByPostId(entity.postId);
        Long authorId = userPostEntity == null ? null : userPostEntity.userId;
        UserView author = getUserView(authorId);
        PostStatsEntity postStats = postStatsRepository.findById(entity.postId).orElse(null);
        boolean likedByCurrentUser = currentUserId != null
            && userLikedPostRepository.findByUserIdAndPostId(currentUserId, entity.postId).isPresent();

        return new PostView(
            entity.postId,
            entity.content,
            Jsons.readStringList(entity.imagesJson),
            entity.createTime,
            entity.categoryKey == null ? "" : entity.categoryKey,
            getPostCustomTags(entity.postId),
            author,
            postStats == null ? 0 : postStats.likeCount,
            postStats == null ? 0 : postStats.commentCount,
            likedByCurrentUser
        );
    }

    public PostView.CommentView toCommentView(PostCommentEntity entity, int replyCount) {
        return new PostView.CommentView(
            entity.commentId,
            entity.time,
            entity.message,
            Jsons.readStringList(entity.imageUrlsJson),
            getUserView(entity.commenterId),
            replyCount
        );
    }

    public PostView.ReplyView toReplyView(PostReplyEntity entity) {
        return new PostView.ReplyView(
            entity.replyId,
            entity.parentCommentId,
            entity.parentReplyId,
            entity.time,
            entity.message,
            Jsons.readStringList(entity.imageUrlsJson),
            getUserView(entity.commenterId),
            getUserView(entity.beCommenterId)
        );
    }

    public ConversationView toConversationView(
        ConversationEntity conversation,
        Long currentUserId,
        MessageEntity lastMessage,
        List<ConversationParticipantEntity> participants,
        ConversationUserStateEntity state
    ) {
        List<Long> participantIds = participants.stream().map(entity -> entity.userId).toList();
        Map<Long, UserView> users = getUserViews(participantIds);
        List<UserView> participantViews = participants.stream()
            .map(entity -> users.get(entity.userId))
            .filter(Objects::nonNull)
            .toList();
        return new ConversationView(
            conversation.conversationId,
            conversation.conversationType,
            conversation.targetId,
            toMessageView(lastMessage),
            conversation.lastTime,
            state == null ? 0 : state.unreadCount,
            state != null && Boolean.TRUE.equals(state.isHidden),
            conversation.conversationType.equals("SINGLE")
                ? participantViews.stream().filter(user -> !Objects.equals(user.id(), currentUserId)).toList()
                : participantViews
        );
    }

    public GroupView toGroupView(Long groupId) {
        if (groupId == null) {
            return null;
        }
        ChatGroupEntity group = chatGroupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return null;
        }
        List<UserGroupRelationEntity> relations = userGroupRelationRepository.findAllByGroupId(groupId);
        Map<Long, UserView> users = getUserViews(relations.stream().map(entity -> entity.userId).toList());
        List<GroupView.MemberView> members = relations.stream()
            .map(entity -> new GroupView.MemberView(entity.userId, entity.role, entity.joinedTime, users.get(entity.userId)))
            .toList();
        return new GroupView(group.groupId, group.groupName, group.createTime, members);
    }

    public Map<String, MessageEntity> loadMessagesByIds(Collection<String> messageIds) {
        List<String> distinctIds = messageIds == null ? List.of() : messageIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return messageRepository.findAllByMessageIdIn(distinctIds).stream()
            .collect(Collectors.toMap(entity -> entity.messageId, entity -> entity));
    }

    private List<String> getPostCustomTags(String postId) {
        return postCustomTagRepository.findAllByPostIdOrderBySortOrderAsc(postId).stream()
            .map(entity -> entity.tag)
            .toList();
    }
}