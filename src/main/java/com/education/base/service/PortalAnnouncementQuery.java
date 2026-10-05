package com.education.base.service;

/**
 * Đếm thông báo chưa đọc cho dashboard cổng.
 * <p>
 * Stream A cung cấp stub trả {@code 0}. Stream B triển khai thật khi có
 * {@code EDU_ANNOUNCEMENTS} (thay bean mặc định).
 */
public interface PortalAnnouncementQuery {

    /**
     * Số thông báo chưa đọc của học sinh {@code studentId}.
     * Trả {@code 0} nếu bảng / module thông báo chưa sẵn sàng.
     */
    long countUnreadForStudent(Long studentId);
}
