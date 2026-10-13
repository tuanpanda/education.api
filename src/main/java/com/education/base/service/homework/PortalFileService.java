package com.education.base.service.homework;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

/**
 * {@code GET /api/v1/portal/me/files/{id}[?download=true]}: file học sinh được xem trên cổng.
 * Cho phép: SUBMISSION/FEEDBACK của bài nộp của chính học sinh; ASSIGNMENT của bài PUBLISHED/CLOSED thuộc lớp đang
 * ENROLLED. Mọi trường hợp khác (kể cả bạn cùng lớp) -> 404 {@code PORTAL_FILE_NOT_FOUND}. Ghi audit FILE_DOWNLOADED.
 * GĐ3 có thể mở rộng cho LIBRARY chia sẻ lớp (visibleToStudents).
 */
public interface PortalFileService {

    ResponseEntity<Resource> serve(Long fileId, boolean download);
}
