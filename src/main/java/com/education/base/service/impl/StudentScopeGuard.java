package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra sở hữu (row-level) cho API cổng học sinh: mọi ID tài nguyên client gửi lên ({@code classId},
 * {@code feeId}...) phải thuộc học sinh của phiên ({@link PortalStudentContext}).
 * <p>
 * Không thuộc -> 404 {@code *_NOT_FOUND} (không lộ sự tồn tại của dữ liệu học sinh khác).
 */
@Component
@RequiredArgsConstructor
public class StudentScopeGuard {

    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";

    public static final String FEE_NOT_FOUND = "FEE_NOT_FOUND";

    private final PortalStudentContext portalStudentContext;
    private final ClassStudentRepository classStudentRepository;
    private final TuitionFeeRepository tuitionFeeRepository;

    /**
     * Học sinh của phiên đang ghi danh ({@code ENROLLED}) vào lớp {@code classId}; trả về bản ghi ghi danh.
     */
    @Transactional(readOnly = true)
    public ClassStudentEntity requireEnrolled(Long classId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        if (classId == null) {
            throw classNotFound();
        }
        return classStudentRepository
                .findByClassIdAndStudentIdAndIsDeleted(classId, studentId, PersistenceFlags.NOT_DELETED)
                .filter(enrollment -> DomainConstants.ENROLLMENT_STATUS_ENROLLED.equals(enrollment.getStatus()))
                .orElseThrow(StudentScopeGuard::classNotFound);
    }

    /**
     * Khoản học phí {@code feeId} thuộc học sinh của phiên (chưa xóa mềm).
     */
    @Transactional(readOnly = true)
    public TuitionFeeEntity requireOwnFee(Long feeId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        if (feeId == null) {
            throw feeNotFound();
        }
        return tuitionFeeRepository.findByIdAndIsDeleted(feeId, PersistenceFlags.NOT_DELETED)
                .filter(fee -> studentId.equals(fee.getStudentId()))
                .orElseThrow(StudentScopeGuard::feeNotFound);
    }

    private static OracleBusinessException classNotFound() {
        return new OracleBusinessException(CLASS_NOT_FOUND, "Không tìm thấy lớp học.");
    }

    private static OracleBusinessException feeNotFound() {
        return new OracleBusinessException(FEE_NOT_FOUND, "Không tìm thấy khoản học phí.");
    }
}
