package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Danh sách đích chia sẻ người dùng hiện tại được chọn ({@code GET /api/v1/documents/share-targets}).
 * Người chỉ có UPLOAD (không SHARE/MANAGE): {@code allStaffAllowed=false}, {@code roles} rỗng, {@code classes} = lớp mình
 * dạy. Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocShareTargetsDto {

    private boolean allStaffAllowed;
    @Builder.Default
    private List<Option> roles = List.of();
    @Builder.Default
    private List<Option> classes = List.of();

    /** Một lựa chọn đích. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Option {
        private Long id;
        private String code;
        private String name;
    }
}
