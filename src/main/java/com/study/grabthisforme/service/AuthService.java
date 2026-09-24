package com.study.grabthisforme.service;

import com.study.grabthisforme.auth.PasswordService;
import com.study.grabthisforme.auth.TokenService;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import com.study.grabthisforme.persistence.entity.UserStatisticsEntity;
import com.study.grabthisforme.persistence.entity.AuthSessionRevokedReason;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.persistence.repository.UserProfileRepository;
import com.study.grabthisforme.persistence.repository.UserStatisticsRepository;
import com.study.grabthisforme.service.view.UserView;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserStatisticsRepository userStatisticsRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public AuthService(
        UserAccountRepository userAccountRepository,
        UserProfileRepository userProfileRepository,
        UserStatisticsRepository userStatisticsRepository,
        PasswordService passwordService,
        TokenService tokenService,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.userStatisticsRepository = userStatisticsRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    @Transactional
    public AuthResult register(
        String accountName,
        String password,
        String displayName,
        String phone,
        String email,
        String deviceName
    ) {
        userAccountRepository.findByAccountName(accountName).ifPresent(user -> {
            throw new ApiException(HttpStatus.CONFLICT, 40901, "Account name already exists");
        });
        long userId = idGenerator.nextLongId();
        long now = System.currentTimeMillis();
        userAccountRepository.save(new UserAccountEntity(
            userId,
            accountName,
            passwordService.hash(password),
            false,
            true,
            now,
            now
        ));
        userProfileRepository.save(new UserProfileEntity(
            userId,
            displayName == null || displayName.isBlank() ? accountName : displayName,
            "",
            phone,
            email,
            0,
            false,
            null
        ));
        userStatisticsRepository.save(new UserStatisticsEntity(userId, 0L, 0L, 0L));
        return buildAuthResult(tokenService.createSession(userId, deviceName), userId);
    }

    @Transactional
    public AuthResult register(String accountName, String password, String displayName, String phone, String email) {
        return register(accountName, password, displayName, phone, email, null);
    }

    @Transactional
    public AuthResult login(String identifier, String password, String deviceName) {
        UserAccountEntity located = findAccount(identifier)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, 40105, "Account not found"));
        UserAccountEntity account = userAccountRepository.findForUpdate(located.userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, 40105, "Account not found"));
        if (!passwordService.matches(password, account.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40106, "Password incorrect");
        }
        account.lastLoginTime = System.currentTimeMillis();
        userAccountRepository.save(account);
        return buildAuthResult(tokenService.replaceActiveSession(account.userId, deviceName), account.userId);
    }

    @Transactional
    public AuthResult login(String identifier, String password) {
        return login(identifier, password, null);
    }

    @Transactional
    public AuthResult changePassword(long userId, String sessionId, String currentPassword, String newPassword) {
        var account=userAccountRepository.findForUpdate(userId)
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,40105,"Account not found"));
        if (!passwordService.matches(currentPassword,account.passwordHash))
            throw new ApiException(HttpStatus.BAD_REQUEST,40008,"Current password incorrect");
        if (newPassword.equals(currentPassword))
            throw new ApiException(HttpStatus.BAD_REQUEST,40009,"New password must be different");
        account.passwordHash=passwordService.hash(newPassword);
        account.tokenVersion=(account.tokenVersion==null?0:account.tokenVersion)+1;
        userAccountRepository.saveAndFlush(account);
        return buildAuthResult(tokenService.rotateCurrentSession(userId, sessionId), userId);
    }

    @Transactional
    public AuthResult changePassword(long userId, String currentPassword, String newPassword) {
        var account=userAccountRepository.findForUpdate(userId)
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,40105,"Account not found"));
        if (!passwordService.matches(currentPassword,account.passwordHash))
            throw new ApiException(HttpStatus.BAD_REQUEST,40008,"Current password incorrect");
        if (newPassword.equals(currentPassword))
            throw new ApiException(HttpStatus.BAD_REQUEST,40009,"New password must be different");
        account.passwordHash=passwordService.hash(newPassword);
        account.tokenVersion=(account.tokenVersion==null?0:account.tokenVersion)+1;
        userAccountRepository.saveAndFlush(account);
        return buildAuthResult(tokenService.rotateActiveSession(userId), userId);
    }

    @Transactional
    public void logout(long userId, String sessionId) {
        tokenService.revokeCurrentSession(userId, sessionId, AuthSessionRevokedReason.USER_LOGOUT, false);
    }

    public UserView me(long userId) {
        UserView userView = viewAssembler.getUserView(userId);
        if (userView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found");
        }
        return userView;
    }

    private Optional<UserAccountEntity> findAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(identifier);
            return userAccountRepository.findById(userId);
        } catch (NumberFormatException ignored) {
            return userAccountRepository.findByAccountName(identifier);
        }
    }

    public TokenService.SessionTokens refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }


    @Transactional
    public void logout(long userId) {
        tokenService.revokeAllSessions(userId, AuthSessionRevokedReason.USER_LOGOUT, false);
    }

    private AuthResult buildAuthResult(TokenService.SessionTokens tokens, long userId) {
        return new AuthResult(
            tokens.accessToken(),
            tokens.accessTokenExpiresIn(),
            tokens.refreshToken(),
            tokens.refreshTokenExpiresIn(),
            tokens.sessionId(),
            viewAssembler.getUserView(userId)
        );
    }

    public record AuthResult(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        String sessionId,
        UserView user
    ) {
        /** Keeps older Java tests source-compatible while JSON exposes accessToken. */
        public String token() {
            return accessToken;
        }
    }
}
