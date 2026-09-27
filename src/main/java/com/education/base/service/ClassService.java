package com.education.base.service;

import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.request.ClassUpdateRequest;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.response.ClassDetailResponse;
import com.education.base.dto.response.ClassOptionResponse;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.EnrolledStudentDto;
import com.education.base.dto.response.NextClassCodeResponse;
import com.education.base.dto.response.PageResponse;

import java.util.List;

/**
 * Nghiệp vụ phân hệ Đào tạo - quản lý lớp học.
 */
public interface ClassService {

    String MODULE_NAME = "CLASS";

    PageResponse<ClassReportDto> search(ClassFilterRequest filter);

    ClassDetailResponse getDetail(Long id);

    ClassDetailResponse create(ClassCreateRequest request);

    ClassDetailResponse update(Long id, ClassUpdateRequest request);

    void softDelete(Long id);

    List<EnrolledStudentDto> enroll(Long classId, EnrollStudentsRequest request);

    void unenroll(Long classId, Long studentId);

    List<ClassOptionResponse> listOpenOptions();

    /**
     * Xem trước mã lớp kế tiếp theo quy luật {@code SYS_CODE_RULES} (RULE_CODE = CLASS).
     * Không gọi {@code FN_NEXT_BIZ_CODE} nên không tăng số thứ tự.
     */
    NextClassCodeResponse peekNextClassCode(Integer gradeLevel);
}
