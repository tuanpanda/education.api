package com.education.base.service;

import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;

/**
 * Nghiệp vụ quản trị thông báo ({@code /api/v1/announcements}).
 */
public interface AnnouncementService {

    PageResponse<AnnouncementDto> search(AnnouncementFilterRequest filter);

    AnnouncementDto getById(Long id);

    AnnouncementDto create(AnnouncementUpsertRequest request);

    AnnouncementDto update(Long id, AnnouncementUpsertRequest request);

    AnnouncementDto publish(Long id);

    AnnouncementDto archive(Long id);

    void softDelete(Long id);
}
