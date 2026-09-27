package com.education.base.mapper;

import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassUpdateRequest;
import com.education.base.dto.response.ClassDetailResponse;
import com.education.base.dto.response.ClassOptionResponse;
import com.education.base.dto.response.EnrolledStudentDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ClassMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "teacher", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(target = "grades", ignore = true)
    @Mapping(target = "tuitionFees", ignore = true)
    @Mapping(target = "schedules", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ClassEntity toEntity(ClassCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classCode", ignore = true)
    @Mapping(target = "teacher", ignore = true)
    @Mapping(target = "enrollments", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(target = "grades", ignore = true)
    @Mapping(target = "tuitionFees", ignore = true)
    @Mapping(target = "schedules", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(ClassUpdateRequest request, @MappingTarget ClassEntity entity);

    @Mapping(target = "teacherName", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    ClassDetailResponse toDetail(ClassEntity entity);

    @Mapping(target = "courseName", source = "subjectName")
    ClassOptionResponse toOption(ClassEntity entity);

    @Mapping(target = "enrollmentId", source = "enrollment.id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentCode", source = "student.studentCode")
    @Mapping(target = "fullName", source = "student.fullName")
    @Mapping(target = "email", source = "student.email")
    @Mapping(target = "enrolledAt", source = "enrollment.enrolledAt")
    @Mapping(target = "status", source = "enrollment.status")
    EnrolledStudentDto toEnrolledStudent(ClassStudentEntity enrollment, StudentEntity student);
}
