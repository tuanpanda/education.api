# education.api

Spring Boot 3.3 / Java 21 / Oracle Hybrid API.

## Docker production

Moi image deu dung profile `prod`. Build tu dong khi co Pull Request (GitHub Actions self-hosted tren Docker Desktop).

### Build tay

```powershell
.\scripts\docker-build-prod.ps1
docker compose up -d
```

Image: `education-api:prod` (API, cổng 8090), `education-ui:prod` (giao diện, cổng 8088).
Mở giao diện Docker: http://localhost:8088

### CI (Pull Request + version tag)

1. Mo Docker Desktop.
2. Dang ky runner (token lay o GitHub: Settings → Actions → Runners → New self-hosted runner):

```powershell
.\scripts\register-self-hosted-runner.ps1 -Token "<RUNNER_TOKEN>"
C:\actions-runner\education-api\run.cmd
```

3. Mo Pull Request: workflow **Docker production build** tao image:

| Su kien | Tag |
| --- | --- |
| Pull Request `#12` | `education-api:prod-pr-12`, `prod-pr-12-<sha>`, `prod-build-<run>`, `prod` |
| Git tag `v1.2.0` | `education-api:v1.2.0`, `prod-v1.2.0`, `prod` |
| Push `main` | `education-api:prod`, `prod-build-<run>`, `sha-<sha>` |

Phat hanh version:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

Oracle tren may host: container ket noi `host.docker.internal:1521`. Copy `.env.example` thanh `.env` neu can doi thong tin DB.

## Bảo mật & quản trị hệ thống (JWT)

Mọi API `/api/**` yêu cầu header `Authorization: Bearer <accessToken>`, trừ:
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `GET /api/v1/health`, Swagger (`/swagger-ui.html`, `/v3/api-docs`).

1. Chạy migration mới (sqlplus):

```powershell
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V12__system_admin_security.sql
```

2. Tài khoản mặc định sau V12 (bắt buộc đổi mật khẩu ở lần đăng nhập đầu):

| Tài khoản | Mật khẩu | Ghi chú |
| --- | --- | --- |
| `admin` | `Admin@123` | Vai trò `ROLE_ADMIN` (toàn quyền) |
| `teacher1`, `accountant1`, `admission1` | `Education@123` | Tài khoản demo (nếu còn mật khẩu seed V1) |

Neu `admin` / `Admin@123` bao sai mat khau (DB tao tu `schema_init.sql` cu co hash BCrypt "mau" khong khop mat khau nao, V12 ban cu bo qua), chay script sua (idempotent, dat lai admin ve `Admin@123` + bat buoc doi mat khau):

```powershell
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/fix_admin_password.sql
```

3. Biến môi trường:

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| `JWT_SECRET` | chỉ có giá trị dev trong `application.yml` | **Bắt buộc** ở profile `prod` / Docker, tối thiểu 32 ký tự (`openssl rand -base64 48`) |
| `JWT_ISSUER` | `education-api` | Claim `iss` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Thời hạn access token (Duration: `15m`, `1h`...) |
| `JWT_REFRESH_TOKEN_TTL` | `7d` | Thời hạn refresh token |

Phân quyền: mã quyền dạng `MENU_CODE:FUNCTION_CODE` (ví dụ `MENU_STUDENT_LIST:CREATE`) lấy từ
`SYS_ROLE_MENU_PERMISSIONS`; controller khai báo `@RequirePermission(...)`, `PermissionInterceptor` kiểm tra
(`ROLE_ADMIN` luôn được phép). Đăng xuất / đổi mật khẩu / khóa tài khoản tăng `SYS_USERS.TOKEN_VERSION`
để vô hiệu hóa mọi token đã cấp.
