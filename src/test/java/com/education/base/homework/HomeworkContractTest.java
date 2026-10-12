package com.education.base.homework;

import com.education.base.common.file.FileConstants;
import com.education.base.common.homework.HomeworkConstants;
import com.education.base.common.homework.HomeworkErrorCodes;
import com.education.base.security.Permissions;
import com.education.base.service.file.AssignmentFileModule;
import com.education.base.service.file.FeedbackFileModule;
import com.education.base.service.file.SubmissionFileModule;
import com.education.base.common.file.FileModuleDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Khoá hợp đồng GĐ2 (Luồng 0). Bot 1 sửa test stub khi viết luật thật. */
class HomeworkContractTest {

    @Test
    void permissionsMatchV20_3() {
        assertThat(List.of(Permissions.HOMEWORK_VIEW, Permissions.HOMEWORK_CREATE, Permissions.HOMEWORK_UPDATE,
                Permissions.HOMEWORK_DELETE, Permissions.HOMEWORK_GRADE, Permissions.HOMEWORK_EXPORT))
                .containsExactly("MENU_HOMEWORK:VIEW", "MENU_HOMEWORK:CREATE", "MENU_HOMEWORK:UPDATE",
                        "MENU_HOMEWORK:DELETE", "MENU_HOMEWORK:GRADE", "MENU_HOMEWORK:EXPORT");
    }

    @Test
    void constantsAreStable() {
        assertThat(HomeworkConstants.ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(HomeworkConstants.GRADEBOOK_GRADE_TYPE).isEqualTo("ASSIGNMENT");
        assertThat(HomeworkConstants.PortalTab.PATTERN).isEqualTo("todo|submitted|graded|overdue");
        assertThat(HomeworkErrorCodes.ASSIGNMENT_NOT_FOUND).endsWith("_NOT_FOUND");
        assertThat(HomeworkErrorCodes.SUBMISSION_NOT_FOUND).endsWith("_NOT_FOUND");
        assertThat(HomeworkErrorCodes.PORTAL_FILE_NOT_FOUND).endsWith("_NOT_FOUND");
    }

    /** Bot 1 (GĐ2 BE): 3 descriptor đã có luật thật và là bean; chi tiết luật ở HomeworkFileModulesTest. */
    @Test
    void homeworkDescriptorsAreBeansWithoutGenericUpload() {
        for (Class<?> c : List.of(AssignmentFileModule.class, SubmissionFileModule.class, FeedbackFileModule.class)) {
            assertThat(c.isAnnotationPresent(Component.class)).as(c.getSimpleName()).isTrue();
            assertThat(FileModuleDescriptor.class.isAssignableFrom(c)).isTrue();
        }
        assertThat(List.of(FileConstants.Module.ASSIGNMENT, FileConstants.Module.SUBMISSION, FileConstants.Module.FEEDBACK))
                .containsExactly("ASSIGNMENT", "SUBMISSION", "FEEDBACK");
    }
}
