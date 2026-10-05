# Triển khai EDUCATION ra Internet (Giai đoạn 0 – hạ tầng public)

Tài liệu này mô tả cách đưa hệ thống (API + UI) đang chạy Docker cục bộ ra Internet để học sinh / phụ huynh truy cập
cổng học sinh từ nhà. Mục tiêu: **chỉ một cổng HTTPS duy nhất ra ngoài**, cookie đăng nhập `Secure`, Oracle không bao
giờ lộ ra Internet, có sao lưu và cách quay lui.

> Docker cục bộ (`docker-compose.yml`, UI `8088`, API `8090`) **không thay đổi** – mọi thiết lập Internet nằm trong
> `docker-compose.prod.yml` (project riêng `education-prod`) và chỉ bật khi đặt `APP_INTERNET_FACING=true`.

## 1. Kiến trúc

```
Internet ──80/443──▶ caddy (TLS Let's Encrypt, HSTS, chặn /v3, /swagger, /actuator)
                       │  http://ui:80  (mạng Docker nội bộ)
                       ▼
                     ui (nginx: SPA + CSP/nosniff/X-Frame-Options, /api/ → http://api:8080)
                       │
                       ▼
                     api (Spring Boot, profile prod, APP_INTERNET_FACING=true)
                       │  jdbc (host.docker.internal:1521)
                       ▼
                     Oracle trên máy chủ  ── cổng 1521 CHỈ nghe nội bộ, KHÔNG mở firewall
```

- Chỉ service `caddy` có `ports` (80, 443/tcp, 443/udp cho HTTP/3). `ui` và `api` chỉ `expose` trong mạng Docker.
- UI và API **cùng origin** (`https://<tên miền>`, API qua `/api`) ⇒ không cần CORS (`CORS_ALLOWED_ORIGINS` để trống),
  cookie `SameSite=Strict` hoạt động bình thường.
- IP thật của người dùng: Caddy đặt `X-Forwarded-For` (bỏ giá trị client tự gửi) và `X-Forwarded-Proto: https`,
  nginx của UI nối thêm IP của Caddy vào `X-Forwarded-For`, giữ nguyên `X-Forwarded-Proto` và `Host`; Tomcat
  (`server.forward-headers-strategy=native`, RemoteIpValve) chỉ tin các proxy có IP nội bộ (10/8, 172.16/12 – mạng
  Docker, 192.168/16, 127/8…) ⇒ `getRemoteAddr()` = IP người dùng, `isSecure()` = true: rate limit và nhật ký hệ thống
  ghi đúng IP. Client gọi thẳng API từ IP public tự gửi `X-Forwarded-For` thì bị bỏ qua (kiểm thử:
  `ForwardedHeadersConfigurationTest`). Đổi dải proxy tin cậy (hiếm khi cần): `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`.

## 2. Chuẩn bị

| Hạng mục | Yêu cầu |
|---|---|
| Máy chủ | Máy hiện tại (Windows + Docker Desktop) hoặc VPS. Có IP public tĩnh, hoặc NAT/port-forward 80 + 443 từ router về máy chủ. |
| Tên miền | Ví dụ `hocsinh.trungtam.vn`. Tạo bản ghi DNS `A` (và `AAAA` nếu có IPv6) trỏ về IP public. Kiểm tra: `nslookup hocsinh.trungtam.vn`. |
| Firewall | **Mở**: 80/tcp (cấp chứng chỉ + chuyển hướng), 443/tcp, 443/udp. **Đóng**: 1521 (Oracle), 8088, 8090, 5500 (EM Express), RDP/SSH chỉ từ IP quản trị. |
| Email | Địa chỉ nhận cảnh báo hết hạn chứng chỉ (`ACME_EMAIL`). |
| Image | `education-api:prod` (repo này, `scripts/docker-build-prod.ps1`) và `education-ui:prod` (repo UI). |

Kiểm tra Oracle không lộ ra ngoài (chạy từ một máy **ngoài** mạng trung tâm, ví dụ điện thoại 4G + app port scan,
hoặc `Test-NetConnection <IP public> -Port 1521` từ máy khác): phải **thất bại**.

Trên Windows, chặn 1521 từ ngoài (chạy PowerShell quyền Administrator):

```powershell
New-NetFirewallRule -DisplayName "Block Oracle 1521 inbound (public)" -Direction Inbound -Protocol TCP `
  -LocalPort 1521 -Profile Public -Action Block
```

> Container `api` kết nối Oracle qua `host.docker.internal` (mạng nội bộ của Docker), không cần mở 1521 ra Internet.

## 3. Các bước triển khai

1. **Chạy migration** (một lần, trên DB thật, xem mục 5): `V17_1`, `V17_2` (stream A) và `V17_3__audit_log.sql`.
2. **Tạo file biến môi trường** (không commit – `.env.prod` đã nằm trong `.gitignore`):

   ```powershell
   Copy-Item deploy\env.prod.example .env.prod
   notepad .env.prod   # điền PUBLIC_DOMAIN, ACME_EMAIL, DB_PASSWORD, JWT_SECRET
   ```

   Sinh `JWT_SECRET` ngẫu nhiên ≥ 32 byte, **khác** secret của Docker cục bộ:

   ```powershell
   $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
   ```

   (hoặc `openssl rand -base64 48`).
3. **Kiểm tra cấu hình** (không khởi động gì):

   ```powershell
   docker compose -f docker-compose.prod.yml --env-file .env.prod config
   ```
4. **Khởi động**:

   ```powershell
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
   docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f caddy api
   ```

   Caddy tự xin chứng chỉ Let's Encrypt khi DNS đã trỏ đúng và cổng 80/443 thông. API từ chối khởi động nếu cấu hình
   không an toàn (cookie không `Secure`, CORS có origin `http://`) – đọc log `InternetExposureValidator`.
5. **Kiểm tra** theo mục 6.

> Không chạy song song stack cục bộ và stack prod trên cùng máy nếu cả hai cùng trỏ một DB mà không có lý do – có thể,
> nhưng hai stack dùng volume file khác nhau (`education-api_education-outputs` và `education-prod-outputs`). Muốn
> dùng lại file đính kèm cũ: khôi phục bản sao lưu volume (mục 7) vào volume mới, hoặc đặt
> `UPLOADS_VOLUME=education-api_education-outputs`.

## 4. Biến môi trường

### 4.1. Bắt buộc (docker-compose.prod.yml)

| Biến | Ý nghĩa |
|---|---|
| `PUBLIC_DOMAIN` | Tên miền công khai, ví dụ `hocsinh.trungtam.vn` (Caddy cấp chứng chỉ cho tên này). |
| `ACME_EMAIL` | Email đăng ký Let's Encrypt. |
| `DB_PASSWORD` | Mật khẩu Oracle của `EDUCATION` (đổi khỏi mặc định trước khi lên Internet). |
| `JWT_SECRET` | Secret ký JWT, ngẫu nhiên ≥ 32 byte. |

### 4.2. Mới ở Giai đoạn 0 – stream B

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `APP_INTERNET_FACING` | `false` (prod compose: `true`) | Bật kiểm tra khi khởi động: cookie `Secure`, CORS chỉ `https://`; cảnh báo nếu tắt rate limit / bật Swagger / forward headers `none`. |
| `AUDIT_ENABLED` | `true` | Ghi nhật ký hệ thống `SYS_AUDIT_LOGS`. Lỗi ghi (chưa chạy V17_3, DB lỗi) chỉ log WARN. |
| `RATE_LIMIT_ENABLED` | `true` | Bật giới hạn tần suất tổng quát (ngoài login/refresh). |
| `RATE_LIMIT_MAX_TRACKED_KEYS` | `100000` | Số khóa đếm tối đa trong bộ nhớ (chống tràn RAM). |
| `RATE_LIMIT_CHANGE_PASSWORD_WINDOW` / `_PER_IP` / `_PER_USER` | `15m` / `200` / `5` | `POST /api/v1/auth/change-password`. |
| `RATE_LIMIT_PORTAL_WINDOW` / `_PER_IP` / `_PER_USER` | `5m` / `0` / `300` | Mọi request `/api/v1/portal/**`. Theo IP mặc định tắt (cả lớp dùng chung IP NAT). |
| `RATE_LIMIT_UPLOAD_WINDOW` / `_PER_IP` / `_PER_USER` | `1h` / `300` / `60` | Request `multipart/*` (POST/PUT/PATCH) tới `/api/**`: tải file, nhập Excel, (GĐ2) nộp bài. |
| `UPLOADS_VOLUME` | `education-prod-outputs` | Tên volume file đính kèm của stack prod (script sao lưu dùng tên này). |
| `CADDY_VERSION` | `2.8-alpine` | Tag image Caddy. |
| `UI_BUILD_CONTEXT` | `../education_ui` | Thư mục repo UI khi `docker compose build`. |

Vượt ngưỡng ⇒ HTTP **429** + header `Retry-After` (giây) + body `{"code":"TOO_MANY_REQUESTS",...}`; header CORS vẫn
được gắn để UI đọc được lỗi. Bộ đếm nằm trong bộ nhớ **từng instance** API (khởi động lại = đếm lại); chạy nhiều
instance thì thay bean `RateLimiter` bằng bản dùng chung (Redis/DB).

### 4.3. Đã có, cần xem lại khi ra Internet

| Biến | Gợi ý |
|---|---|
| `AUTH_RATE_LIMIT_LOGIN_PER_IP` (`200`/5 phút) | Mặc định đã rộng vì cả trung tâm dùng chung một IP public (NAT): cả lớp đăng nhập cùng lúc từ Wi-Fi trung tâm không chạm ngưỡng. Giới hạn theo tên đăng nhập (`AUTH_RATE_LIMIT_LOGIN_PER_USERNAME`, `10`/5 phút) và khóa tài khoản tạm thời vẫn bảo vệ từng tài khoản. Trung tâm rất đông (nhiều lớp đăng nhập cùng lúc) thì tăng tiếp. |
| `AUTH_COOKIE_SECURE` | Luôn `true` trên Internet (compose prod cố định `true`). |
| `CORS_ALLOWED_ORIGINS` | Để trống (cùng origin). Nếu bắt buộc khác origin: chỉ `https://...` cụ thể, không wildcard. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native` (compose prod cố định). |

### 4.4. Địa chỉ đăng nhập in trên phiếu tài khoản học sinh (`PUBLIC_LOGIN_URL`)

Phiếu tài khoản (màn hình **Hệ thống → Tài khoản học sinh**: in phiếu / sao chép / xuất CSV) ghi địa chỉ đăng nhập
cho học sinh. Mặc định là địa chỉ trang nhân viên đang mở + `/login` – nếu nhân viên in phiếu từ máy trong LAN
(`http://192.168.1.10:8088`) thì phiếu sẽ ghi địa chỉ LAN, học sinh ở nhà không vào được. Đặt địa chỉ công khai cố định:

| Cách chạy UI | Cấu hình |
|---|---|
| `docker-compose.prod.yml` | Biến `PUBLIC_LOGIN_URL` của service `ui`; mặc định `https://${PUBLIC_DOMAIN}/login` (không cần đặt). Đổi trong `.env.prod` nếu cần. |
| Docker cục bộ (`docker-compose.yml`, UI `8088`) | Đặt `PUBLIC_LOGIN_URL=https://hocsinh.trungtam.vn/login` trong `.env` rồi `docker compose up -d ui` (không cần build lại). Để trống = hành vi cũ. |
| IIS / file tĩnh (`dist/`) | Sửa `PUBLIC_LOGIN_URL` trong `env.js` cạnh `index.html` (mẫu: `env/runtime/env.*.js` của repo UI). |

- Cơ chế: image UI có script `/docker-entrypoint.d/40-education-runtime-env.sh` chạy mỗi lần container khởi động,
  ghi biến môi trường `PUBLIC_LOGIN_URL` vào `/usr/share/nginx/html/env.js` (`window.__EDUCATION_ENV__.PUBLIC_LOGIN_URL`).
  Log khởi động container `ui` in giá trị đang dùng: `docker compose logs ui | Select-String PUBLIC_LOGIN_URL`.
- Chỉ nhận `http://` / `https://`, không khoảng trắng / dấu nháy; giá trị sai bị bỏ qua (cảnh báo trong log `ui`) và
  phiếu quay về địa chỉ trang đang mở. Chỉ ghi tên miền (`https://hocsinh.trungtam.vn`) thì tự thêm `/login`.
- Kiểm tra: mở `http://<máy chủ>:8088/env.js` (hoặc `https://<tên miền>/env.js`) thấy dòng `PUBLIC_LOGIN_URL`; tạo /
  đặt lại mật khẩu một tài khoản thử rồi xem phiếu.

## 5. Migration V17_3 (nhật ký hệ thống)

Script: `src/main/resources/db/migration/V17_3__audit_log.sql` – tạo `SEQ_SYS_AUDIT_LOGS`, bảng `SYS_AUDIT_LOGS`
(ID, EVENT_TIME, USER_ID, USERNAME, USER_TYPE, ACTION, RESOURCE_TYPE, RESOURCE_ID, IP, USER_AGENT, RESULT, DETAIL) +
4 index, menu `MENU_AUDIT_LOG` (`/system/audit-logs`, chức năng `VIEW`, cấp cho `ROLE_ADMIN`). Chạy lại nhiều lần
an toàn; không phụ thuộc V17_1/V17_2.

```powershell
$env:NLS_LANG = "AMERICAN_AMERICA.AL32UTF8"
F:\Database\bin\sqlplus.exe EDUCATION/<mật khẩu>@//localhost:1521/ORCL @src/main/resources/db/migration/V17_3__audit_log.sql
```

- Có thể chạy trước hoặc sau khi deploy backend: backend chưa có bảng thì chỉ log WARN, không lỗi request.
- Rollback: xem phần đầu script (xóa quyền/menu, `DROP TABLE SYS_AUDIT_LOGS PURGE`, `DROP SEQUENCE SEQ_SYS_AUDIT_LOGS`)
  – **mất toàn bộ nhật ký**, nên export trước.
- Lưu giữ: bảng chỉ ghi thêm; dọn định kỳ theo chính sách (ví dụ 12 tháng) bằng câu lệnh ở phần đầu script.

## 6. Danh sách kiểm tra sau triển khai

Thay `hocsinh.trungtam.vn` bằng tên miền thật.

- [ ] `https://hocsinh.trungtam.vn` mở được UI, chứng chỉ hợp lệ; `http://...` tự chuyển sang `https://`.
- [ ] Header (DevTools → Network, hoặc `curl.exe -sI https://hocsinh.trungtam.vn`):
      `Strict-Transport-Security`, `Content-Security-Policy` (có `frame-ancestors 'none'`), `X-Content-Type-Options: nosniff`,
      `X-Frame-Options: DENY`, `Referrer-Policy`; **không** có `Server`.
- [ ] Đăng nhập được; cookie `EDU_ACCESS_TOKEN` / `EDU_REFRESH_TOKEN` có `Secure`, `HttpOnly`, `SameSite=Strict`.
- [ ] `https://hocsinh.trungtam.vn/v3/api-docs`, `/swagger-ui.html` → 404.
- [ ] `Test-NetConnection <IP public> -Port 1521`, `-Port 8090`, `-Port 8088` từ mạng ngoài → thất bại.
- [ ] Sai mật khẩu nhiều lần → HTTP 429 có `Retry-After`; màn hình *Hệ thống → Nhật ký hệ thống* (hoặc
      `GET /api/v1/audit-logs/search?action=LOGIN_FAILED`) hiện bản ghi với **IP public của máy thử**, không phải IP
      Docker `172.x`.
- [ ] Log API khi khởi động có dòng `Chế độ Internet: cookie Secure, CORS same-origin - OK.`
- [ ] Đã chạy thử sao lưu (mục 7) và **khôi phục thử** ra một schema/máy khác.

## 7. Sao lưu và khôi phục

Cần sao lưu **cùng thời điểm**: schema Oracle `EDUCATION` và volume file đính kèm (`education-prod-outputs`) – DB
chứa đường dẫn file, file nằm trong volume.

### 7.1. Sao lưu (hằng ngày)

- Windows: `scripts/ops/backup.ps1`; Linux: `scripts/ops/backup.sh`.
- Oracle: `expdp schemas=EDUCATION` vào `DATA_PUMP_DIR`, `FLASHBACK_TIME=SYSTIMESTAMP` (bản chụp nhất quán), mật khẩu
  qua parfile tạm (không nằm trên dòng lệnh / lịch sử).
- Volume: `docker run --rm -v <volume>:/data:ro alpine tar czf ...` (chỉ đọc, không đụng container đang chạy).
- Mỗi lần tạo thư mục `<BackupRoot>\yyyyMMdd-HHmmss` kèm `SHA256SUMS.txt`; tự xóa bản cũ hơn `-RetentionDays` (mặc định 14).

Quyền cần có (DBA cấp một lần): `GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO EDUCATION;`

Lên lịch hằng ngày lúc 01:30 (Task Scheduler, chạy bằng tài khoản dịch vụ; đặt `EDU_DB_PASSWORD` ở biến môi trường
của tài khoản đó, không ghi trong lệnh):

```powershell
$action  = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument "-NoProfile -ExecutionPolicy Bypass -File E:\JAVA\education\scripts\ops\backup.ps1 -BackupRoot D:\Backup\education"
$trigger = New-ScheduledTaskTrigger -Daily -At 1:30am
Register-ScheduledTask -TaskName "EDUCATION backup" -Action $action -Trigger $trigger -RunLevel Highest
```

**Quy tắc 3-2-1**: sao chép thư mục sao lưu sang ít nhất một nơi khác máy chủ (ổ ngoài, NAS, cloud drive có mã hóa).
Bản sao lưu chứa dữ liệu cá nhân học sinh – giới hạn quyền truy cập thư mục.

### 7.2. Khôi phục

1. Kiểm tra toàn vẹn: so `Get-FileHash -Algorithm SHA256 <file>` với `SHA256SUMS.txt` (Linux: `sha256sum -c SHA256SUMS.txt`).
2. Oracle (dừng `api` trước: `docker compose -f docker-compose.prod.yml --env-file .env.prod stop api`):

   ```powershell
   Copy-Item D:\Backup\education\<stamp>\education-<stamp>.dmp <đường dẫn DATA_PUMP_DIR>
   # Khôi phục thử sang schema khác (an toàn):
   F:\Database\bin\impdp.exe system@//localhost:1521/ORCL directory=DATA_PUMP_DIR dumpfile=education-<stamp>.dmp `
     remap_schema=EDUCATION:EDUCATION_RESTORE logfile=impdp-<stamp>.log
   # Khôi phục đè (chỉ khi chắc chắn): table_exists_action=REPLACE thay cho remap_schema
   ```
3. Volume file (ghi đè nội dung volume):

   ```powershell
   docker run --rm -v education-prod-outputs:/data -v D:\Backup\education\<stamp>:/backup alpine `
     sh -c "cd /data && tar xzf /backup/uploads-<stamp>.tar.gz"
   ```
4. Khởi động lại `api`, kiểm tra theo mục 6.

## 8. Giám sát và vận hành

- **Uptime**: dịch vụ bên ngoài (UptimeRobot, Better Stack...) gọi `https://<tên miền>/api/v1/health` mỗi 1–5 phút,
  cảnh báo qua email/Zalo. Container `api` có `healthcheck` (`docker ps` hiện `healthy`).
- **Log**: `docker compose -f docker-compose.prod.yml --env-file .env.prod logs --since 1h api caddy`; log được xoay vòng
  (10 MB × 5 file / container). Theo dõi `WARN` của `AuditServiceImpl` (không ghi được nhật ký) và `Vượt giới hạn tần suất`.
- **Nhật ký hệ thống**: màn hình *Nhật ký hệ thống* / `GET /api/v1/audit-logs/search` (quyền `MENU_AUDIT_LOG:VIEW`):
  đăng nhập thành công/thất bại, đăng xuất, đổi/đặt lại mật khẩu, khóa/mở khóa, thay đổi vai trò/quyền, xác nhận/hủy/hoàn
  tiền thanh toán, hủy/xóa khoản phí. Không bao giờ chứa mật khẩu, token, cookie.
- **Chứng chỉ**: Caddy tự gia hạn; dữ liệu chứng chỉ trong volume `caddy-data` (đừng xóa – Let's Encrypt giới hạn số lần cấp).
- **Cập nhật**: build image mới → `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d api ui`.
  Chạy migration mới trước khi bật code phụ thuộc vào nó.

## 9. Quay lui (rollback)

| Tình huống | Cách làm |
|---|---|
| Bản API mới lỗi | Đổi `IMAGE_TAG` về tag cũ (ví dụ `prod-<sha cũ>`, do `scripts/docker-build-prod.ps1` tạo) rồi `up -d api`. |
| Muốn rút khỏi Internet | `docker compose -f docker-compose.prod.yml --env-file .env.prod down` (giữ volume; **không** dùng `-v`), đóng 80/443 ở firewall/router. Stack cục bộ vẫn chạy như cũ. |
| Lỗi do V17_3 | Code mới chạy được khi chưa có bảng (chỉ WARN). Gỡ hẳn: phần ROLLBACK ở đầu script. |
| Dữ liệu hỏng | Khôi phục theo mục 7.2 từ bản sao lưu gần nhất. |

## 10. Lưu ý đã biết

- **X-Forwarded-Proto**: nginx của UI giữ nguyên giá trị Caddy gửi (`map $http_x_forwarded_proto ...`, chỉ nhận
  `http`/`https`; không có thì dùng `$scheme`) nên API thấy đúng `https`. Ở Docker cục bộ (nginx là cửa vào) người dùng
  có thể tự gửi header này - chỉ ảnh hưởng `isSecure()` của chính request đó; cookie `Secure` vẫn theo cấu hình.
- **Docker cục bộ / LAN**: với Docker Desktop, kết nối vào cổng publish (8088, 8090) thường hiện IP gateway của Docker
  (172.x / 192.168.65.x – thuộc dải nội bộ), nên mọi máy trong LAN có thể chung một "IP" và tự gửi `X-Forwarded-For`.
  Không ảnh hưởng triển khai Internet (chỉ Caddy publish cổng, Caddy bỏ `X-Forwarded-For` của client); đừng mở cổng
  8090 của API ra ngoài.
- **Rate limit trong bộ nhớ**: chỉ đúng với **một** instance API.
- **NAT trung tâm**: xem `AUTH_RATE_LIMIT_LOGIN_PER_IP` ở mục 4.3; giới hạn cổng học sinh mặc định chỉ tính theo người dùng.
- **Actuator** chưa có trong ứng dụng (health dùng `/api/v1/health`); profile prod đã cấu hình sẵn chỉ mở `health` nếu sau
  này thêm, và Spring Security chặn mọi đường dẫn ngoài `/api/**`.
