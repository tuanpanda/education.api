package com.education.base.service.homework.impl;

import com.education.base.common.homework.HomeworkConstants;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.SubmissionEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/** Dữ liệu dựng sẵn cho test GĐ2. Mốc: hạn nộp 10/10/2026 17:00 (giờ VN). */
public final class HomeworkTestData {

    public static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 10, 17, 0);

    private HomeworkTestData() {
    }

    public static Clock clockAt(LocalDateTime vnTime) {
        return Clock.fixed(vnTime.atZone(HomeworkConstants.ZONE).toInstant(), HomeworkConstants.ZONE);
    }

    public static AssignmentEntity assignment(long id, long classId, String status, String type) {
        return AssignmentEntity.builder()
                .id(id).classId(classId).title("Bai " + id).description("<p>De</p>")
                .submissionType(type).maxScore(BigDecimal.TEN).dueAt(DUE)
                .allowLate(0).maxFiles(3).allowResubmit(1).syncToGradebook(0).gradeWeight(BigDecimal.ONE)
                .status(status).version(0L).isDeleted(0).createdAt(DUE.minusDays(7))
                .build();
    }

    public static SubmissionEntity submission(long id, long assignmentId, long studentId, String status) {
        return SubmissionEntity.builder()
                .id(id).assignmentId(assignmentId).studentId(studentId).status(status)
                .attemptCount(0).isLate(0).version(3L).isDeleted(0).createdAt(DUE.minusDays(1))
                .build();
    }

    public static FileEntity file(long id, String module, long ref) {
        return FileEntity.builder().id(id).moduleName(module).referenceId(ref).originalName("bai.pdf")
                .contentType("application/pdf").fileSize(100L).fileExt("pdf").status("ACTIVE").isDeleted(0)
                .scanStatus("SKIPPED").build();
    }
}
