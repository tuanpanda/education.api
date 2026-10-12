package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.SafeFileType;
import com.education.base.common.homework.HomeworkErrorCodes;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.homework.PortalFileService;
import com.education.base.service.impl.StudentScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GĐ2 - học sinh xem / tải file qua {@code /api/v1/portal/me/files/{id}}. Cho phép: SUBMISSION / FEEDBACK của bài nộp
 * của chính học sinh; ASSIGNMENT của bài học sinh nhìn thấy. Khác -> 404 {@code PORTAL_FILE_NOT_FOUND}.
 */
@Service
@RequiredArgsConstructor
public class PortalFileServiceImpl implements PortalFileService {

    static final String NOSNIFF = "nosniff";
    static final String CSP_SANDBOX = "sandbox";
    static final String CACHE_NO_STORE = "private, no-store";

    private final FileStorageService fileStorageService;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final PortalStudentContext portalStudentContext;
    private final StudentScopeGuard studentScopeGuard;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> serve(Long fileId, boolean download) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        FileEntity file;
        try {
            file = fileStorageService.getFileEntity(fileId);
        } catch (OracleBusinessException e) {
            throw notFound();
        }
        if (!allowed(file, studentId)) {
            throw notFound();
        }
        if (FileConstants.ScanStatus.INFECTED.equals(file.getScanStatus())) {
            throw new OracleBusinessException(FileErrorCodes.FILE_INFECTED, "File có dấu hiệu chứa mã độc.");
        }
        if (FileConstants.ScanStatus.PENDING.equals(file.getScanStatus())) {
            throw new OracleBusinessException(FileErrorCodes.FILE_SCAN_PENDING,
                    "File đang chờ quét virus, vui lòng thử lại sau.");
        }
        Resource resource = fileStorageService.loadResource(file);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", file.getModuleName());
        d.put("refId", file.getReferenceId());
        d.put("size", file.getFileSize());
        d.put("portal", true);
        d.put("download", download);
        auditService.success(AuditActions.FILE_DOWNLOADED, AuditActions.RESOURCE_FILE, file.getId(), d);
        Optional<SafeFileType> type = SafeFileType.forStoredFile(file.getContentType(), file.getOriginalName());
        boolean inline = !download && type.map(SafeFileType::isInlineSafe).orElse(false);
        MediaType contentType = type.map(t -> MediaType.parseMediaType(t.responseContentType()))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(inline ? "inline" : "attachment",
                        file.getOriginalName()))
                .header("X-Content-Type-Options", NOSNIFF)
                .header("Content-Security-Policy", CSP_SANDBOX)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_NO_STORE)
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(resource);
    }

    boolean allowed(FileEntity file, Long studentId) {
        String module = file.getModuleName();
        Long ref = file.getReferenceId();
        if (ref == null || module == null) {
            return false;
        }
        if (FileConstants.Module.SUBMISSION.equals(module) || FileConstants.Module.FEEDBACK.equals(module)) {
            Optional<SubmissionEntity> s = submissionRepository.findById(ref)
                    .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == PersistenceFlags.NOT_DELETED)
                    .filter(x -> Objects.equals(studentId, x.getStudentId()));
            return s.isPresent() && visible(s.get().getAssignmentId());
        }
        if (FileConstants.Module.ASSIGNMENT.equals(module)) {
            return visible(ref);
        }
        return false;
    }

    private boolean visible(Long assignmentId) {
        Optional<AssignmentEntity> a = assignmentRepository.findByIdAndIsDeleted(assignmentId, PersistenceFlags.NOT_DELETED)
                .filter(x -> PortalHomeworkServiceImpl.VISIBLE.contains(x.getStatus()));
        if (a.isEmpty()) {
            return false;
        }
        try {
            studentScopeGuard.requireEnrolled(a.get().getClassId());
            return true;
        } catch (OracleBusinessException e) {
            return false;
        }
    }

    private static String disposition(String type, String originalName) {
        String name = originalName == null || originalName.isBlank() ? "download" : originalName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    private static OracleBusinessException notFound() {
        return new OracleBusinessException(HomeworkErrorCodes.PORTAL_FILE_NOT_FOUND, "Không tìm thấy file.");
    }
}
