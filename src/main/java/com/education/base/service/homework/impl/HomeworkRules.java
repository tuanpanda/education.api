package com.education.base.service.homework.impl;

import com.education.base.common.homework.HomeworkConstants;
import com.education.base.common.homework.HomeworkConstants.AssignmentStatus;
import com.education.base.common.homework.HomeworkConstants.MatrixState;
import com.education.base.common.homework.HomeworkConstants.PortalTab;
import com.education.base.common.homework.HomeworkConstants.SubmissionStatus;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.SubmissionEntity;

import java.time.Clock;
import java.time.LocalDateTime;

/** GĐ2 - luật hạn nộp / trạng thái (thuần, không truy cập DB). Giờ luôn theo {@link HomeworkConstants#ZONE}. */
final class HomeworkRules {

    private HomeworkRules() {
    }

    static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(HomeworkConstants.ZONE));
    }

    static boolean flag(Integer v) {
        return v != null && v == 1;
    }

    static int flag(Boolean v) {
        return Boolean.TRUE.equals(v) ? 1 : 0;
    }

    static String statusOf(SubmissionEntity s) {
        return s == null ? null : s.getStatus();
    }

    /** Còn trong khung nộp (đúng hạn hoặc trong hạn muộn); bài RETURNED được nộp lại tới khi bài CLOSED. */
    static boolean withinWindow(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        if (AssignmentStatus.CLOSED.equals(a.getStatus())) {
            return false;
        }
        if (s != null && SubmissionStatus.RETURNED.equals(s.getStatus())) {
            return true;
        }
        return onTimeOrLateAllowed(a, now);
    }

    /** Luật hạn nộp thuần (không xét ngoại lệ RETURNED). */
    static boolean onTimeOrLateAllowed(AssignmentEntity a, LocalDateTime now) {
        if (!now.isAfter(a.getDueAt())) {
            return true;
        }
        return flag(a.getAllowLate()) && (a.getLateUntil() == null || !now.isAfter(a.getLateUntil()));
    }

    static boolean editableStatus(SubmissionEntity s) {
        String st = statusOf(s);
        return st == null || SubmissionStatus.DRAFT.equals(st) || SubmissionStatus.RETURNED.equals(st);
    }

    static boolean canEdit(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        return editableStatus(s) && withinWindow(a, s, now);
    }

    static boolean late(AssignmentEntity a, LocalDateTime now) {
        return now.isAfter(a.getDueAt());
    }

    /** Tab cổng: mỗi bài đúng 1 tab. RETURNED nhưng bài đã CLOSED (không nộp lại được) -> overdue. */
    static String tab(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        String st = statusOf(s);
        if (SubmissionStatus.SUBMITTED.equals(st)) {
            return PortalTab.SUBMITTED;
        }
        if (SubmissionStatus.GRADED.equals(st)) {
            return PortalTab.GRADED;
        }
        return canEdit(a, s, now) ? PortalTab.TODO : PortalTab.OVERDUE;
    }

    /** Trạng thái ô ma trận (tính, không lưu). */
    static String matrixState(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        String st = statusOf(s);
        if (st == null || SubmissionStatus.DRAFT.equals(st)) {
            return now.isAfter(a.getDueAt()) ? MatrixState.MISSING : MatrixState.NOT_SUBMITTED;
        }
        if (SubmissionStatus.SUBMITTED.equals(st)) {
            return flag(s.getIsLate()) ? MatrixState.LATE : MatrixState.SUBMITTED;
        }
        if (SubmissionStatus.RETURNED.equals(st)) {
            return MatrixState.RETURNED;
        }
        return MatrixState.GRADED;
    }
}
