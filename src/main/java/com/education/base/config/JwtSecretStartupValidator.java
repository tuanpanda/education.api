package com.education.base.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Chặn ứng dụng khởi động khi JWT secret yếu (ngắn hơn 32 byte) hoặc khi profile {@code prod}
 * dùng secret dev/test đã công khai (xem {@link JwtSecretPolicy}).
 */
@Slf4j
@Component
public class JwtSecretStartupValidator implements InitializingBean {

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretStartupValidator(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = environment.acceptsProfiles(Profiles.of(JwtSecretPolicy.PROD_PROFILE));
        JwtSecretPolicy.validate(properties.getSecret(), prod);
        if (!prod && JwtSecretPolicy.isKnownNonProductionSecret(properties.getSecret())) {
            log.warn("Đang dùng JWT secret mặc định cho dev/test. KHÔNG dùng cấu hình này cho môi trường thật.");
        }
    }
}
