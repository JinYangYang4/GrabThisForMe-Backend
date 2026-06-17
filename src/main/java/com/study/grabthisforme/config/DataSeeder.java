package com.study.grabthisforme.config;

import com.study.grabthisforme.auth.PasswordService;
import com.study.grabthisforme.common.IdGenerator;
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
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import com.study.grabthisforme.persistence.entity.SecondhandTradeEntity;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryItemEntity;
import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import com.study.grabthisforme.persistence.entity.UserFriendRelationEntity;
import com.study.grabthisforme.persistence.entity.UserGroupRelationEntity;
import com.study.grabthisforme.persistence.entity.UserPostEntity;
import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import com.study.grabthisforme.persistence.entity.UserStatisticsEntity;
import com.study.grabthisforme.persistence.repository.ChatGroupRepository;
import com.study.grabthisforme.persistence.repository.ConversationParticipantRepository;
import com.study.grabthisforme.persistence.repository.ConversationRepository;
import com.study.grabthisforme.persistence.repository.ConversationUserStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.persistence.repository.GoodsPriceRepository;
import com.study.grabthisforme.persistence.repository.GoodsStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsUiRepository;
import com.study.grabthisforme.persistence.repository.MessageRepository;
import com.study.grabthisforme.persistence.repository.OrderRepository;
import com.study.grabthisforme.persistence.repository.PostRepository;
import com.study.grabthisforme.persistence.repository.PostStatsRepository;
import com.study.grabthisforme.persistence.repository.SecondhandTradeRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryItemRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryRepository;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.StoreTagRepository;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.persistence.repository.UserFriendRelationRepository;
import com.study.grabthisforme.persistence.repository.UserGroupRelationRepository;
import com.study.grabthisforme.persistence.repository.UserPostRepository;
import com.study.grabthisforme.persistence.repository.UserProfileRepository;
import com.study.grabthisforme.persistence.repository.UserStatisticsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
        UserAccountRepository userAccountRepository,
        UserProfileRepository userProfileRepository,
        UserStatisticsRepository userStatisticsRepository,
        StoreRepository storeRepository,
        StoreTagRepository storeTagRepository,
        StoreGoodsCategoryRepository storeGoodsCategoryRepository,
        StoreGoodsCategoryItemRepository storeGoodsCategoryItemRepository,
        GoodsBaseRepository goodsBaseRepository,
        GoodsPriceRepository goodsPriceRepository,
        GoodsUiRepository goodsUiRepository,
        GoodsStateRepository goodsStateRepository,
        SecondhandTradeRepository secondhandTradeRepository,
        PostRepository postRepository,
        PostStatsRepository postStatsRepository,
        UserPostRepository userPostRepository,
        UserFriendRelationRepository userFriendRelationRepository,
        ChatGroupRepository chatGroupRepository,
        UserGroupRelationRepository userGroupRelationRepository,
        ConversationRepository conversationRepository,
        ConversationParticipantRepository conversationParticipantRepository,
        ConversationUserStateRepository conversationUserStateRepository,
        MessageRepository messageRepository,
        OrderRepository orderRepository,
        PasswordService passwordService,
        IdGenerator idGenerator
    ) {
        return args -> {
            if (userAccountRepository.count() > 0) {
                return;
            }

            long userA = 10001L;
            long userB = 10002L;
            long userC = 10003L;
            long now = System.currentTimeMillis();

            userAccountRepository.save(new UserAccountEntity(userA, "alice", passwordService.hash("123456"), false, true, now, now));
            userAccountRepository.save(new UserAccountEntity(userB, "bob", passwordService.hash("123456"), false, true, now, now));
            userAccountRepository.save(new UserAccountEntity(userC, "carol", passwordService.hash("123456"), false, true, now, now));

            userProfileRepository.save(new UserProfileEntity(userA, "Alice", "", "13800000001", "alice@example.com", 2, true, "校园代拿和二手爱好者"));
            userProfileRepository.save(new UserProfileEntity(userB, "Bob", "", "13800000002", "bob@example.com", 1, false, "随叫随到"));
            userProfileRepository.save(new UserProfileEntity(userC, "Carol", "", "13800000003", "carol@example.com", 2, false, "闲置出清"));

            userStatisticsRepository.save(new UserStatisticsEntity(userA, 18L, 5L, 12L));
            userStatisticsRepository.save(new UserStatisticsEntity(userB, 9L, 2L, 4L));
            userStatisticsRepository.save(new UserStatisticsEntity(userC, 6L, 1L, 3L));

            long storeId = 20001L;
            storeRepository.save(new StoreEntity(
                storeId,
                userA,
                "Alice校园小店",
                "校园超市",
                "A区宿舍一楼",
                22.0,
                113.0,
                "020-88888888",
                "08:00-22:30",
                "10",
                "2",
                true,
                "",
                4.8f,
                320L
            ));
            storeTagRepository.save(new StoreTagEntity(storeId, "夜间配送", 0));
            storeTagRepository.save(new StoreTagEntity(storeId, "热销零食", 1));

            long categoryId = 21001L;
            storeGoodsCategoryRepository.save(new StoreGoodsCategoryEntity(categoryId, storeId, "饮料零食", 0));

            long goodsId1 = 30001L;
            long goodsId2 = 30002L;
            goodsBaseRepository.save(new GoodsBaseEntity(goodsId1, storeId, "可乐 500ml", "宿舍常备", "drink"));
            goodsPriceRepository.save(new GoodsPriceEntity(goodsId1, 3.5, 3.0, "限时"));
            goodsUiRepository.save(new GoodsUiEntity(goodsId1, "", "热卖", "瓶"));
            goodsStateRepository.save(new GoodsStateEntity(goodsId1, 88L, 100, false, true, 0, 55L));
            storeGoodsCategoryItemRepository.save(new StoreGoodsCategoryItemEntity(categoryId, goodsId1, 0));

            goodsBaseRepository.save(new GoodsBaseEntity(goodsId2, storeId, "二手计算器", "考试可用", "secondhand"));
            goodsPriceRepository.save(new GoodsPriceEntity(goodsId2, 25.0, 0.0, ""));
            goodsUiRepository.save(new GoodsUiEntity(goodsId2, "", "9成新", "件"));
            goodsStateRepository.save(new GoodsStateEntity(goodsId2, 3L, 1, false, false, 0, 1L));
            secondhandTradeRepository.save(new SecondhandTradeEntity(goodsId2, userC, 59.0, "9成新", "6个月", 0, true));
            storeGoodsCategoryItemRepository.save(new StoreGoodsCategoryItemEntity(categoryId, goodsId2, 1));

            PostEntity post = new PostEntity();
            post.postId = "POST_BOOTSTRAP";
            post.content = "今天晚上谁顺路帮忙带一瓶可乐？";
            post.imagesJson = Jsons.writeStringList(java.util.List.of());
            post.createTime = now;
            postRepository.save(post);
            PostStatsEntity postStats = new PostStatsEntity();
            postStats.postId = post.postId;
            postStats.likeCount = 2;
            postStats.commentCount = 0;
            postStatsRepository.save(postStats);
            userPostRepository.save(new UserPostEntity(userA, post.postId));

            userFriendRelationRepository.save(new UserFriendRelationEntity(userA, userB, "FRIEND", now));
            userFriendRelationRepository.save(new UserFriendRelationEntity(userB, userA, "FRIEND", now));

            ChatGroupEntity group = new ChatGroupEntity();
            group.groupId = 40001L;
            group.groupName = "宿舍拼单群";
            group.createTime = now;
            chatGroupRepository.save(group);
            userGroupRelationRepository.save(new UserGroupRelationEntity(userA, group.groupId, "OWNER", now));
            userGroupRelationRepository.save(new UserGroupRelationEntity(userB, group.groupId, "MEMBER", now));
            userGroupRelationRepository.save(new UserGroupRelationEntity(userC, group.groupId, "MEMBER", now));

            ConversationEntity singleConversation = new ConversationEntity();
            singleConversation.conversationId = idGenerator.nextConversationId();
            singleConversation.conversationType = "SINGLE";
            singleConversation.targetId = userB;
            singleConversation.lastTime = now;
            conversationRepository.save(singleConversation);

            MessageEntity message = new MessageEntity();
            message.messageId = idGenerator.nextMessageId();
            message.conversationId = singleConversation.conversationId;
            message.senderId = userA;
            message.type = "TEXT";
            message.content = "我刚下楼，顺手给你带。";
            message.timestamp = now;
            message.status = "SENT";
            messageRepository.save(message);
            singleConversation.lastMessageId = message.messageId;
            conversationRepository.save(singleConversation);

            conversationParticipantRepository.save(new ConversationParticipantEntity(singleConversation.conversationId, userA, "", now, 0));
            conversationParticipantRepository.save(new ConversationParticipantEntity(singleConversation.conversationId, userB, "", now, 1));
            conversationUserStateRepository.save(new ConversationUserStateEntity(singleConversation.conversationId, userA, 0, false));
            conversationUserStateRepository.save(new ConversationUserStateEntity(singleConversation.conversationId, userB, 1, false));

            OrderEntity order = new OrderEntity();
            order.orderId = "ORD_BOOTSTRAP";
            order.senderId = userB;
            order.senderName = "Bob";
            order.senderAvatarUrl = "";
            order.buyerId = userA;
            order.buyerName = "Alice";
            order.buyerAvatarUrl = "";
            order.goodsId = goodsId1;
            order.goodsName = "可乐 500ml";
            order.goodsMessage = "宿舍常备";
            order.goodsPrice = 3.0;
            order.goodsPic = "";
            order.shelfNumber = "A-01";
            order.aimPosition = "2栋201";
            order.atPosition = "超市门口";
            order.startTime = now;
            order.endTime = now + 3_600_000L;
            order.orderStatus = 1;
            order.isAccepted = true;
            orderRepository.save(order);
        };
    }
}
