package com.study.grabthisforme.config;

import com.study.grabthisforme.persistence.entity.CouponTemplateEntity;
import com.study.grabthisforme.persistence.repository.CouponTemplateRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CouponDataSeeder implements ApplicationRunner {
    private final CouponTemplateRepository repository;

    public CouponDataSeeder(CouponTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
            new CouponTemplateEntity(
                5001L, "\u6ee1100\u51cf20\u5238", "\u5168\u573a\u5e97\u94fa\u5546\u54c1\u6ee1100\u5143\u53ef\u7528", 20.0, 100.0,
                3.99, 30, null, 1000, 0L, 3, true
            ),
            new CouponTemplateEntity(
                5002L, "\u6ee150\u51cf10\u5238", "\u5168\u573a\u5e97\u94fa\u5546\u54c1\u6ee150\u5143\u53ef\u7528", 10.0, 50.0,
                1.99, 20, null, 1500, 0L, 3, true
            ),
            new CouponTemplateEntity(
                5003L, "\u65e0\u95e8\u69db5\u5143\u5238", "\u4efb\u610f\u5e97\u94fa\u5546\u54c1\u8ba2\u5355\u53ef\u7528", 5.0, 0.0,
                0.99, 10, null, 2000, 0L, 2, true
            )
        ));
    }
}
