package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.common.excel.StudentExcelHelper;
import com.education.base.dto.response.StudentImportResultResponse;
import com.education.base.service.StudentImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Import hàng loạt học sinh từ Excel và tải file mẫu.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Quản lý Học sinh", description = "Import danh sách học sinh từ Excel")
public class StudentImportController {

    private final StudentImportService studentImportService;

    @Operation(summary = "Tải file Excel mẫu import học sinh",
            description = "File .xlsx gồm sheet DanhSach (header nổi bật, dropdown Trạng thái, 2 dòng mẫu) "
                    + "và sheet HuongDan.")
    @GetMapping("/import-template")
    @RequirePermission(Permissions.STUDENT_IMPORT)
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] content = studentImportService.generateTemplate();
        ByteArrayResource resource = new ByteArrayResource(content);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(StudentExcelHelper.TEMPLATE_FILE_NAME, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StudentExcelHelper.XLSX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.length)
                .body(resource);
    }

    @Operation(summary = "Import học sinh từ Excel",
            description = "Chỉ nhận .xlsx. Lưu các dòng hợp lệ; dòng lỗi được trả về chi tiết "
                    + "(số dòng, cột, lý do). File gốc lưu tại outputs/STUDENT/IMPORT/{YYYY}/{MM}/.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.STUDENT_IMPORT)
    public ApiResponse<StudentImportResultResponse> importStudents(
            @RequestParam("file") MultipartFile file) {
        StudentImportResultResponse result = studentImportService.importStudents(file);
        String message = "Import hoàn tất: thành công " + result.getSuccessCount()
                + "/" + result.getTotalRows() + " dòng.";
        return ApiResponse.success(message, result);
    }
}
