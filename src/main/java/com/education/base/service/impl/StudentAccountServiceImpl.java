package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
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
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.security.UserType;
import com.education.base.service.AuditService;
import com.education.base.service.RefreshTokenService;
import com.education.base.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tài khoản học sinh ({@code SYS_USERS.USER_TYPE = STUDENT} + liên kết {@code SELF} trong
 * {@code EDU_USER_STUDENT_LINKS} + vai trò {@code ROLE_STUDENT}).
 * <p>
 * <b>Quy tắc tên đăng nhập</b>: {@code hs} + số thứ tự từ sequence {@code SEQ_STUDENT_USERNAME} (V17_1), đệm 0 đủ
 * 5 chữ số: {@code hs00001}, {@code hs00002}... ({@code hs100000} khi vượt 99999). Ngắn, chỉ gồm chữ thường + số
 * (thỏa {@link DomainConstants#USERNAME_PATTERN}), không lộ mã học sinh 18 số / họ tên, và không bao giờ dùng lại
 * (sequence không quay vòng; nếu số đã bị một tài khoản khác chiếm thì lấy số kế tiếp).
 * <p>
 * <b>Mật khẩu tạm</b>: {@link TempPasswordGenerator}; chỉ trả về MỘT lần trong response, lưu BCrypt, KHÔNG ghi log;
 * tài khoản bị buộc đổi mật khẩu ở lần đăng nhập đầu ({@code MUST_CHANGE_PASSWORD = 1}).
 * <p>
 * <b>Nhật ký</b> ({@link AuditService}, V17_3): mỗi tài khoản tạo mới -> {@code STUDENT_ACCOUNT_PROVISIONED}; gỡ liên
 * kết cũ -> {@code STUDENT_LINK_REMOVED}; đặt lại mật khẩu / khóa / mở khóa -> {@code PASSWORD_RESET} /
 * {@code ACCOUNT_LOCKED} / {@code ACCOUNT_UNLOCKED} (chi tiết {@code accountType = STUDENT}). Sự kiện thành công chỉ
 * được ghi khi transaction commit; thao tác lỗi ghi {@code FAILURE} kèm mã lỗi. KHÔNG BAO GIỜ ghi mật khẩu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAccountServiceImpl implements StudentAccountService {

    static final String USERNAME_PREFIX = "hs";
    static final int USERNAME_MIN_DIGITS = 5;
    /** Số lần thử lấy số mới khi tên đăng nhập sinh ra đã bị chiếm. */
    static final int USERNAME_MAX_ATTEMPTS = 50;
    /** {@code SYS_USERS.FULL_NAME VARCHAR2(100)} - giới hạn theo byte UTF-8. */
    static final int USER_FULL_NAME_MAX_BYTES = 100;

    static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    static final String STUDENT_ROLE_MISSING = "STUDENT_ROLE_MISSING";
    static final String USERNAME_GENERATION_FAILED = "USERNAME_GENERATION_FAILED";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final StudentRepository studentRepository;
    private final UserStudentLinkRepository userStudentLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final Clock clock;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentAccountDto> search(StudentAccountFilterRequest filter) {
        StudentAccountFilterRequest criteria = filter == null ? new StudentAccountFilterRequest() : filter;
        int pageNo = criteria.resolvePageNo();
        int pageSize = criteria.resolvePageSize();
        Integer hasAccount = criteria.getHasAccount() == null ? null : (criteria.getHasAccount() ? 1 : 0);
        Page<Object[]> page = userStudentLinkRepository.searchStudentAccounts(
                likePattern(criteria.getKeyword()), criteria.getClassId(), hasAccount,
                PageRequest.of(pageNo - 1, pageSize));
        List<StudentAccountDto> content = new ArrayList<>(page.getNumberOfElements());
        for (Object[] row : page.getContent()) {
            content.add(toDto((StudentEntity) row[0], (UserEntity) row[1]));
        }
        return PageResponse.of(content, pageNo, pageSize, page.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StudentAccountBulkResultDto> bulkCreate(List<Long> studentIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (studentIds != null) {
            studentIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        RoleEntity studentRole = requireStudentRole();
        String actor = SecurityUtils.currentUsername();
        LocalDateTime now = LocalDateTime.now(clock);

        Map<Long, StudentEntity> students = new HashMap<>();
        for (StudentEntity student : studentRepository.findAllById(ids)) {
            if (Objects.equals(student.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                students.put(student.getId(), student);
            }
        }
        Map<Long, UserStudentLinkEntity> linkByStudent = new HashMap<>();
        for (UserStudentLinkEntity link : userStudentLinkRepository.findActiveSelfLinksByStudentIds(ids)) {
            linkByStudent.putIfAbsent(link.getStudentId(), link);
        }
        Map<Long, UserEntity> linkedUsers = new HashMap<>();
        if (!linkByStudent.isEmpty()) {
            for (UserEntity user : userRepository.findAllById(
                    linkByStudent.values().stream().map(UserStudentLinkEntity::getUserId).toList())) {
                linkedUsers.put(user.getId(), user);
            }
        }

        List<StudentAccountBulkResultDto> results = new ArrayList<>(ids.size());
        int created = 0;
        for (Long studentId : ids) {
            StudentEntity student = students.get(studentId);
            if (student == null) {
                results.add(skipped(studentId, null, StudentAccountBulkResultDto.REASON_STUDENT_NOT_FOUND, null));
                continue;
            }
            if (!DomainConstants.STUDENT_STATUS_ACTIVE.equals(student.getStatus())) {
                results.add(skipped(studentId, student, StudentAccountBulkResultDto.REASON_STUDENT_INACTIVE, null));
                continue;
            }
            UserStudentLinkEntity existing = linkByStudent.get(studentId);
            if (existing != null) {
                UserEntity linkedUser = linkedUsers.get(existing.getUserId());
                if (linkedUser != null && Objects.equals(linkedUser.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                    results.add(skipped(studentId, student, StudentAccountBulkResultDto.REASON_ALREADY_HAS_ACCOUNT,
                            linkedUser.getUsername()));
                    continue;
                }
                // Liên kết còn hiệu lực nhưng tài khoản đã bị xóa: gỡ liên kết cũ (flush ngay để unique index
                // "1 SELF đang hoạt động / học sinh" không chặn liên kết mới).
                existing.setIsDeleted(PersistenceFlags.DELETED);
                existing.setStatus(DomainConstants.LINK_STATUS_INACTIVE);
                existing.setUpdatedBy(actor);
                userStudentLinkRepository.saveAndFlush(existing);
                auditService.record(AuditEvent.builder()
                        .action(AuditActions.STUDENT_LINK_REMOVED)
                        .resource(AuditActions.RESOURCE_STUDENT, studentId)
                        .detail("linkId", existing.getId())
                        .detail("userId", existing.getUserId())
                        .detail("relation", existing.getRelation())
                        .detail("reason", "LINKED_ACCOUNT_DELETED")
                        .build());
            }
            String tempPassword = tempPasswordGenerator.generate();
            UserEntity user = createStudentUser(student, tempPassword, actor, now);
            userRoleRepository.save(UserRoleEntity.builder()
                    .userId(user.getId())
                    .roleId(studentRole.getId())
                    .assignedAt(now)
                    .assignedBy(actor)
                    .build());
            userStudentLinkRepository.save(UserStudentLinkEntity.builder()
                    .userId(user.getId())
                    .studentId(student.getId())
                    .relation(DomainConstants.LINK_RELATION_SELF)
                    .isPrimary(1)
                    .status(DomainConstants.LINK_STATUS_ACTIVE)
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .createdAt(now)
                    .createdBy(actor)
                    .build());
            created++;
            auditService.record(AuditEvent.builder()
                    .action(AuditActions.STUDENT_ACCOUNT_PROVISIONED)
                    .resource(AuditActions.RESOURCE_USER, user.getId())
                    .detail("studentId", student.getId())
                    .detail("studentCode", student.getStudentCode())
                    .detail("username", user.getUsername())
                    .detail("relation", DomainConstants.LINK_RELATION_SELF)
                    .detail("role", studentRole.getRoleCode())
                    .build());
            log.info("Đã tạo tài khoản học sinh: studentId={}, userId={}, username={}",
                    student.getId(), user.getId(), user.getUsername());
            results.add(StudentAccountBulkResultDto.builder()
                    .studentId(student.getId())
                    .studentCode(student.getStudentCode())
                    .fullName(student.getFullName())
                    .username(user.getUsername())
                    .tempPassword(tempPassword)
                    .status(StudentAccountBulkResultDto.CREATED)
                    .build());
        }
        log.info("Tạo tài khoản học sinh hàng loạt bởi {}: {} yêu cầu, {} tạo mới, {} bỏ qua",
                actor, ids.size(), created, ids.size() - created);
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentAccountCredentialDto resetPassword(Long userId) {
        UserEntity user = requireStudentUser(userId, AuditActions.PASSWORD_RESET);
        String tempPassword = tempPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(1);
        user.setPasswordChangedAt(LocalDateTime.now(clock));
        user.setUpdatedBy(SecurityUtils.currentUsername());
        userRepository.save(user);
        revokeAllAccess(user.getId());
        // Mật khẩu mới do nhân viên cấp: gỡ khóa tạm thời để học sinh đăng nhập được ngay.
        userRepository.clearLoginFailures(user.getId());
        log.info("Đã đặt lại mật khẩu tài khoản học sinh userId={}, username={}", user.getId(), user.getUsername());
        auditSuccess(AuditActions.PASSWORD_RESET, user);
        return StudentAccountCredentialDto.builder()
                .username(user.getUsername())
                .tempPassword(tempPassword)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentAccountStatusDto lock(Long userId) {
        UserEntity user = requireStudentUser(userId, AuditActions.ACCOUNT_LOCKED);
        user.setStatus(DomainConstants.USER_STATUS_LOCKED);
        user.setUpdatedBy(SecurityUtils.currentUsername());
        userRepository.save(user);
        revokeAllAccess(user.getId());
        log.info("Đã khóa tài khoản học sinh userId={}, username={}", user.getId(), user.getUsername());
        auditSuccess(AuditActions.ACCOUNT_LOCKED, user);
        return toStatusDto(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentAccountStatusDto unlock(Long userId) {
        UserEntity user = requireStudentUser(userId, AuditActions.ACCOUNT_UNLOCKED);
        user.setStatus(DomainConstants.USER_STATUS_ACTIVE);
        user.setUpdatedBy(SecurityUtils.currentUsername());
        userRepository.save(user);
        userRepository.clearLoginFailures(user.getId());
        log.info("Đã mở khóa tài khoản học sinh userId={}, username={}", user.getId(), user.getUsername());
        auditSuccess(AuditActions.ACCOUNT_UNLOCKED, user);
        return toStatusDto(user);
    }

    /** Trạng thái hiển thị của tài khoản ({@code null} = chưa có tài khoản). */
    static String accountStatus(UserEntity user) {
        if (user == null) {
            return StudentAccountDto.NO_ACCOUNT;
        }
        if (DomainConstants.USER_STATUS_LOCKED.equals(user.getStatus())) {
            return StudentAccountDto.LOCKED;
        }
        if (!DomainConstants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            return StudentAccountDto.INACTIVE;
        }
        return user.getLastLoginAt() == null ? StudentAccountDto.NEVER_LOGGED_IN : StudentAccountDto.ACTIVE;
    }

    /** {@code hs} + số đệm 0 đủ {@value #USERNAME_MIN_DIGITS} chữ số. */
    static String formatUsername(long number) {
        return USERNAME_PREFIX + String.format(Locale.ROOT, "%0" + USERNAME_MIN_DIGITS + "d", number);
    }

    /** Cắt chuỗi để vừa {@code maxBytes} byte UTF-8, không cắt đôi ký tự. */
    static String truncateUtf8(String value, int maxBytes) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) {
                break;
            }
            out.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return out.toString().strip();
    }

    private UserEntity createStudentUser(StudentEntity student, String tempPassword, String actor, LocalDateTime now) {
        UserEntity user = UserEntity.builder()
                .username(nextUsername())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .fullName(truncateUtf8(student.getFullName().strip(), USER_FULL_NAME_MAX_BYTES))
                .status(DomainConstants.USER_STATUS_ACTIVE)
                .userType(DomainConstants.USER_TYPE_STUDENT)
                .mustChangePassword(1)
                .tokenVersion(0)
                .failedLoginCount(0)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .passwordChangedAt(now)
                .createdAt(now)
                .createdBy(actor)
                .build();
        return userRepository.save(user);
    }

    private String nextUsername() {
        for (int attempt = 0; attempt < USERNAME_MAX_ATTEMPTS; attempt++) {
            Long number = userStudentLinkRepository.nextStudentUsernameNumber();
            if (number == null) {
                break;
            }
            String candidate = formatUsername(number);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new OracleBusinessException(USERNAME_GENERATION_FAILED,
                "Không sinh được tên đăng nhập cho học sinh, vui lòng thử lại.");
    }

    private RoleEntity requireStudentRole() {
        return roleRepository.findByRoleCodeAndIsDeleted(Permissions.STUDENT_ROLE, PersistenceFlags.NOT_DELETED)
                .filter(role -> DomainConstants.RECORD_STATUS_ACTIVE.equals(role.getStatus()))
                .orElseThrow(() -> new OracleBusinessException(STUDENT_ROLE_MISSING,
                        "Chưa có vai trò " + Permissions.STUDENT_ROLE + " đang hoạt động (migration V17_1)."));
    }

    /** Tài khoản HỌC SINH chưa xóa; tài khoản nhân viên coi như không tồn tại (không thao tác được ở đây). */
    private UserEntity requireStudentUser(Long userId, String auditAction) {
        return userRepository.findByIdAndIsDeleted(userId, PersistenceFlags.NOT_DELETED)
                .filter(user -> UserType.fromDb(user.getUserType()).orElse(null) == UserType.STUDENT)
                .orElseThrow(() -> {
                    auditService.record(AuditEvent.builder()
                            .action(auditAction)
                            .result(AuditResult.FAILURE)
                            .resource(AuditActions.RESOURCE_USER, userId)
                            .detail("accountType", UserType.STUDENT.name())
                            .detail("errorCode", USER_NOT_FOUND)
                            .build());
                    return new OracleBusinessException(USER_NOT_FOUND,
                            "Không tìm thấy tài khoản học sinh với ID: " + userId);
                });
    }

    /** Sự kiện thành công (ghi sau commit) cho thao tác trên tài khoản học sinh - không kèm mật khẩu. */
    private void auditSuccess(String action, UserEntity user) {
        auditService.record(AuditEvent.builder()
                .action(action)
                .resource(AuditActions.RESOURCE_USER, user.getId())
                .detail("accountType", UserType.STUDENT.name())
                .detail("username", user.getUsername())
                .build());
    }

    /** Vô hiệu hóa mọi access token ({@code TOKEN_VERSION}) và phiên refresh token của tài khoản. */
    private void revokeAllAccess(Long userId) {
        userRepository.incrementTokenVersion(userId);
        refreshTokenService.revokeAllSessions(userId);
    }

    private static StudentAccountBulkResultDto skipped(Long studentId, StudentEntity student, String reason,
                                                       String username) {
        return StudentAccountBulkResultDto.builder()
                .studentId(studentId)
                .studentCode(student == null ? null : student.getStudentCode())
                .fullName(student == null ? null : student.getFullName())
                .username(username)
                .status(StudentAccountBulkResultDto.SKIPPED)
                .reason(reason)
                .build();
    }

    private static StudentAccountDto toDto(StudentEntity student, UserEntity user) {
        return StudentAccountDto.builder()
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .studentStatus(student.getStatus())
                .userId(user == null ? null : user.getId())
                .username(user == null ? null : user.getUsername())
                .accountStatus(accountStatus(user))
                .mustChangePassword(user != null && Integer.valueOf(1).equals(user.getMustChangePassword()))
                .lastLoginAt(user == null ? null : user.getLastLoginAt())
                .build();
    }

    private static StudentAccountStatusDto toStatusDto(UserEntity user) {
        return StudentAccountStatusDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .accountStatus(accountStatus(user))
                .build();
    }

    /** Mẫu LIKE chữ thường {@code %...%}, escape {@code \ % _} bằng {@code \}; rỗng -> {@code null}. */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
