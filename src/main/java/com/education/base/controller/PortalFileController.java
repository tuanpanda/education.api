package com.education.base.controller;

import com.education.base.security.PortalAccess;
import com.education.base.service.homework.PortalFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GĐ2 - học sinh xem / tải file bài tập. Học sinh không bao giờ đi qua {@code /api/v1/files/**}. */
@RestController
@RequestMapping("/api/v1/portal/me/files")
@RequiredArgsConstructor
@PortalAccess
@Tag(name = "Cổng học sinh - File", description = "Xem / tải file đề bài, bài làm và nhận xét của chính học sinh")
public class PortalFileController {

    private final PortalFileService portalFileService;

    @Operation(summary = "Xem (inline ảnh/PDF) hoặc tải file (download=true)")
    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> serve(@PathVariable("fileId") Long fileId,
                                          @RequestParam(value = "download", required = false, defaultValue = "false")
                                          boolean download) {
        return portalFileService.serve(fileId, download);
    }
}
