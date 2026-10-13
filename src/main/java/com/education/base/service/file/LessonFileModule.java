package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.repository.LessonRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.service.lesson.impl.LessonFileAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Module file {@code LESSON}: tài liệu bài học (REFERENCE_ID = LMS_LESSONS.ID nơi file được tải lên; quyền đọc tính theo
 * MỌI dòng LMS_LESSON_RESOURCES chưa xoá có FILE_ID này, vì bài chép từ khoá mẫu dùng chung file). docs/portal-p3-api.md §4.
 * <ul>
 *   <li>Đọc: MENU_LESSON:VIEW hoặc MENU_COURSE:VIEW; nếu mọi bài dùng file đều thuộc lớp thì cần phạm vi giáo viên
 *       ({@link TeacherFileScope#classAllowed}) ở ít nhất một lớp.</li>
 *   <li>Upload/xoá qua /api/v1/files: không (chỉ qua API học liệu).</li>
 *   <li>assertReferenceUsable: bài tồn tại, chưa xoá. describeReference: "Bài học: &lt;tiêu đề&gt;".</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LessonFileModule extends AbstractFileModule {

    private final LessonRepository lessonRepository;
    private final LessonFileAccess lessonFileAccess;
    private final TeacherFileScope teacherFileScope;

    @Override
    public String code() {
        return FileConstants.Module.LESSON;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        if (file == null || !any(user, List.of(Permissions.LESSON_VIEW, Permissions.COURSE_VIEW))) {
            return false;
        }
        List<LessonEntity> lessons = new ArrayList<>(lessonFileAccess.lessonsUsingFile(file.getId()));
        if (lessons.isEmpty() && file.getReferenceId() != null) {
            lessonRepository.findById(file.getReferenceId()).ifPresent(lessons::add);
        }
        if (lessons.isEmpty()) {
            return false;
        }
        if (lessons.stream().anyMatch(l -> l.getClassId() == null)) {
            return true;
        }
        return lessons.stream().anyMatch(l -> teacherFileScope.classAllowed(l.getClassId()));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return false;
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return false;
    }

    @Override
    public boolean allowGenericUpload() {
        return false;
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        LessonEntity l = referenceId == null ? null : lessonRepository.findById(referenceId).orElse(null);
        if (l == null) {
            throw referenceNotFound(code(), referenceId);
        }
        if (deleted(l)) {
            throw referenceInactive(code(), referenceId);
        }
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(code());
        }
        return lessonRepository.findById(referenceId)
                .map(l -> new FileReferenceInfo(code(), referenceId, "Bài học: " + l.getTitle(),
                        "/academic/lessons?lesson=" + referenceId,
                        deleted(l) ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }

    private static boolean deleted(LessonEntity l) {
        return l.getIsDeleted() != null && l.getIsDeleted() != 0;
    }
}
