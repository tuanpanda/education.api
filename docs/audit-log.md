# Nhật ký hệ thống (SYS_AUDIT_LOGS)

Giai đoạn 0 – stream B. Bảng tạo bởi `V17_3__audit_log.sql`; tra cứu: `GET /api/v1/audit-logs/search`
(quyền `MENU_AUDIT_LOG:VIEW`, `ROLE_ADMIN` luôn được).

## Ghi tự động (không sửa mã nghiệp vụ)

`AuditTrailAspect` bọc các service sau (ngoài transaction, nên `SUCCESS` chỉ ghi khi nghiệp vụ đã commit; lỗi ghi
`FAILURE` rồi ném lại nguyên vẹn):

| Service#method | ACTION | RESOURCE_TYPE |
|---|---|---|
| `AuthService#login` | `LOGIN_SUCCESS` / `LOGIN_FAILED` | `USER` |
| `AuthService#logout` | `LOGOUT` | `USER` |
| `AuthService#refresh` (chỉ khi phát hiện dùng lại refresh token) | `TOKEN_REUSE_DETECTED` | `USER` |
| `AuthService#changePassword` | `PASSWORD_CHANGED` | `USER` |
| `UserAdminService#create` / `update` / `delete` | `ACCOUNT_CREATED` / `ACCOUNT_UPDATED` / `ACCOUNT_DELETED` | `USER` |
| `UserAdminService#changeStatus` | `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED` | `USER` |
| `UserAdminService#resetPassword` | `PASSWORD_RESET` | `USER` |
| `UserAdminService#assignRoles` | `USER_ROLES_CHANGED` | `USER` |
| `RoleAdminService#create` / `update` / `delete` / `updatePermissions` | `ROLE_CREATED` / `ROLE_UPDATED` / `ROLE_DELETED` / `ROLE_PERMISSIONS_CHANGED` | `ROLE` |
| `PaymentService#confirmPayment` | `PAYMENT_CONFIRMED` | `PAYMENT_TRANSACTION` |
| `PaymentService#voidTransaction` / `refundTransaction` | `PAYMENT_VOIDED` / `PAYMENT_REFUNDED` | `PAYMENT_TRANSACTION` |
| `TuitionFeeService#cancel` / `delete` | `FEE_CANCELLED` / `FEE_DELETED` | `TUITION_FEE` |

Đổi tên method trong bảng trên sẽ làm `AuditTrailAspectTest` đỏ (kiểm tra mọi khóa còn khớp interface).

## Đã nối dây khi gộp stream A (feat/portal-p0)

Ghi bằng hook `AuditService` ngay trong mã của stream A (không qua aspect):

| Nơi gọi | ACTION | RESOURCE_TYPE / ID | DETAIL (không bao giờ có mật khẩu) |
|---|---|---|---|
| `StudentAccountService#bulkCreate` – mỗi tài khoản tạo mới | `STUDENT_ACCOUNT_PROVISIONED` | `USER` / userId mới | `studentId`, `studentCode`, `username`, `relation=SELF`, `role=ROLE_STUDENT` |
| `StudentAccountService#bulkCreate` – gỡ liên kết SELF cũ của tài khoản đã xóa | `STUDENT_LINK_REMOVED` | `STUDENT` / studentId | `linkId`, `userId`, `relation`, `reason=LINKED_ACCOUNT_DELETED` |
| `StudentAccountService#resetPassword` | `PASSWORD_RESET` | `USER` / userId | `accountType=STUDENT`, `username` |
| `StudentAccountService#lock` / `unlock` | `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED` | `USER` / userId | `accountType=STUDENT`, `username` |
| ba thao tác trên với userId không phải tài khoản học sinh | như trên, `RESULT=FAILURE` | `USER` / userId | `accountType=STUDENT`, `errorCode=USER_NOT_FOUND` |
| `PermissionInterceptor` – hàng rào loại tài khoản chặn (`STAFF_ONLY` / `STUDENT_ONLY`) | `PORTAL_ACCESS_DENIED`, `RESULT=DENIED` | `API` / – | `code`, `zone`, `method`, `path` (mẫu đường dẫn) |

- Thành công ghi SAU commit (cả lô tạo tài khoản rollback ⇒ không có dòng `STUDENT_ACCOUNT_PROVISIONED` nào); học sinh bị
  bỏ qua (`SKIPPED`) không ghi.
- `USER_TYPE` lấy từ `PrincipalAuditUserTypeResolver` (bean `AuditUserTypeResolver`: loại tài khoản của principal, dựng
  lại từ `SYS_USERS.USER_TYPE` mỗi request). `LOGIN_SUCCESS` lấy loại tài khoản từ kết quả đăng nhập.

## Hook cho module khác (stream A: cấp tài khoản học sinh, liên kết, hàng rào cổng)

```java
private final AuditService auditService;

auditService.record(AuditEvent.builder()
        .action(AuditActions.STUDENT_ACCOUNT_PROVISIONED)
        .resource(AuditActions.RESOURCE_USER, userId)
        .detail("studentId", studentId)
        .build());

// Rút gọn:
auditService.denied(AuditActions.PORTAL_ACCESS_DENIED, AuditActions.RESOURCE_STUDENT, studentId, Map.of("path", path));
```

- Người thao tác, IP, User-Agent, thời điểm lấy tự động từ request / `SecurityContext`; `actor(id, username)` chỉ khi
  chưa đăng nhập.
- `AuditService` **không bao giờ ném lỗi**; ghi trong transaction riêng (`REQUIRES_NEW`). Gọi trong transaction đang mở:
  `SUCCESS` ghi sau commit (rollback ⇒ không ghi), `FAILURE`/`DENIED` ghi ngay.
- `USER_TYPE`: khai báo một bean `AuditUserTypeResolver` (ví dụ đọc `SYS_USERS.USER_TYPE` của V17_1) – chưa có bean thì để `NULL`.
- **Không truyền** mật khẩu, token, cookie vào `detail` (khóa chứa `password`, `token`, `secret`, `cookie`,
  `authorization`, `otp`, `session`, `csrf`, `apiKey`... vẫn bị che `[REDACTED]` để phòng hờ). `DETAIL` là JSON rút gọn ≤ 4000 byte.
- Hằng số sẵn có cho stream A: `STUDENT_ACCOUNT_PROVISIONED`, `STUDENT_LINK_CREATED`, `STUDENT_LINK_REMOVED`,
  `PORTAL_ACCESS_DENIED`, `FILE_DOWNLOADED`.
