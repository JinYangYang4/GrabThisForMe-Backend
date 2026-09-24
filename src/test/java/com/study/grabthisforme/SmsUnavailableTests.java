package com.study.grabthisforme;
import static org.assertj.core.api.Assertions.*;
import com.study.grabthisforme.service.PasswordRecoveryService;
import com.study.grabthisforme.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class SmsUnavailableTests {
    @Autowired PasswordRecoveryService recovery;
    @Test void missingProviderNeverPretendsToSend() {
        assertThatThrownBy(()->recovery.request("someone","13900000000"))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(503));
    }
}
