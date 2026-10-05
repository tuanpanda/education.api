package com.education.base.service;

import com.education.base.dto.response.PortalAnnouncementDto;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.AnnouncementReadEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AnnouncementReadRepository;
import com.education.base.repository.AnnouncementRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.PortalStudentContext;
import com.education.base.security.Permissions;
import com.education.base.security.UserType;
import com.education.base.service.impl.AnnouncementQueryServiceImpl;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementQueryServiceTest {

    @Mock AnnouncementRepository announcementRepository;
    @Mock AnnouncementReadRepository announcementReadRepository;
    @Mock ClassStudentRepository classStudentRepository;
    @Mock UserStudentLinkRepository userStudentLinkRepository;
    @Mock PortalStudentContext portalStudentContext;

    @InjectMocks
    AnnouncementQueryServiceImpl service;

    @BeforeEach
    void loginStudent() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(99L).username("hs00001").roles(List.of(Permissions.STUDENT_ROLE))
                .permissions(Set.of()).userType(UserType.STUDENT).studentId(42L).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_onlyOwnClassAndAll() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(10L).studentId(42L).build()));
        AnnouncementEntity allScope = AnnouncementEntity.builder()
                .id(1L).title("ALL").scopeType("ALL").status("PUBLISHED").isPinned(0).build();
        AnnouncementEntity ownClass = AnnouncementEntity.builder()
                .id(2L).title("CLASS10").scopeType("CLASS").classId(10L).status("PUBLISHED").isPinned(1).build();
        when(announcementRepository.findVisibleForStudent(eq(Set.of(10L)), any(LocalDateTime.class)))
                .thenReturn(List.of(ownClass, allScope));
        when(announcementReadRepository.findReadAnnouncementIds(eq(99L), anyCollection()))
                .thenReturn(Set.of(1L));

        List<PortalAnnouncementDto> result = service.listForCurrentStudent();

        assertThat(result).extracting(PortalAnnouncementDto::getId).containsExactly(2L, 1L);
        assertThat(result.get(0).isPinned()).isTrue();
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(1).isRead()).isTrue();
    }

    @Test
    void markRead_idor_otherClassAnnouncement_rejected() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(10L).studentId(42L).build()));
        when(announcementRepository.findVisibleForStudent(eq(Set.of(10L)), any(LocalDateTime.class)))
                .thenReturn(List.of()); // announcement 77 not visible

        assertThatThrownBy(() -> service.markRead(77L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo(AnnouncementQueryServiceImpl.NOT_VISIBLE);
        verify(announcementReadRepository, never()).save(any());
    }

    @Test
    void markRead_visible_savesOnce() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(10L).studentId(42L).build()));
        AnnouncementEntity visible = AnnouncementEntity.builder()
                .id(5L).title("OK").scopeType("ALL").status("PUBLISHED").isPinned(0).build();
        when(announcementRepository.findVisibleForStudent(eq(Set.of(10L)), any(LocalDateTime.class)))
                .thenReturn(List.of(visible));
        when(announcementReadRepository.existsByAnnouncementIdAndUserId(5L, 99L)).thenReturn(false);

        service.markRead(5L);

        ArgumentCaptor<AnnouncementReadEntity> captor = ArgumentCaptor.forClass(AnnouncementReadEntity.class);
        verify(announcementReadRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnouncementId()).isEqualTo(5L);
        assertThat(captor.getValue().getUserId()).isEqualTo(99L);
    }

    @Test
    void countUnread_usesStudentLink() {
        when(userStudentLinkRepository.findActiveSelfLinkByUserId(99L)).thenReturn(Optional.of(
                UserStudentLinkEntity.builder().userId(99L).studentId(42L).relation("SELF").build()));
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(10L).studentId(42L).build()));
        when(announcementRepository.countUnreadForStudent(eq(99L), eq(Set.of(10L)), any(LocalDateTime.class)))
                .thenReturn(3L);

        assertThat(service.countUnread(99L)).isEqualTo(3L);
    }

    @Test
    void countUnread_noLink_returnsZero() {
        when(userStudentLinkRepository.findActiveSelfLinkByUserId(99L)).thenReturn(Optional.empty());
        assertThat(service.countUnread(99L)).isZero();
    }
}
