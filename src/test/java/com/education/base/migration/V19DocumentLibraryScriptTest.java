package com.education.base.migration;

import com.education.base.common.document.DocumentConstants;
import com.education.base.common.file.FileConstants;
import com.education.base.security.Permissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V19_4 kho tai lieu (QD-F6 = lam ngay). Chi Luong D sua lop nay.
 */
class V19DocumentLibraryScriptTest {

    @Test
    void createsTreeItemsAndSharesWithIntegrityConstraints() throws IOException {
        String upper = V19FilesScriptTest.code(V19FilesScriptTest.read(V19FilesScriptTest.V19_4))
                .toUpperCase(Locale.ROOT);
        assertThat(upper).contains("CREATE TABLE DOC_FOLDERS")
                .contains("CREATE TABLE DOC_ITEMS")
                .contains("CREATE TABLE DOC_SHARES")
                .contains("SEQ_DOC_FOLDERS").contains("SEQ_DOC_ITEMS").contains("SEQ_DOC_SHARES")
                .contains("TREE_PATH")
                .contains("UQ_DOC_ITEMS_FILE UNIQUE (FILE_ID)")
                .contains("REFERENCES SYS_ATTACHED_FILES (ID)")
                .contains("REFERENCES EDU_CLASSES (ID)")
                .contains("REFERENCES SYS_ROLES (ID)")
                .contains("CK_DOC_SHARES_SUBJECT")
                .contains("CK_DOC_SHARES_TARGET_ID")
                .contains("CK_DOC_SHARES_STUDENTS")
                .contains("VISIBLE_TO_STUDENTS")
                .contains("UQ_DOC_FOLDERS_SIBLING_NAME")
                .contains("IX_DOC_SHARES_CLASS")
                .contains("TITLE         VARCHAR2(255 CHAR)")
                .contains("NAME          VARCHAR2(200 CHAR)");
        assertThat(upper).contains("'ALL_STAFF', 'ROLE', 'CLASS'");
        assertThat(DocumentConstants.TARGET_TYPE_PATTERN).isEqualTo("ALL_STAFF|ROLE|CLASS");
    }

    @Test
    void seedsLibraryModuleAndReusesFileExplorerMenu() throws IOException {
        String text = V19FilesScriptTest.read(V19FilesScriptTest.V19_4);
        String upper = V19FilesScriptTest.code(text).toUpperCase(Locale.ROOT);
        assertThat(upper).contains("'LIBRARY'").contains("SYS_FILE_MODULE_SETTINGS");
        assertThat(FileConstants.Module.LIBRARY).isEqualTo("LIBRARY");
        assertThat(DocumentConstants.FILE_MODULE).isEqualTo("LIBRARY");

        assertThat(upper).contains("MENU_FILE_EXPLORER").contains("IS_HIDDEN = 0");
        assertThat(upper).doesNotContain("IS_HIDDEN = 1");
        for (String fn : List.of("VIEW", "DOWNLOAD", "UPLOAD", "UPDATE", "DELETE", "SHARE", "MANAGE")) {
            assertThat(upper).contains("'" + fn + "'");
        }
        // Vai tro nghiep vu chi duoc doc / upload (khong UPDATE/DELETE/SHARE/MANAGE mac dinh).
        assertThat(upper).contains("GRANT_FUNCS('ROLE_TEACHER',    'MENU_FILE_EXPLORER', 'VIEW,UPLOAD,DOWNLOAD')")
                .contains("GRANT_FUNCS('ROLE_ACCOUNTANT', 'MENU_FILE_EXPLORER', 'VIEW,UPLOAD,DOWNLOAD')")
                .contains("GRANT_FUNCS('ROLE_ADMISSION',  'MENU_FILE_EXPLORER', 'VIEW,DOWNLOAD')");

        assertThat(Permissions.DOC_LIBRARY_VIEW).isEqualTo("MENU_FILE_EXPLORER:VIEW");
        assertThat(Permissions.DOC_LIBRARY_SHARE).isEqualTo("MENU_FILE_EXPLORER:SHARE");
        assertThat(Permissions.DOC_LIBRARY_MANAGE).isEqualTo("MENU_FILE_EXPLORER:MANAGE");
    }
}
