package com.study.grabthisforme.service.sms;
/** Implement with a real provider, bounded timeouts and provider-side abuse protection. Never log the code. */
public interface SmsVerificationSender {
    boolean available();
    void send(String phone, String code);
}
