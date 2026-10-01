package com.education.base.mapper;

import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.entity.PaymentTransactionEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

/**
 * Mapper riêng của phân hệ giao dịch thanh toán (Stream B).
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PaymentTransactionMapper {

    PaymentTransactionDto toDto(PaymentTransactionEntity entity);
}
