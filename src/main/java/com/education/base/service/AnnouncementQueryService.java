package com.education.base.service;

import com.education.base.dto.response.PortalAnnouncementDto;

import java.util.List;

/**
 * Truy vấn thông báo cho cổng học sinh / dashboard.
 * <p>
 * {@link #countUnread(Long)} nhận {@code studentUserId} (SYS_USERS.ID của tài khoản học sinh)
 * để Stream A có thể gắn lên dashboard mà không phụ thuộc session portal.
 */
public interface AnnouncementQueryService {

    /** Danh sách thông báo PUBLISHED còn hiệu lực cho học sinh đang đăng nhập. */
    List<PortalAnnouncementDto> listForCurrentStudent();

    /** Đánh dấu đã đọc; chỉ khi thông báo nằm trong phạm vi nhìn thấy của học sinh (chống IDOR). */
    void markRead(Long announcementId);

    /**
     * Số thông báo chưa đọc của tài khoản học sinh {@code studentUserId}.
     * Trả 0 nếu user không có liên kết SELF đang hoạt động.
     */
    long countUnread(Long studentUserId);
}
