package com.education.base.service;

import com.education.base.dto.response.StudentImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import hàng loạt học sinh từ Excel và sinh file mẫu.
 */
public interface StudentImportService {

    byte[] generateTemplate();

    StudentImportResultResponse importStudents(MultipartFile file);
}
