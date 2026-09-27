package com.education.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tài khoản thụ hưởng mặc định khi sinh VietQR thanh toán học phí.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    /** Mã BIN NAPAS của ngân hàng nhận tiền. */
    private String bankBin;

    private String accountNo;

    private String accountName;
}
