package com.education.base.service;

import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AnnouncementRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.UserType;
import com.education.base.service.impl.AnnouncementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock AnnouncementRepository announcementRepository;
    @Mock ClassRepository classRepository;
    @Mock HtmlContentSanitizer htmlContentSanitizer;

    @InjectMocks
    AnnouncementServiceImpl service;

    @BeforeEach
    void loginAdmin() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(1L).username("admin").roles(List.of(Permissions.ADMIN_ROLE))
                .permissions(Set.of()).userType(UserType.STAFF).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        lenient().when(htmlContentSanitizer.sanitize(any())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            if (raw == null) {
                return "";
            }
            return raw.replaceAll("(?i)<script[^>]*>.*?</script>", "").trim();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_sanitizesHtmlAndStartsDraft() {
        when(announcementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AnnouncementEntity e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });

        AnnouncementDto dto = service.create(AnnouncementUpsertRequest.builder()
                .title("TB")
                .content("<p>Hi</p><script>alert(1)</script>")
                .scopeType("ALL")
                .audience("STUDENT")
                .pinned(true)
                .build());

        ArgumentCaptor<AnnouncementEntity> captor = ArgumentCaptor.forClass(AnnouncementEntity.class);
        verify(announcementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).doesNotContain("script");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getIsPinned()).isEqualTo(1);
        assertThat(dto.getId()).isEqualTo(11L);
    }

    @Test
    void create_classScope_requiresExistingClass() {
        when(classRepository.findByIdAndIsDeleted(5L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(ClassEntity.builder().id(5L).classCode("L5").build()));
        when(announcementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AnnouncementEntity e = inv.getArgument(0);
            e.setId(12L);
            return e;
        });

        AnnouncementDto dto = service.create(AnnouncementUpsertRequest.builder()
                .title("Lop")
                .content("<p>x</p>")
                .scopeType("CLASS")
                .classId(5L)
                .audience("ALL")
                .build());

        assertThat(dto.getClassId()).isEqualTo(5L);
    }

    @Test
    void create_classScope_missingClass_fails() {
        assertThatThrownBy(() -> service.create(AnnouncementUpsertRequest.builder()
                .title("Lop")
                .content("<p>x</p>")
                .scopeType("CLASS")
                .audience("STUDENT")
                .build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo(AnnouncementServiceImpl.CLASS_REQUIRED);
    }

    @Test
    void publish_setsPublishedAt() {
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(3L).title("T").content("<p>a</p>").scopeType("ALL").audience("STUDENT")
                .status("DRAFT").isPinned(0).isDeleted(0).build();
        when(announcementRepository.findByIdAndIsDeleted(3L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(entity));
        when(announcementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AnnouncementDto dto = service.publish(3L);

        assertThat(dto.getStatus()).isEqualTo("PUBLISHED");
        assertThat(dto.getPublishedAt()).isNotNull();
    }

    @Test
    void softDelete_marksDeleted() {
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(4L).title("T").content("c").scopeType("ALL").audience("STUDENT")
                .status("DRAFT").isPinned(0).isDeleted(0).build();
        when(announcementRepository.findByIdAndIsDeleted(4L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(entity));

        service.softDelete(4L);

        assertThat(entity.getIsDeleted()).isEqualTo(PersistenceFlags.DELETED);
        verify(announcementRepository).save(entity);
    }
}
