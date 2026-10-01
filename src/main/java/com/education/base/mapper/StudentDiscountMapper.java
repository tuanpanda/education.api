package com.education.base.mapper;

import com.education.base.dto.request.StudentDiscountUpsertRequest;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.entity.StudentDiscountEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct cho miễn giảm - học bổng ({@code FIN_STUDENT_DISCOUNTS}).
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface StudentDiscountMapper {

    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    StudentDiscountDto toDto(StudentDiscountEntity entity);

    /** Chép các trường nghiệp vụ của request vào entity (id / cờ xóa / audit giữ nguyên). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void apply(StudentDiscountUpsertRequest request, @MappingTarget StudentDiscountEntity entity);
}
