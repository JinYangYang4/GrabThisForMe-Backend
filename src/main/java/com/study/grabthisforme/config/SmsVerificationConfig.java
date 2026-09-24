package com.study.grabthisforme.config;
import com.study.grabthisforme.service.sms.SmsVerificationSender;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration
public class SmsVerificationConfig {
    @Bean @ConditionalOnMissingBean(SmsVerificationSender.class)
    public SmsVerificationSender unavailableSmsSender() {
        return new SmsVerificationSender() {
            public boolean available() { return false; }
            public void send(String phone,String code) { throw new IllegalStateException("SMS provider is not configured"); }
        };
    }
}
