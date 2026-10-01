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
| `CORS_ALLOWED_ORIGINS` | non-prod: `http://localhost:5173,http://localhost:8088,http://localhost:3000`; prod: rỗng | Origin được gọi `/api/**` cross-origin, phân tách bằng dấu phẩy (hỗ trợ pattern `*` trong phần host, ví dụ `http://192.168.1.*:8081`; pattern khớp mọi host như `*`, `http://*` bị từ chối khi khởi động). Rỗng = chỉ same-origin |
| `TZ` | `Asia/Ho_Chi_Minh` (Dockerfile, compose) | Múi giờ OS trong container |

- **CORS**: UI Docker gọi `/api` qua nginx cùng origin nên prod để trống. UI deploy riêng (IIS, domain/cổng khác
  gọi thẳng API, ví dụ `env.home.js` / `env.production.js` của `education_ui`) **phải** khai báo origin của UI, nếu không
  trình duyệt chặn request. CORS bật `allowCredentials` (token nằm trong cookie HttpOnly, UI gọi `fetch` với
  `credentials: "include"`), chỉ cho header `Accept`, `Accept-Language`, `Content-Type`, `X-XSRF-TOKEN`,
  `X-Requested-With`. Header `Content-Disposition`, `Retry-After` được expose để UI đọc tên file / thời gian chờ.
  UI và API phải cùng **site** (cùng host khác cổng, hoặc chung tên miền gốc như `edu.example.vn` /
  `api.edu.example.vn`) vì cookie đăng nhập dùng `SameSite=Strict`; host trong `API_BASE_URL` của UI phải trùng host
  người dùng gõ trên thanh địa chỉ (không trộn `localhost` với IP).
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
  `SPRING_PROFILES_ACTIVE=dev,local-logging` (giữ `dev`: khi đã đặt `SPRING_PROFILES_ACTIVE`, profile mặc định `dev`
  không còn tự bật nên thiếu JWT secret dev và ứng dụng không khởi động), cấu hình ở `application-local-logging.yml`. Không bật ở production (TRACE ghi cả giá trị tham số).

## Bảo mật & quản trị hệ thống (JWT trong cookie HttpOnly)

Mọi API `/api/**` yêu cầu access token hợp lệ trong cookie `EDU_ACCESS_TOKEN`, trừ:
`GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`,
`GET /api/v1/health`, Swagger (`/swagger-ui.html`, `/v3/api-docs`). Header `Authorization: Bearer` **không còn được
chấp nhận** (token không còn trả về cho JavaScript; Swagger UI cùng origin dùng cookie).

| Cookie | Nội dung | Thuộc tính |
| --- | --- | --- |
| `EDU_ACCESS_TOKEN` | access token (JWT) | `HttpOnly`, `SameSite=Strict`, `Path=/api`, `Max-Age` = `JWT_ACCESS_TOKEN_TTL`, `Secure` theo `AUTH_COOKIE_SECURE`, host-only (không `Domain`) |
| `EDU_REFRESH_TOKEN` | refresh token (JWT, xoay vòng) | `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth` (chỉ API xác thực: refresh, logout...; API nghiệp vụ không nhận), `Max-Age` = `JWT_REFRESH_TOKEN_TTL`, `Secure` theo `AUTH_COOKIE_SECURE` |
| `XSRF-TOKEN` | CSRF token | JS đọc được, `SameSite=Strict`, `Path=/`, cookie phiên trình duyệt |

- `POST /login`, `/refresh`, `/change-password` đặt cặp cookie mới và chỉ trả **thông tin người dùng** (vai trò, quyền,
  `mustChangePassword`), không còn `accessToken`/`refreshToken` trong body. `GET /api/v1/auth/me` trả cùng thông tin để
  UI khôi phục phiên khi tải lại trang.
- **CSRF**: cookie được gửi tự động nên mọi request `POST/PUT/PATCH/DELETE` (kể cả đăng nhập, chống "login CSRF") phải
  có header `X-XSRF-TOKEN` trùng cookie `XSRF-TOKEN` (Spring Security `CookieCsrfTokenRepository`, double-submit).
  Lấy token qua `GET /api/v1/auth/csrf` (`{"headerName":"X-XSRF-TOKEN","token":"..."}`). Thiếu / sai token ->
  HTTP 403 mã `CSRF_TOKEN_INVALID` (UI tự lấy token mới và gửi lại một lần). Swagger UI tự gửi header này
  (`springdoc.swagger-ui.csrf.enabled=true`).
- Gọi API bằng curl / Postman: dùng cookie jar, ví dụ
  `curl -c jar -b jar http://localhost:8090/api/v1/auth/csrf` rồi gửi `-H "X-XSRF-TOKEN: <token>"` cho request ghi.

1. Chạy migration mới (sqlplus), theo thứ tự, TRƯỚC khi deploy backend mới (xem mục
[Migration V13](#migration-v13-bản-vá-bảo-mật) bên dưới):

```powershell
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V12__system_admin_security.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_1__authz.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_2__auth_tokens.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_3__files_tuition.sql
```

2. Tài khoản khởi tạo: V12 tạo `admin` (vai trò `ROLE_ADMIN`) và chuyển tài khoản demo sang BCrypt với **mật khẩu
tạm thời** ghi trong chú thích của `V12__system_admin_security.sql`. Mọi tài khoản này bị buộc đổi mật khẩu ở lần
đăng nhập đầu: **đổi mật khẩu NGAY sau khi cài đặt**, không dùng mật khẩu tạm thời trên môi trường thật.

Nếu `admin` báo sai mật khẩu vì DB tạo từ `schema_init.sql` cũ (hash BCrypt "mẫu" không khớp mật khẩu nào) hoặc
còn hash không phải BCrypt, chạy script vận hành (không đóng gói trong jar). Script CHỈ sửa dòng có hash "mẫu" hoặc
không phải BCrypt (`NOT LIKE '$2%'`), nên chạy lại không bao giờ đặt lại mật khẩu đã đổi; dòng được sửa nhận lại mật
khẩu tạm thời của V12 và bị buộc đổi mật khẩu:

```powershell
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @scripts/db/fix_admin_password.sql
```

3. Biến môi trường:

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| `JWT_SECRET` | không có (chỉ profile `dev` có secret dev trong `application-dev.yml`) | **Bắt buộc** ngoài dev, tối thiểu 32 byte UTF-8 (`openssl rand -base64 48`). Ứng dụng không khởi động nếu secret ngắn hơn, hoặc nếu profile `prod` dùng secret dev/test/ví dụ |
| `JWT_ISSUER` | `education-api` | Claim `iss` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Thời hạn access token (Duration: `15m`, `1h`...) |
| `JWT_REFRESH_TOKEN_TTL` | `7d` | Thời hạn refresh token |
| `JWT_REFRESH_REUSE_GRACE` | `10s` | Nhiều tab làm mới cùng lúc bằng một refresh token trong khoảng này không bị coi là dùng lại (`0s` = tắt) |
| `AUTH_LOCKOUT_MAX_FAILED_ATTEMPTS` | `5` | Mỗi N lần sai mật khẩu liên tiếp thì khóa tạm thời |
| `AUTH_LOCKOUT_BASE_DURATION` / `AUTH_LOCKOUT_MAX_DURATION` | `15m` / `24h` | Lần khóa đầu / trần; mỗi lần khóa tiếp theo gấp đôi |
| `AUTH_RATE_LIMIT_ENABLED` | `true` | Giới hạn tần suất `/api/v1/auth/login`, `/refresh` (HTTP 429 + `Retry-After`) |
| `AUTH_RATE_LIMIT_WINDOW` | `5m` | Cửa sổ trượt |
| `AUTH_RATE_LIMIT_LOGIN_PER_IP` / `..._LOGIN_PER_USERNAME` | `30` / `10` | Số lần đăng nhập tối đa mỗi cửa sổ |
| `AUTH_RATE_LIMIT_REFRESH_PER_IP` / `..._REFRESH_PER_USER` | `120` / `60` | Số lần làm mới tối đa mỗi cửa sổ |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native` | Lấy IP thật từ `X-Forwarded-For` khi chạy sau proxy nội bộ (nginx của UI) |
| `AUTH_COOKIE_SECURE` | `true` (profile `dev`: `false`) | Thuộc tính `Secure` của cookie đăng nhập / CSRF: trình duyệt chỉ gửi qua HTTPS. **Production qua HTTPS: giữ `true`.** Trình duyệt chấp nhận cookie `Secure` trên `http://localhost`, nhưng mở UI/API qua HTTP bằng IP / tên máy (ví dụ `http://192.168.1.10:8088`) thì cookie bị bỏ và đăng nhập "thành công" nhưng mọi request sau trả 401 (log backend có cảnh báo) -> dùng HTTPS, hoặc đặt `false` (không khuyến nghị) |
| `AUTH_COOKIE_SAME_SITE` | `Strict` | `Strict` hoặc `Lax` cho mọi cookie trên (`None` không được hỗ trợ: UI và API phải cùng site) |

Chạy local trong IntelliJ không cần biến môi trường: không có `SPRING_PROFILES_ACTIVE` thì profile mặc định là `dev`.
Docker / production luôn chạy profile `prod`.

Đăng nhập & phiên:
- Sai mật khẩu 5 lần liên tiếp -> khóa tạm thời 15 phút (mã `ACCOUNT_TEMPORARILY_LOCKED`, thông báo kèm số phút còn lại);
  mỗi lần bị khóa tiếp theo gấp đôi (tối đa 24 giờ). Đăng nhập thành công đặt lại bộ đếm. Trạng thái `STATUS` không đổi.
- Refresh token xoay vòng (`SYS_REFRESH_TOKENS`): mỗi lần đăng nhập tạo một phiên (claim `sid`), mỗi lần
  `/refresh` (đọc refresh token từ cookie, body bị bỏ qua) thu hồi token cũ và đặt cookie token mới cùng phiên.
  Dùng lại một refresh token đã bị xoay vòng (`REFRESH_TOKEN_REUSED`) thu hồi cả phiên. Refresh thất bại (401) xóa
  cả hai cookie.
- `POST /api/v1/auth/logout` chỉ thu hồi phiên hiện tại (theo refresh token trong cookie - chạy được cả khi access
  token đã hết hạn - hoặc `sid` của access token) và luôn xóa cả hai cookie; các thiết bị khác vẫn đăng nhập.

### Chuyển token từ localStorage sang cookie HttpOnly (bản này)

- Không cần migration DB (không có script V14).
- **Mọi người dùng phải đăng nhập lại một lần**: UI mới xóa khóa `EDU_AUTH_SESSION` (token cũ) khỏi localStorage
  khi khởi động; backend không còn nhận header `Bearer` nên phiên cũ không dùng được nữa.
- Deploy backend và UI **cùng lúc** (UI cũ gửi Bearer + không gửi CSRF token -> 401/403 với backend mới).
- Docker: build lại cả hai image (`docker compose build && docker compose up -d`). Truy cập UI Docker qua HTTP bằng
  IP / tên máy (không phải `localhost`) thì đặt `AUTH_COOKIE_SECURE=false` trong `.env` hoặc đặt HTTPS phía trước.
- UI deploy riêng (IIS gọi API cổng 8090): khai báo `CORS_ALLOWED_ORIGINS` = origin của UI; host của API trong
  `env.js` phải trùng host của UI.

### Migration V13 (bản vá bảo mật)

Ba script tách riêng, chạy đúng thứ tự `V13_1` → `V13_2` → `V13_3` bằng sqlplus (từ thư mục gốc repo), sau V12.
Mỗi script idempotent (chạy lại an toàn) và dừng ngay ở lỗi SQL đầu tiên (`WHENEVER SQLERROR EXIT`):

```powershell
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_1__authz.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_2__auth_tokens.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_3__files_tuition.sql
```

| Thứ tự | Script | Nội dung |
| --- | --- | --- |
| 1 | `V13_1__authz.sql` | Index `IX_USER_ROLES_ROLE` trên `SYS_USER_ROLES (ROLE_ID, USER_ID)` cho khóa hàng khi kiểm tra "quản trị viên cuối cùng". Không đổi dữ liệu |
| 2 | `V13_2__auth_tokens.sql` | Cột `SYS_USERS.FAILED_LOGIN_COUNT`, `SYS_USERS.LOCKED_UNTIL`; bảng `SYS_REFRESH_TOKENS` + `SEQ_SYS_REFRESH_TOKENS`. Không đổi `STATUS` của tài khoản nào. **Bắt buộc** trước khi chạy backend mới (entity đã map các cột này) |
| 3 | `V13_3__files_tuition.sql` | Unique index `UQ_FIN_TRANS_BANK_REF` trên `FIN_PAYMENT_TRANSACTIONS.BANK_REFERENCE_NO`. Nếu dữ liệu đã có mã tham chiếu trùng, script dừng (`ORA-20133`) và in danh sách mã trùng để xử lý tay (không tự sửa / xóa dữ liệu); xử lý xong chạy lại |

**Sau V13_2, mọi người dùng phải đăng nhập lại một lần**: refresh token phát hành trước đó (chưa có trong
`SYS_REFRESH_TOKENS`) bị từ chối khi làm mới, UI đưa về trang đăng nhập.

Phân quyền: mã quyền dạng `MENU_CODE:FUNCTION_CODE` (ví dụ `MENU_STUDENT_LIST:CREATE`) lấy từ
`SYS_ROLE_MENU_PERMISSIONS`; controller khai báo `@RequirePermission(...)`, `PermissionInterceptor` kiểm tra
(`ROLE_ADMIN` luôn được phép). Đổi / đặt lại mật khẩu, khóa tài khoản tăng `SYS_USERS.TOKEN_VERSION`
để vô hiệu hóa mọi token đã cấp.
