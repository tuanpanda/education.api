package com.education.base.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.common.TempPasswordGenerator;
import com.education.base.dto.request.StudentAccountFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentAccountBulkResultDto;
import com.education.base.dto.response.StudentAccountCredentialDto;
import com.education.base.dto.response.StudentAccountDto;
import com.education.base.dto.response.StudentAccountStatusDto;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.service.AuditService;
import com.education.base.service.RefreshTokenService;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccountServiceImplTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T08:00:00Z"), ZoneId.of("Asia/Bangkok"));

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserStudentLinkRepository linkRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TempPasswordGenerator tempPasswordGenerator;
    @Mock
    private AuditService auditService;

    private StudentAccountServiceImpl service;
    private final AtomicLong userIds = new AtomicLong(100);
    private final AtomicLong usernameNumbers = new AtomicLong(0);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        service = new StudentAccountServiceImpl(userRepository, roleRepository, userRoleRepository, studentRepository,
                linkRepository, ENCODER, refreshTokenService, tempPasswordGenerator, CLOCK, auditService);
        TestSecurityContexts.loginAdmin(1L);
        lenient().when(roleRepository.findByRoleCodeAndIsDeleted("ROLE_STUDENT", 0))
                .thenReturn(Optional.of(RoleEntity.builder().id(7L).roleCode("ROLE_STUDENT").status("ACTIVE")
                        .isDeleted(0).build()));
        lenient().when(linkRepository.nextStudentUsernameNumber()).thenAnswer(inv -> usernameNumbers.incrementAndGet());
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity user = inv.getArgument(0);
            if (user.getId() == null) {
                user.setId(userIds.incrementAndGet());
            }
            return user;
        });
        lenient().when(tempPasswordGenerator.generate()).thenReturn("k7m2p9x4qa", "r3t8w2n6hb", "z9y8x7w6vc");

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(StudentAccountServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(StudentAccountServiceImpl.class)).detachAppender(logs);
        TestSecurityContexts.clear();
    }

    private static StudentEntity student(long id, String status) {
        return StudentEntity.builder().id(id).studentCode("HS" + id).fullName("Nguyễn Văn " + id)
                .status(status).isDeleted(0).build();
    }

    private static UserEntity studentUser(long id, String status) {
        return UserEntity.builder().id(id).username("hs000" + id).fullName("HS").status(status)
                .userType("STUDENT").isDeleted(0).tokenVersion(0).mustChangePassword(0).build();
    }

    // ------------------------------------------------------------------ bulk create

    @Test
    void bulkCreate_createsUserRoleAndSelfLink_andReturnsTempPasswordOnce() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE"), student(2L, "ACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        List<StudentAccountBulkResultDto> results = service.bulkCreate(List.of(1L, 2L));

        assertThat(results).extracting(StudentAccountBulkResultDto::getStatus).containsExactly("CREATED", "CREATED");
        assertThat(results).extracting(StudentAccountBulkResultDto::getUsername).containsExactly("hs00001", "hs00002");
        assertThat(results).extracting(StudentAccountBulkResultDto::getTempPassword)
                .containsExactly("k7m2p9x4qa", "r3t8w2n6hb");
        assertThat(results.get(0).getStudentCode()).isEqualTo("HS1");
        assertThat(results.get(0).getFullName()).isEqualTo("Nguyễn Văn 1");
        assertThat(results.get(0).getReason()).isNull();
        assertThat(results.get(0).toString()).doesNotContain("k7m2p9x4qa");

        ArgumentCaptor<UserEntity> users = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(users.capture());
        UserEntity first = users.getAllValues().get(0);
        assertThat(first.getUserType()).isEqualTo("STUDENT");
        assertThat(first.getMustChangePassword()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo("ACTIVE");
        assertThat(first.getEmail()).isNull();
        assertThat(first.getPhone()).isNull();
        assertThat(first.getFullName()).isEqualTo("Nguyễn Văn 1");
        assertThat(first.getCreatedBy()).isEqualTo("user1");
        assertThat(ENCODER.matches("k7m2p9x4qa", first.getPasswordHash())).isTrue();
        assertThat(first.getPasswordHash()).doesNotContain("k7m2p9x4qa");

        ArgumentCaptor<UserRoleEntity> roles = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleRepository, org.mockito.Mockito.times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(UserRoleEntity::getRoleId).containsOnly(7L);
        assertThat(roles.getAllValues()).extracting(UserRoleEntity::getUserId).containsExactly(101L, 102L);

        ArgumentCaptor<UserStudentLinkEntity> links = ArgumentCaptor.forClass(UserStudentLinkEntity.class);
        verify(linkRepository, org.mockito.Mockito.times(2)).save(links.capture());
        UserStudentLinkEntity link = links.getAllValues().get(0);
        assertThat(link.getUserId()).isEqualTo(101L);
        assertThat(link.getStudentId()).isEqualTo(1L);
        assertThat(link.getRelation()).isEqualTo("SELF");
        assertThat(link.getIsPrimary()).isEqualTo(1);
        assertThat(link.getStatus()).isEqualTo("ACTIVE");
        assertThat(link.getIsDeleted()).isZero();
    }

    @Test
    void bulkCreate_skipsMissingInactiveAndExistingAccounts_preservingRequestOrder() {
        StudentEntity deleted = student(4L, "ACTIVE");
        deleted.setIsDeleted(1);
        when(studentRepository.findAllById(any())).thenReturn(List.of(
                student(1L, "ACTIVE"), student(2L, "INACTIVE"), student(3L, "ACTIVE"), deleted));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of(
                UserStudentLinkEntity.builder().id(50L).userId(60L).studentId(3L).relation("SELF")
                        .status("ACTIVE").isDeleted(0).build()));
        when(userRepository.findAllById(List.of(60L))).thenReturn(List.of(studentUser(60L, "ACTIVE")));
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        List<StudentAccountBulkResultDto> results = service.bulkCreate(Arrays.asList(9L, 2L, 3L, 1L, 1L, null, 4L));

        assertThat(results).extracting(StudentAccountBulkResultDto::getStudentId).containsExactly(9L, 2L, 3L, 1L, 4L);
        assertThat(results).extracting(StudentAccountBulkResultDto::getStatus)
                .containsExactly("SKIPPED", "SKIPPED", "SKIPPED", "CREATED", "SKIPPED");
        assertThat(results).extracting(StudentAccountBulkResultDto::getReason).containsExactly(
                "STUDENT_NOT_FOUND", "STUDENT_INACTIVE", "ALREADY_HAS_ACCOUNT", null, "STUDENT_NOT_FOUND");
        assertThat(results.get(2).getUsername()).isEqualTo("hs00060");
        assertThat(results).filteredOn(r -> "SKIPPED".equals(r.getStatus()))
                .allSatisfy(r -> assertThat(r.getTempPassword()).isNull());
        verify(linkRepository, never()).saveAndFlush(any());
    }

    @Test
    void bulkCreate_staleLinkOfDeletedUser_isRetiredBeforeNewLink() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE")));
        UserStudentLinkEntity stale = UserStudentLinkEntity.builder().id(50L).userId(60L).studentId(1L)
                .relation("SELF").status("ACTIVE").isDeleted(0).build();
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of(stale));
        UserEntity deletedUser = studentUser(60L, "INACTIVE");
        deletedUser.setIsDeleted(1);
        when(userRepository.findAllById(List.of(60L))).thenReturn(List.of(deletedUser));
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        List<StudentAccountBulkResultDto> results = service.bulkCreate(List.of(1L));

        assertThat(results).extracting(StudentAccountBulkResultDto::getStatus).containsExactly("CREATED");
        assertThat(stale.getIsDeleted()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo("INACTIVE");
        var order = inOrder(linkRepository);
        order.verify(linkRepository).saveAndFlush(stale);
        order.verify(linkRepository).save(any(UserStudentLinkEntity.class));
    }

    @Test
    void bulkCreate_takenUsernameNumber_isSkipped() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername("hs00001")).thenReturn(true);
        when(userRepository.existsByUsername("hs00002")).thenReturn(false);

        assertThat(service.bulkCreate(List.of(1L)).get(0).getUsername()).isEqualTo("hs00002");
    }

    @Test
    void bulkCreate_usernameNeverFree_fails() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.bulkCreate(List.of(1L)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("USERNAME_GENERATION_FAILED");
    }

    @Test
    void bulkCreate_missingStudentRole_fails() {
        when(roleRepository.findByRoleCodeAndIsDeleted("ROLE_STUDENT", 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bulkCreate(List.of(1L)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("STUDENT_ROLE_MISSING");
        verify(userRepository, never()).save(any());
    }

    @Test
    void bulkCreate_emptyRequest_doesNothing() {
        assertThat(service.bulkCreate(List.of())).isEmpty();
        assertThat(service.bulkCreate(null)).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    void bulkCreate_longVietnameseName_truncatedTo100Utf8Bytes() {
        StudentEntity longName = student(1L, "ACTIVE");
        longName.setFullName("Nguyễn ".repeat(30).strip());
        when(studentRepository.findAllById(any())).thenReturn(List.of(longName));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        service.bulkCreate(List.of(1L));

        ArgumentCaptor<UserEntity> users = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(users.capture());
        assertThat(users.getValue().getFullName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(100);
        assertThat(users.getValue().getFullName()).startsWith("Nguyễn Nguyễn");
    }

    @Test
    void tempPasswordsAreNeverLogged() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.findByIdAndIsDeleted(101L, 0)).thenAnswer(inv -> Optional.of(studentUser(101L, "ACTIVE")));

        service.bulkCreate(List.of(1L));
        service.resetPassword(101L);

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("k7m2p9x4qa").doesNotContain("r3t8w2n6hb");
            assertThat(Arrays.toString(event.getArgumentArray())).doesNotContain("k7m2p9x4qa")
                    .doesNotContain("r3t8w2n6hb");
        });
    }

    // ------------------------------------------------------------------ reset / lock / unlock

    @Test
    void resetPassword_setsNewHashForcesChangeAndRevokesSessions() {
        UserEntity user = studentUser(5L, "ACTIVE");
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(user));

        StudentAccountCredentialDto credential = service.resetPassword(5L);

        assertThat(credential.getUsername()).isEqualTo("hs0005");
        assertThat(credential.getTempPassword()).isEqualTo("k7m2p9x4qa");
        assertThat(credential.toString()).doesNotContain("k7m2p9x4qa");
        assertThat(ENCODER.matches("k7m2p9x4qa", user.getPasswordHash())).isTrue();
        assertThat(user.getMustChangePassword()).isEqualTo(1);
        assertThat(user.getPasswordChangedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(userRepository).incrementTokenVersion(5L);
        verify(refreshTokenService).revokeAllSessions(5L);
        verify(userRepository).clearLoginFailures(5L);
    }

    @Test
    void lock_setsLockedAndRevokesSessions() {
        UserEntity user = studentUser(5L, "ACTIVE");
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(user));

        StudentAccountStatusDto status = service.lock(5L);

        assertThat(user.getStatus()).isEqualTo("LOCKED");
        assertThat(status.getAccountStatus()).isEqualTo("LOCKED");
        verify(userRepository).incrementTokenVersion(5L);
        verify(refreshTokenService).revokeAllSessions(5L);
    }

    @Test
    void unlock_setsActiveAndClearsLoginFailures() {
        UserEntity user = studentUser(5L, "LOCKED");
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(user));

        StudentAccountStatusDto status = service.unlock(5L);

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(status.getAccountStatus()).isEqualTo("NEVER_LOGGED_IN");
        verify(userRepository).clearLoginFailures(5L);
    }

    @Test
    void staffUserCannotBeManagedHere() {
        UserEntity staff = UserEntity.builder().id(2L).username("teacher1").status("ACTIVE").userType("STAFF")
                .isDeleted(0).build();
        when(userRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(staff));

        for (Runnable action : List.<Runnable>of(() -> service.resetPassword(2L), () -> service.lock(2L),
                () -> service.unlock(2L))) {
            assertThatThrownBy(action::run).isInstanceOf(OracleBusinessException.class)
                    .extracting("errorCode").isEqualTo("USER_NOT_FOUND");
        }
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllSessions(any());
    }

    @Test
    void unknownUser_isNotFound() {
        when(userRepository.findByIdAndIsDeleted(404L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lock(404L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("USER_NOT_FOUND");
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_mapsRowsAndPassesEscapedFilters() {
        UserEntity user = studentUser(5L, "ACTIVE");
        user.setLastLoginAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{student(1L, "ACTIVE"), user});
        rows.add(new Object[]{student(2L, "ACTIVE"), null});
        when(linkRepository.searchStudentAccounts(eq("%an\\_%"), eq(3L), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(1, 2), 7));
        StudentAccountFilterRequest filter = new StudentAccountFilterRequest();
        filter.setKeyword("  AN_ ");
        filter.setClassId(3L);
        filter.setHasAccount(false);
        filter.setPageNo(2);
        filter.setPageSize(2);

        PageResponse<StudentAccountDto> page = service.search(filter);

        assertThat(page.getTotalRows()).isEqualTo(7);
        assertThat(page.getContent()).hasSize(2);
        StudentAccountDto withAccount = page.getContent().get(0);
        assertThat(withAccount.getUsername()).isEqualTo("hs0005");
        assertThat(withAccount.getUserId()).isEqualTo(5L);
        assertThat(withAccount.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(withAccount.getLastLoginAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
        StudentAccountDto withoutAccount = page.getContent().get(1);
        assertThat(withoutAccount.getUsername()).isNull();
        assertThat(withoutAccount.getAccountStatus()).isEqualTo("NO_ACCOUNT");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(linkRepository).searchStudentAccounts(eq("%an\\_%"), eq(3L), eq(0), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void search_nullFilter_usesDefaults() {
        when(linkRepository.searchStudentAccounts(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.search(null).getContent()).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    @Test
    void usernameScheme() {
        assertThat(StudentAccountServiceImpl.formatUsername(1)).isEqualTo("hs00001");
        assertThat(StudentAccountServiceImpl.formatUsername(99999)).isEqualTo("hs99999");
        assertThat(StudentAccountServiceImpl.formatUsername(100000)).isEqualTo("hs100000");
        assertThat(StudentAccountServiceImpl.formatUsername(42))
                .matches(com.education.base.common.DomainConstants.USERNAME_PATTERN);
    }

    @Test
    void accountStatusValues() {
        assertThat(StudentAccountServiceImpl.accountStatus(null)).isEqualTo("NO_ACCOUNT");
        assertThat(StudentAccountServiceImpl.accountStatus(studentUser(1L, "LOCKED"))).isEqualTo("LOCKED");
        assertThat(StudentAccountServiceImpl.accountStatus(studentUser(1L, "INACTIVE"))).isEqualTo("INACTIVE");
        assertThat(StudentAccountServiceImpl.accountStatus(studentUser(1L, "ACTIVE"))).isEqualTo("NEVER_LOGGED_IN");
        UserEntity loggedIn = studentUser(1L, "ACTIVE");
        loggedIn.setLastLoginAt(LocalDateTime.now(CLOCK));
        assertThat(StudentAccountServiceImpl.accountStatus(loggedIn)).isEqualTo("ACTIVE");
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(StudentAccountServiceImpl.likePattern(null)).isNull();
        assertThat(StudentAccountServiceImpl.likePattern("  ")).isNull();
        assertThat(StudentAccountServiceImpl.likePattern("A%b_c\\")).isEqualTo("%a\\%b\\_c\\\\%");
    }

    @Test
    void truncateUtf8NeverSplitsCharacters() {
        assertThat(StudentAccountServiceImpl.truncateUtf8("abc", 100)).isEqualTo("abc");
        assertThat(StudentAccountServiceImpl.truncateUtf8(null, 10)).isNull();
        assertThat(StudentAccountServiceImpl.truncateUtf8("ệệệ", 7)).isEqualTo("ệệ");
    }

    // ------------------------------------------------------------------ audit (V17_3)

    private List<AuditEvent> auditEvents() {
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(events.capture());
        return events.getAllValues();
    }

    private static void assertNoSecret(List<AuditEvent> events, String... secrets) {
        for (AuditEvent event : events) {
            for (String secret : secrets) {
                assertThat(event.toString()).doesNotContain(secret);
                assertThat(event.getDetails().keySet()).noneMatch(key -> key.toLowerCase().contains("password"));
            }
        }
    }

    @Test
    void audit_bulkCreate_recordsOneProvisionedEventPerCreatedAccount_withoutPasswords() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE"), student(2L, "INACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        service.bulkCreate(List.of(1L, 2L));

        List<AuditEvent> events = auditEvents();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getAction()).isEqualTo(AuditActions.STUDENT_ACCOUNT_PROVISIONED);
            assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
            assertThat(event.getResourceType()).isEqualTo(AuditActions.RESOURCE_USER);
            assertThat(event.getResourceId()).isEqualTo("101");
            assertThat(event.getDetails()).containsEntry("studentId", 1L).containsEntry("username", "hs00001")
                    .containsEntry("relation", "SELF").containsEntry("role", "ROLE_STUDENT");
        });
        assertNoSecret(events, "k7m2p9x4qa");
    }

    @Test
    void audit_staleLinkRemoval_isRecorded() {
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(1L, "ACTIVE")));
        when(linkRepository.findActiveSelfLinksByStudentIds(anyCollection())).thenReturn(List.of(
                UserStudentLinkEntity.builder().id(50L).userId(60L).studentId(1L).relation("SELF")
                        .status("ACTIVE").isDeleted(0).build()));
        UserEntity deletedUser = studentUser(60L, "INACTIVE");
        deletedUser.setIsDeleted(1);
        when(userRepository.findAllById(List.of(60L))).thenReturn(List.of(deletedUser));
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        service.bulkCreate(List.of(1L));

        assertThat(auditEvents()).extracting(AuditEvent::getAction)
                .containsExactly(AuditActions.STUDENT_LINK_REMOVED, AuditActions.STUDENT_ACCOUNT_PROVISIONED);
    }

    @Test
    void audit_resetLockUnlock_recordSuccessWithoutPasswords() {
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenAnswer(inv -> Optional.of(studentUser(5L, "ACTIVE")));

        service.resetPassword(5L);
        service.lock(5L);
        service.unlock(5L);

        List<AuditEvent> events = auditEvents();
        assertThat(events).extracting(AuditEvent::getAction).containsExactly(
                AuditActions.PASSWORD_RESET, AuditActions.ACCOUNT_LOCKED, AuditActions.ACCOUNT_UNLOCKED);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
            assertThat(event.getResourceId()).isEqualTo("5");
            assertThat(event.getDetails()).containsEntry("accountType", "STUDENT");
        });
        assertNoSecret(events, "k7m2p9x4qa");
    }

    @Test
    void audit_operationOnNonStudentAccount_recordsFailure() {
        when(userRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(UserEntity.builder().id(2L)
                .username("teacher1").status("ACTIVE").userType("STAFF").isDeleted(0).build()));

        assertThatThrownBy(() -> service.resetPassword(2L)).isInstanceOf(OracleBusinessException.class);

        assertThat(auditEvents()).singleElement().satisfies(event -> {
            assertThat(event.getAction()).isEqualTo(AuditActions.PASSWORD_RESET);
            assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
            assertThat(event.getDetails()).containsEntry("errorCode", "USER_NOT_FOUND");
        });
    }
}
