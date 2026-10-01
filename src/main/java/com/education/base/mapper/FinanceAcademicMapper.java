package com.education.base.mapper;

import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeListItemDto;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

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
    @Mapping(target = "cancelReason", ignore = true)
    TuitionFeeEntity toFeeEntity(TuitionFeeCreateRequest request);

    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    TuitionFeeReportDto toFeeReport(TuitionFeeEntity entity);

    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "studentStatus", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    TuitionFeeListItemDto toFeeListItem(TuitionFeeEntity entity);

    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    GradeResponseDto toGradeDto(GradeEntity entity);

    /**
     * Trường cùng tên tự map. Trường chỉ có ở DTO (Stream B thêm cho giao dịch / phiếu thu, ví dụ thông tin
     * hiển thị tính ở tầng Service) được bỏ qua thay vì cảnh báo "unmapped target" khi build.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PaymentTransactionDto toPaymentDto(PaymentTransactionEntity entity);
}
