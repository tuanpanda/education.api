package com.education.base.mapper;

import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.entity.StudentEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Chuyển đổi giữa {@link StudentEntity} và các DTO của Module Quản lý Học sinh.
 * <p>
 * Các cột do hệ thống / CSDL quản lý ({@code ID}, {@code STUDENT_CODE}, {@code IS_DELETED}, audit)
 * luôn được bỏ qua khi map từ request. {@code ID} do sequence/trigger;
 * {@code STUDENT_CODE} do {@code FN_NEXT_BIZ_CODE} theo {@code SYS_CODE_RULES}.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface StudentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(target = "grades", ignore = true)
    @Mapping(target = "tuitionFees", ignore = true)
    @Mapping(target = "convertedFromLeads", ignore = true)
    StudentEntity toEntity(StudentCreateRequest request);

    /**
     * Áp thông tin cập nhật lên entity đang được quản lý bởi JPA.
     * Ngữ nghĩa PUT: trường {@code email} client bỏ trống sẽ được ghi thành {@code null}.
     * {@code classId} do Service ghi danh, không map lên entity.
     */
    @BeanMapping(ignoreUnmappedSourceProperties = "classId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "studentCode", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(target = "grades", ignore = true)
    @Mapping(target = "tuitionFees", ignore = true)
    @Mapping(target = "convertedFromLeads", ignore = true)
    void updateEntity(StudentUpdateRequest request, @MappingTarget StudentEntity entity);

    /**
     * Map thông tin cơ bản; danh sách {@code attachments} do Service nạp riêng
     * qua Standalone Procedure {@code PRC_GET_FILES_BY_REF}.
     * Thông tin lớp ({@code classId}/{@code classCode}/{@code className}) và
     * số buổi điểm danh do Service gán.
     */
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "classId", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "className", ignore = true)
    @Mapping(target = "attendedSessionCount", ignore = true)
    @Mapping(target = "attendanceMarkedCount", ignore = true)
    @Mapping(target = "attendanceSessions", ignore = true)
    StudentDetailResponse toDetail(StudentEntity entity);
}
