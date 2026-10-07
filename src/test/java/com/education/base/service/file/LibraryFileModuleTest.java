package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.FileEntity;
import com.education.base.repository.DocItemRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.service.DocTestData;
import com.education.base.service.DocumentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryFileModuleTest {

    @Mock
    private DocItemRepository itemRepository;
    @Mock
    private DocumentAccessService accessService;
    @InjectMocks
    private LibraryFileModule module;

    @Test
    void descriptor_basics() {
        assertThat(module.code()).isEqualTo("LIBRARY");
        assertThat(module.allowGenericUpload()).isFalse();
        assertThat(module.canUpload(DocTestData.admin(), 1L)).isFalse();
        assertThat(module.canDelete(DocTestData.admin(), new FileEntity())).isFalse();
    }

    @Test
    void canRead_onlyWhenItemVisible() {
        AuthUserPrincipal user = DocTestData.staff(7L, "ROLE_TEACHER");
        FileEntity file = FileEntity.builder().id(140L).referenceId(40L).moduleName("LIBRARY").build();
        DocItemEntity item = DocTestData.item(40, null, 7);
        when(itemRepository.findFirstByFileIdAndIsDeleted(140L, 0)).thenReturn(Optional.of(item));
        when(accessService.forUser(user)).thenReturn(DocTestData.ctx(user, false, true, false, false, false,
                Set.of(), Map.of(), Set.of(), Set.of()));
        assertThat(module.canRead(user, file)).isTrue();

        FileEntity old = FileEntity.builder().id(139L).referenceId(40L).build();
        when(itemRepository.findFirstByFileIdAndIsDeleted(139L, 0)).thenReturn(Optional.empty());
        assertThat(module.canRead(user, old)).isFalse();
        assertThat(module.canRead(null, file)).isFalse();
    }

    @Test
    void describeAndAssertReference() {
        DocItemEntity item = DocTestData.item(40, null, 7);
        when(itemRepository.findById(40L)).thenReturn(Optional.of(item));
        var info = module.describeReference(40L);
        assertThat(info.label()).isEqualTo("T40");
        assertThat(info.uiRoute()).isEqualTo("/documents/files?item=40");
        assertThat(info.state()).isEqualTo(FileConstants.RefState.ACTIVE);

        when(itemRepository.findById(41L)).thenReturn(Optional.empty());
        assertThat(module.describeReference(41L).state()).isEqualTo(FileConstants.RefState.NOT_FOUND);
        module.assertReferenceUsable(41L); // ID cấp trước, chưa có dòng -> chấp nhận

        item.setIsDeleted(1);
        assertThatThrownBy(() -> module.assertReferenceUsable(40L))
                .hasFieldOrPropertyWithValue("errorCode", FileErrorCodes.FILE_REFERENCE_INACTIVE);
        assertThatThrownBy(() -> module.assertReferenceUsable(null))
                .hasFieldOrPropertyWithValue("errorCode", FileErrorCodes.FILE_REFERENCE_REQUIRED);
    }
}