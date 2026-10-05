package com.education.base.service;

import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;

import java.util.List;

/**
 * Nghiệp vụ quản trị thông báo ({@code /api/v1/announcements}).
 * <p>
 * Giáo viên ({@code ROLE_TEACHER}, không phải {@code ROLE_ADMIN}) chỉ được tạo/sửa/đăng/lưu trữ/xóa
 * thông báo phạm vi {@code CLASS} cho lớp mình dạy; danh sách gồm thêm thông báo {@code ALL} (chỉ đọc).
 */
public interface AnnouncementService {

    PageResponse<AnnouncementDto> search(AnnouncementFilterRequest filter);

    AnnouncementDto getById(Long id);

    AnnouncementDto create(AnnouncementUpsertRequest request);

    AnnouncementDto update(Long id, AnnouncementUpsertRequest request);

    AnnouncementDto publish(Long id);

    AnnouncementDto archive(Long id);

    void softDelete(Long id);

    /**
     * Lớp được phép chọn khi tạo/sửa thông báo CLASS.
     * Giáo viên bị giới hạn: lớp chủ nhiệm hoặc có buổi dạy; nhân viên khác: danh sách rỗng
     * (UI dùng {@code /api/v1/classes/search}).
     */
    List<AnnouncementClassOptionDto> listManageableClasses();
}