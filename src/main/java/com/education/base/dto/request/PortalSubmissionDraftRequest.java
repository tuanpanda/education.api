package com.education.base.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code PUT /api/v1/portal/me/assignments/{id}/submission}: lưu nháp văn bản (không nộp). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSubmissionDraftRequest {

    /** HTML hoặc text thuần; backend sanitize. NULL/rỗng = xoá nội dung nháp. */
    @Size(max = 50000, message = "Bài làm tối đa 50.000 ký tự")
    private String contentText;
}
