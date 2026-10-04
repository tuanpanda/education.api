package com.education.base.security;

import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.UserStudentLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Học sinh "của tôi" cho API cổng {@code /api/v1/portal/**}.
 * <p>
 * Học sinh LUÔN được xác định từ người dùng đang đăng nhập qua liên kết {@code SELF} đang hoạt động trong
 * {@code EDU_USER_STUDENT_LINKS} - KHÔNG BAO GIỜ nhận {@code studentId} từ request (chống IDOR).
 */
@Component
@RequiredArgsConstructor
public class PortalStudentContext {

    public static final String STUDENT_LINK_NOT_FOUND = "STUDENT_LINK_NOT_FOUND";

    private final UserStudentLinkRepository userStudentLinkRepository;

    /**
     * ID học sinh của tài khoản học sinh đang đăng nhập.
     *
     * @throws ForbiddenException      người dùng không phải tài khoản học sinh (403 {@code STUDENT_ONLY}).
     * @throws OracleBusinessException tài khoản chưa được liên kết với học sinh nào (404 {@code STUDENT_LINK_NOT_FOUND}).
     */
    @Transactional(readOnly = true)
    public Long requireCurrentStudentId() {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        if (principal.getUserType() != UserType.STUDENT) {
            throw new ForbiddenException(PermissionInterceptor.STUDENT_ONLY_CODE,
                    "Chức năng này chỉ dành cho tài khoản học sinh.");
        }
        return userStudentLinkRepository.findActiveSelfLinkByUserId(principal.getId())
                .map(UserStudentLinkEntity::getStudentId)
                .orElseThrow(() -> new OracleBusinessException(STUDENT_LINK_NOT_FOUND,
                        "Tài khoản chưa được liên kết với học sinh nào. Vui lòng liên hệ trung tâm."));
    }
}
