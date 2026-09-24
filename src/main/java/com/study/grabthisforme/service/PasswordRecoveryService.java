package com.study.grabthisforme.service;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.auth.PasswordService;
import com.study.grabthisforme.auth.TokenService;
import com.study.grabthisforme.persistence.entity.AuthSessionRevokedReason;
import com.study.grabthisforme.persistence.entity.PasswordResetChallenge;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.service.sms.SmsVerificationSender;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.UUID;
@Service
public class PasswordRecoveryService {
    private final PasswordResetRepository resets;
    private final UserAccountRepository accounts;
    private final UserProfileRepository profiles;
    private final PasswordService passwords;
    private final SmsVerificationSender sms;
    private final TokenService tokens;
    private final SecureRandom random=new SecureRandom();
    public PasswordRecoveryService(PasswordResetRepository resets,UserAccountRepository accounts,
        UserProfileRepository profiles,PasswordService passwords,SmsVerificationSender sms,TokenService tokens) {
        this.resets=resets;this.accounts=accounts;this.profiles=profiles;this.passwords=passwords;this.sms=sms;this.tokens=tokens;
    }
    public record Challenge(String challengeId,int retryAfterSeconds,int expiresInSeconds) {}
    // Serializes sends on a single instance; provider must enforce deployment-wide SMS quotas too.
    public synchronized Challenge request(String identifier,String phone) {
        if (!sms.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,50311,"短信服务尚未配置，请稍后再试");
        long now=System.currentTimeMillis();
        if (resets.countByPhoneAndCreatedAtGreaterThan(phone,now-60000)>0
            || resets.countByPhoneAndCreatedAtGreaterThan(phone,now-3600000)>=5)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,42911,"验证码请求过于频繁");
        var account=accounts.findByAccountName(identifier);
        if (account.isEmpty()) { try { account=accounts.findById(Long.parseLong(identifier)); } catch(NumberFormatException ignored) {} }
        Long userId=null;
        if (account.isPresent()) {
            var profile=profiles.findById(account.get().userId);
            if (profile.isPresent() && phone.equals(profile.get().phone)) userId=account.get().userId;
        }
        String code=String.format("%06d",random.nextInt(1000000));
        PasswordResetChallenge c=new PasswordResetChallenge(); c.challengeId=UUID.randomUUID().toString();
        c.userId=userId;
        c.tokenVersion=account.map(a -> a.tokenVersion==null?0L:a.tokenVersion).orElse(null);
        c.phone=phone;c.createdAt=now;c.expiresAt=now+300000;c.codeHash=passwords.hash(code);
        resets.saveAndFlush(c);
        if (userId!=null) sms.send(phone,code);
        return new Challenge(c.challengeId,60,300);
    }
    // Persist failed attempts even when rejecting a code.
    @Transactional(noRollbackFor=ApiException.class)
    public void reset(String id,String code,String newPassword) {
        var c=resets.findForUpdate(id).orElseThrow(PasswordRecoveryService::invalid);
        if(c.consumed || c.expiresAt<=System.currentTimeMillis() || c.attempts>=5) throw invalid();
        c.attempts++;
        if(c.userId==null || !passwords.matches(code,c.codeHash)) { resets.save(c); throw invalid(); }
        var a=accounts.findForUpdate(c.userId).orElseThrow(PasswordRecoveryService::invalid);
        if(c.tokenVersion==null || !c.tokenVersion.equals(a.tokenVersion==null?0L:a.tokenVersion)) throw invalid();
        a.passwordHash=passwords.hash(newPassword);a.tokenVersion=(a.tokenVersion==null?0:a.tokenVersion)+1;
        accounts.save(a);c.consumed=true;resets.save(c);
        tokens.revokeAllSessions(a.userId,AuthSessionRevokedReason.PASSWORD_RESET,true);
    }
    private static ApiException invalid() {return new ApiException(HttpStatus.BAD_REQUEST,40081,"验证码无效或已过期，请重新获取");}
}
