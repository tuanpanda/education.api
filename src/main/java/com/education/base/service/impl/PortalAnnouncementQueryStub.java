package com.education.base.service.impl;

import com.education.base.service.PortalAnnouncementQuery;
import org.springframework.stereotype.Service;

/**
 * Stub Phase 1 Stream A: chưa có {@code EDU_ANNOUNCEMENTS}.
 * TODO(Stream B): thay bằng implementation đọc bảng thông báo + trạng thái đã đọc.
 */
@Service
public class PortalAnnouncementQueryStub implements PortalAnnouncementQuery {

    @Override
    public long countUnreadForStudent(Long studentId) {
        // TODO Stream B: EDU_ANNOUNCEMENTS — trả số chưa đọc thật
        return 0L;
    }
}
