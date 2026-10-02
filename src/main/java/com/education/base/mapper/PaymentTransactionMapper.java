package com.education.base.mapper;

import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.entity.PaymentTransactionEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper riêng của phân hệ giao dịch thanh toán (Stream B).
 * Các trường hiển thị (khoản phí, học sinh, lớp, số tiền đã hoàn) do Service điền thêm.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PaymentTransactionMapper {

    @Mapping(target = "refundedAmount", ignore = true)
    @Mapping(target = "refundableAmount", ignore = true)
    @Mapping(target = "feeCode", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "classId", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    PaymentTransactionDto toDto(PaymentTransactionEntity entity);

    List<PaymentTransactionDto> toDtoList(List<PaymentTransactionEntity> entities);
}
