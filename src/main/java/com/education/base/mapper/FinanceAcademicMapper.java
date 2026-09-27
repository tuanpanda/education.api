package com.education.base.mapper;

import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface FinanceAcademicMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "clazz", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    TuitionFeeEntity toFeeEntity(TuitionFeeCreateRequest request);

    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    TuitionFeeReportDto toFeeReport(TuitionFeeEntity entity);

    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    GradeResponseDto toGradeDto(GradeEntity entity);

    PaymentTransactionDto toPaymentDto(PaymentTransactionEntity entity);
}
