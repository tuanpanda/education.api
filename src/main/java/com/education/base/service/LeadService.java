package com.education.base.service;

import com.education.base.dto.request.LeadConvertRequest;
import com.education.base.dto.request.LeadCreateRequest;
import com.education.base.dto.request.LeadFilterRequest;
import com.education.base.dto.request.LeadUpdateRequest;
import com.education.base.dto.response.LeadDetailResponse;
import com.education.base.dto.response.LeadReportDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;

public interface LeadService {

    String MODULE_NAME = "LEAD";

    PageResponse<LeadReportDto> search(LeadFilterRequest filter);

    LeadDetailResponse getDetail(Long id);

    LeadDetailResponse create(LeadCreateRequest request);

    LeadDetailResponse update(Long id, LeadUpdateRequest request);

    void softDelete(Long id);

    /**
     * Chuyển lead thành học sinh trong cùng một transaction (thỏa {@code CK_LEADS_CONVERTED}).
     */
    StudentDetailResponse convert(Long id, LeadConvertRequest request);
}
