package com.education.base.service;

import com.education.base.dto.request.StudentAccountFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentAccountBulkResultDto;
import com.education.base.dto.response.StudentAccountCredentialDto;
import com.education.base.dto.response.StudentAccountDto;
import com.education.base.dto.response.StudentAccountStatusDto;

import java.util.List;

/**
 * Quản trị tài khoản học sinh (nhân viên): tìm kiếm, tạo hàng loạt, đặt lại mật khẩu, khóa / mở khóa.
 * Chỉ thao tác trên tài khoản {@code USER_TYPE = STUDENT}.
 */
public interface StudentAccountService {

    PageResponse<StudentAccountDto> search(StudentAccountFilterRequest filter);

    /**
     * Tạo tài khoản cho từng học sinh (bỏ qua học sinh đã có tài khoản / không hợp lệ), theo thứ tự đầu vào,
     * bỏ ID trùng. Cả lô chạy trong MỘT giao dịch.
     */
    List<StudentAccountBulkResultDto> bulkCreate(List<Long> studentIds);

    StudentAccountCredentialDto resetPassword(Long userId);

    StudentAccountStatusDto lock(Long userId);

    StudentAccountStatusDto unlock(Long userId);
}
