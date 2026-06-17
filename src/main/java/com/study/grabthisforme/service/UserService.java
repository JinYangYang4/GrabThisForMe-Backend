package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.persistence.repository.UserProfileRepository;
import com.study.grabthisforme.service.view.UserView;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final ViewAssembler viewAssembler;

    public UserService(
        UserAccountRepository userAccountRepository,
        UserProfileRepository userProfileRepository,
        ViewAssembler viewAssembler
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.viewAssembler = viewAssembler;
    }

    public List<UserView> listUsers(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return userAccountRepository.findAll().stream()
            .sorted(Comparator.comparing((UserAccountEntity entity) -> entity.createTime).reversed())
            .map(entity -> viewAssembler.getUserView(entity.userId))
            .filter(user -> user != null)
            .filter(user -> normalizedKeyword.isBlank()
                || String.valueOf(user.id()).contains(normalizedKeyword)
                || (user.accountName() != null && user.accountName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                || (user.name() != null && user.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
            .toList();
    }

    public UserView getUser(long userId) {
        UserView userView = viewAssembler.getUserView(userId);
        if (userView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found");
        }
        return userView;
    }

    @Transactional
    public UserView updateProfile(
        long userId,
        String name,
        String avatarUrl,
        String phone,
        String email,
        Integer gender,
        Boolean isVip,
        String signature
    ) {
        UserProfileEntity entity = userProfileRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found"));
        if (name != null) {
            entity.displayName = name;
        }
        if (avatarUrl != null) {
            entity.avatarUrl = avatarUrl;
        }
        if (phone != null) {
            entity.phone = phone;
        }
        if (email != null) {
            entity.email = email;
        }
        if (gender != null) {
            entity.gender = gender;
        }
        if (isVip != null) {
            entity.isVip = isVip;
        }
        if (signature != null) {
            entity.signature = signature;
        }
        userProfileRepository.save(entity);
        return getUser(userId);
    }
}
