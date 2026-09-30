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

## Cấu hình web: CORS, header bảo mật, múi giờ, logging

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| `CORS_ALLOWED_ORIGINS` | non-prod: `http://localhost:5173,http://localhost:8088,http://localhost:3000`; prod: rỗng | Origin được gọi `/api/**` cross-origin, phân tách bằng dấu phẩy (hỗ trợ pattern `*`, ví dụ `http://192.168.1.*:8081`). Rỗng = chỉ same-origin |
| `TZ` | `Asia/Ho_Chi_Minh` (Dockerfile, compose) | Múi giờ OS trong container |

- **CORS**: UI Docker gọi `/api` qua nginx cùng origin nên prod để trống. UI deploy riêng (IIS, domain/cổng khác
  gọi thẳng API, ví dụ `env.home.js` / `env.production.js` của `education_ui`) **phải** khai báo origin của UI, nếu không
  trình duyệt chặn request. Không bật `allowCredentials` (JWT gửi qua header `Authorization`, không dùng cookie).
  Header `Content-Disposition` được expose để UI đọc tên file khi tải về.
- **Swagger/OpenAPI**: tắt ở profile `prod` (`springdoc.api-docs.enabled=false`, `swagger-ui.enabled=false`).
  Ngoài `/api/**`, chỉ `/`, `/favicon.ico`, `/error`, `/swagger-ui/**`, `/v3/api-docs/**` được truy cập; đường dẫn khác bị từ chối.
- **Header bảo mật**: response API có `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`;
  Swagger UI dùng CSP nới lỏng (chỉ tài nguyên cùng origin). HSTS (`max-age=31536000`) chỉ gửi khi request là HTTPS;
  nếu TLS kết thúc ở reverse proxy, cần cấu hình proxy gửi `X-Forwarded-Proto` và bật
  `server.forward-headers-strategy` (chỉ khi API không bị truy cập trực tiếp từ ngoài proxy).
- **Múi giờ**: DB lưu `DATE`/`TIMESTAMP` không kèm time zone theo giờ Việt Nam (`SYSDATE` của Oracle trên máy host,
  `LocalDateTime.now()` của Java). Ứng dụng cố định `Asia/Ho_Chi_Minh` ở mọi nơi: JVM (`EducationApplication` +
  `-Duser.timezone` trong Dockerfile), `TZ` của container, `spring.jpa.properties.hibernate.jdbc.time_zone`,
  `spring.jackson.time-zone`. Giờ Việt Nam và `Asia/Bangkok` (Windows "SE Asia Standard Time") cùng UTC+7 nên dữ liệu
  đã ghi từ máy dev không bị lệch. Nếu Oracle báo `ORA-01882: timezone region not found` khi kết nối, thêm
  `-Doracle.jdbc.timezoneAsRegion=false` vào `JAVA_TOOL_OPTIONS`.
- **Lỗi Database**: lỗi Oracle thô (`ORA-xxxxx`) không trả về client, chỉ ghi log ERROR; client nhận
  "Có lỗi xử lý dữ liệu, vui lòng thử lại hoặc liên hệ quản trị viên.". Lỗi nghiệp vụ
  `RAISE_APPLICATION_ERROR(-20xxx, 'text')` trả `text` (bỏ tiền tố `ORA-20xxx:`). Mã lỗi `*_NOT_FOUND` trả HTTP 404.
- **Logging**: mặc định `INFO`, không in SQL. Debug SQL + bind parameter ở máy dev:
  `SPRING_PROFILES_ACTIVE=local-logging` (hoặc thêm vào danh sách profile, ví dụ `dev,local-logging`), cấu hình ở
  `application-local-logging.yml`. Không bật ở production (TRACE ghi cả giá trị tham số).

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

Neu `admin` / `Admin@123` bao sai mat khau (DB tao tu `schema_init.sql` cu co hash BCrypt "mau" khong khop mat khau nao, V12 ban cu bo qua), chay script sua (idempotent, dat lai admin ve `Admin@123`, tai khoan demo con hash "mau"/SHA-256 nhu `teacher1` ve `Education@123`, tat ca bat buoc doi mat khau):

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
