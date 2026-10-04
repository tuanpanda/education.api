package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.StudentAccountBulkRequest;
import com.education.base.dto.request.StudentAccountFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentAccountBulkResultDto;
import com.education.base.dto.response.StudentAccountCredentialDto;
import com.education.base.dto.response.StudentAccountDto;
import com.education.base.dto.response.StudentAccountStatusDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.StudentAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Màn hình quản trị "Tài khoản học sinh" (menu {@code MENU_STUDENT_ACCOUNT}, V17_2): nhân viên tạo hàng loạt
 * tài khoản cho học sinh, đặt lại mật khẩu, khóa / mở khóa. Mật khẩu tạm chỉ trả về MỘT lần.
 */
@RestController
@RequestMapping("/api/v1/student-accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tài khoản học sinh", description = "Cấp và quản lý tài khoản đăng nhập cổng học sinh")
public class StudentAccountController {

    private final StudentAccountService studentAccountService;

    @Operation(summary = "Tìm học sinh kèm trạng thái tài khoản (phân trang)",
            description = "accountStatus: NO_ACCOUNT / NEVER_LOGGED_IN / ACTIVE / LOCKED / INACTIVE.")
    @GetMapping("/search")
    @RequirePermission(Permissions.STUDENT_ACCOUNT_VIEW)
    public ApiResponse<PageResponse<StudentAccountDto>> search(@Valid @ModelAttribute StudentAccountFilterRequest filter) {
        return ApiResponse.success(studentAccountService.search(filter));
    }

    @Operation(summary = "Tạo hàng loạt tài khoản học sinh",
            description = "Tên đăng nhập hs00001, hs00002...; mật khẩu tạm ngẫu nhiên chỉ trả về MỘT lần, buộc đổi ở "
                    + "lần đăng nhập đầu. Học sinh đã có tài khoản bị bỏ qua (SKIPPED).")
    @PostMapping("/bulk")
    @RequirePermission(Permissions.STUDENT_ACCOUNT_CREATE)
    public ApiResponse<List<StudentAccountBulkResultDto>> bulkCreate(@Valid @RequestBody StudentAccountBulkRequest request) {
        return ApiResponse.success("Đã xử lý tạo tài khoản học sinh.",
                studentAccountService.bulkCreate(request.getStudentIds()));
    }

    @Operation(summary = "Đặt lại mật khẩu tài khoản học sinh",
            description = "Sinh mật khẩu tạm mới (trả về MỘT lần), buộc đổi khi đăng nhập, thu hồi mọi phiên.")
    @PostMapping("/{userId}/reset-password")
    @RequirePermission(Permissions.STUDENT_ACCOUNT_RESET_PASSWORD)
    public ApiResponse<StudentAccountCredentialDto> resetPassword(@PathVariable("userId") Long userId) {
        return ApiResponse.success("Đã đặt lại mật khẩu.", studentAccountService.resetPassword(userId));
    }

    @Operation(summary = "Khóa tài khoản học sinh", description = "Thu hồi mọi phiên đăng nhập.")
    @PostMapping("/{userId}/lock")
    @RequirePermission(Permissions.STUDENT_ACCOUNT_LOCK)
    public ApiResponse<StudentAccountStatusDto> lock(@PathVariable("userId") Long userId) {
        return ApiResponse.success("Đã khóa tài khoản.", studentAccountService.lock(userId));
    }

    @Operation(summary = "Mở khóa tài khoản học sinh", description = "Gỡ cả khóa tạm thời do sai mật khẩu nhiều lần.")
    @PostMapping("/{userId}/unlock")
    @RequirePermission(Permissions.STUDENT_ACCOUNT_LOCK)
    public ApiResponse<StudentAccountStatusDto> unlock(@PathVariable("userId") Long userId) {
        return ApiResponse.success("Đã mở khóa tài khoản.", studentAccountService.unlock(userId));
    }
}
