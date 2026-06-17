package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import com.study.grabthisforme.persistence.entity.UserLikedStoreEntity;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.StoreTagRepository;
import com.study.grabthisforme.persistence.repository.UserLikedStoreRepository;
import com.study.grabthisforme.service.view.StoreView;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreTagRepository storeTagRepository;
    private final UserLikedStoreRepository userLikedStoreRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public StoreService(
        StoreRepository storeRepository,
        StoreTagRepository storeTagRepository,
        UserLikedStoreRepository userLikedStoreRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.storeRepository = storeRepository;
        this.storeTagRepository = storeTagRepository;
        this.userLikedStoreRepository = userLikedStoreRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<StoreView> listStores(String keyword) {
        List<StoreEntity> stores = keyword == null || keyword.isBlank()
            ? storeRepository.findAll().stream().sorted(Comparator.comparing(entity -> entity.storeId, Comparator.reverseOrder())).toList()
            : storeRepository.findAllByNameContainingIgnoreCaseOrderByStoreIdDesc(keyword.trim());
        return stores.stream().map(store -> viewAssembler.getStoreView(store.storeId, true)).toList();
    }

    public StoreView getStore(long storeId) {
        StoreView storeView = viewAssembler.getStoreView(storeId, true);
        if (storeView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40421, "Store not found");
        }
        return storeView;
    }

    @Transactional
    public StoreView createStore(
        long userId,
        String name,
        String type,
        String address,
        Double latitude,
        Double longitude,
        String phone,
        String businessHours,
        String minOrderAmount,
        String deliveryFee,
        String pic,
        List<String> tags
    ) {
        long storeId = idGenerator.nextLongId();
        StoreEntity entity = new StoreEntity(
            storeId,
            userId,
            name,
            type,
            address,
            latitude,
            longitude,
            phone,
            businessHours,
            minOrderAmount == null ? "0" : minOrderAmount,
            deliveryFee == null ? "0" : deliveryFee,
            true,
            pic,
            0.0f,
            0L
        );
        storeRepository.save(entity);
        if (tags != null) {
            int sortOrder = 0;
            for (String tag : tags.stream().filter(value -> value != null && !value.isBlank()).toList()) {
                storeTagRepository.save(new StoreTagEntity(storeId, tag, sortOrder++));
            }
        }
        return getStore(storeId);
    }

    @Transactional
    public boolean setStoreLiked(long userId, long storeId, boolean liked) {
        getStore(storeId);
        Optional<UserLikedStoreEntity> existing = userLikedStoreRepository.findByUserIdAndStoreId(userId, storeId);
        if (liked) {
            if (existing.isEmpty()) {
                userLikedStoreRepository.save(new UserLikedStoreEntity(userId, storeId, System.currentTimeMillis()));
            }
            return true;
        }
        existing.ifPresent(userLikedStoreRepository::delete);
        return false;
    }
}
