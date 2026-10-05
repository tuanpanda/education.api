package com.education.base.service;

import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AnnouncementRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.UserType;
import com.education.base.service.impl.AnnouncementServiceImpl;
import com.education.base.service.impl.TeachingAssignmentGuard;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final long TEACHER_ID = 7L;

    @Mock AnnouncementRepository announcementRepository;
    @Mock ClassRepository classRepository;
    @Mock HtmlContentSanitizer htmlContentSanitizer;
    @Mock TeachingAssignmentGuard teachingAssignmentGuard;

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
        lenient().when(teachingAssignmentGuard.isAssignmentRestricted()).thenReturn(false);
        lenient().when(teachingAssignmentGuard.taughtClassIdsIfRestricted()).thenReturn(Optional.empty());
        lenient().when(teachingAssignmentGuard.taughtClassesIfRestricted()).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TestSecurityContexts.clear();
    }

    private void loginTeacher() {
        TestSecurityContexts.login(TEACHER_ID, List.of(Permissions.TEACHER_ROLE),
                Set.of(Permissions.ANNOUNCEMENT_VIEW, Permissions.ANNOUNCEMENT_CREATE,
                        Permissions.ANNOUNCEMENT_UPDATE, Permissions.ANNOUNCEMENT_PUBLISH,
                        Permissions.ANNOUNCEMENT_DELETE));
        lenient().when(teachingAssignmentGuard.isAssignmentRestricted()).thenReturn(true);
        lenient().when(teachingAssignmentGuard.taughtClassIdsIfRestricted()).thenReturn(Optional.of(Set.of(5L)));
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
        assertThat(dto.isCanManage()).isTrue();
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

    @Test
    @SuppressWarnings("unchecked")
    void search_pageNoIsOneBasedLikeOtherListScreens() {
        when(announcementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable pageable = inv.getArgument(1);
                    return new PageImpl<AnnouncementEntity>(List.of(), pageable, 45);
                });

        PageResponse<AnnouncementDto> page = service.search(AnnouncementFilterRequest.builder()
                .pageNo(2).pageSize(20).build());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(announcementRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotalRows()).isEqualTo(45L);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(new AnnouncementFilterRequest().getPageNo()).isEqualTo(1);
    }

    @Test
    void teacher_createAllScope_forbidden() {
        loginTeacher();

        assertThatThrownBy(() -> service.create(AnnouncementUpsertRequest.builder()
                .title("TB")
                .content("<p>x</p>")
                .scopeType("ALL")
                .audience("STUDENT")
                .build()))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getErrorCode())
                .isEqualTo(AnnouncementServiceImpl.SCOPE_FORBIDDEN);
        verify(announcementRepository, never()).saveAndFlush(any());
    }

    @Test
    void teacher_createOwnClass_allowed() {
        loginTeacher();
        ClassEntity clazz = ClassEntity.builder().id(5L).classCode("L5").teacherId(TEACHER_ID).build();
        when(classRepository.findByIdAndIsDeleted(5L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(clazz));
        doNothing().when(teachingAssignmentGuard).requireCanWrite(eq(clazz), any());
        when(announcementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AnnouncementEntity e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });

        AnnouncementDto dto = service.create(AnnouncementUpsertRequest.builder()
                .title("Lop")
                .content("<p>x</p>")
                .scopeType("CLASS")
                .classId(5L)
                .audience("STUDENT")
                .build());

        assertThat(dto.getId()).isEqualTo(20L);
        assertThat(dto.getScopeType()).isEqualTo("CLASS");
        assertThat(dto.isCanManage()).isTrue();
    }

    @Test
    void teacher_createOtherClass_forbidden() {
        loginTeacher();
        ClassEntity clazz = ClassEntity.builder().id(9L).classCode("L9").teacherId(99L).build();
        when(classRepository.findByIdAndIsDeleted(9L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(clazz));
        doThrow(new ForbiddenException("NOT_CLASS_TEACHER", "not yours"))
                .when(teachingAssignmentGuard).requireCanWrite(eq(clazz), any());

        assertThatThrownBy(() -> service.create(AnnouncementUpsertRequest.builder()
                .title("Lop")
                .content("<p>x</p>")
                .scopeType("CLASS")
                .classId(9L)
                .audience("STUDENT")
                .build()))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getErrorCode())
                .isEqualTo("NOT_CLASS_TEACHER");
    }

    @Test
    void teacher_publishAllScope_forbidden() {
        loginTeacher();
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(3L).title("T").content("<p>a</p>").scopeType("ALL").audience("STUDENT")
                .status("DRAFT").isPinned(0).isDeleted(0).build();
        when(announcementRepository.findByIdAndIsDeleted(3L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.publish(3L))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getErrorCode())
                .isEqualTo(AnnouncementServiceImpl.SCOPE_FORBIDDEN);
    }

    @Test
    void teacher_updateOwnClass_cannotConvertToAll() {
        loginTeacher();
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(8L).title("T").content("<p>a</p>").scopeType("CLASS").classId(5L)
                .audience("STUDENT").status("DRAFT").isPinned(0).isDeleted(0).build();
        ClassEntity clazz = ClassEntity.builder().id(5L).classCode("L5").teacherId(TEACHER_ID).build();
        when(announcementRepository.findByIdAndIsDeleted(8L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(entity));
        when(classRepository.findByIdAndIsDeleted(5L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(clazz));
        doNothing().when(teachingAssignmentGuard).requireCanWrite(eq(clazz), any());

        assertThatThrownBy(() -> service.update(8L, AnnouncementUpsertRequest.builder()
                .title("T2")
                .content("<p>b</p>")
                .scopeType("ALL")
                .audience("STUDENT")
                .build()))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getErrorCode())
                .isEqualTo(AnnouncementServiceImpl.SCOPE_FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacher_search_foreignClassId_returnsEmpty() {
        loginTeacher();

        PageResponse<AnnouncementDto> page = service.search(AnnouncementFilterRequest.builder()
                .classId(99L).pageNo(1).pageSize(20).build());

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalRows()).isZero();
        verify(announcementRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void teacher_getById_allScope_readableWithCanManageFalse() {
        loginTeacher();
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(2L).title("All").content("<p>a</p>").scopeType("ALL").audience("STUDENT")
                .status("PUBLISHED").isPinned(0).isDeleted(0).build();
        when(announcementRepository.findByIdAndIsDeleted(2L, PersistenceFlags.NOT_DELETED))
                .thenReturn(Optional.of(entity));

        AnnouncementDto dto = service.getById(2L);

        assertThat(dto.getScopeType()).isEqualTo("ALL");
        assertThat(dto.isCanManage()).isFalse();
    }

    @Test
    void teacher_listManageableClasses_returnsTaught() {
        loginTeacher();
        when(teachingAssignmentGuard.taughtClassesIfRestricted()).thenReturn(Optional.of(List.of(
                ClassEntity.builder().id(5L).classCode("L5").className("Lop 5").build())));

        assertThat(service.listManageableClasses())
                .hasSize(1)
                .first()
                .extracting("id", "classCode", "className")
                .containsExactly(5L, "L5", "Lop 5");
    }
}