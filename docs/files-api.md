# Hợp đồng API - Quản lý file và Kho tài liệu (Luồng 0)

> Phiên bản hợp đồng: **v1 - 07/10/2026**. Nhánh hợp đồng: backend `feat/files` (từ `feat/education-api`),
> frontend `feat/files-ui` (từ `main`). Kế hoạch chi tiết: `plans/ke-hoach-quan-ly-file.md`.
>
> Tài liệu này là **nguồn chuẩn** cho 4 luồng song song A/B/C/D. Mọi thay đổi hình dạng API, DTO, mã lỗi,
> quyền, migration sau ngày chốt phải đi qua mục [Yêu cầu thay đổi hợp đồng](#14-yeu-cau-thay-doi-hop-dong).

## Mục lục

1. [Quyết định đã chốt](#1-quyet-dinh-da-chot)
2. [Quy ước chung](#2-quy-uoc-chung)
3. [Quyền (menu / function)](#3-quyen-menu--function)
4. [API file lõi `/api/v1/files` (Luồng A)](#4-api-file-loi-apiv1files-luong-a)
5. [API quản trị file `/api/v1/admin/files` (Luồng B)](#5-api-quan-tri-file-apiv1adminfiles-luong-b)
6. [API kho tài liệu `/api/v1/documents` (Luồng D)](#6-api-kho-tai-lieu-apiv1documents-luong-d)
7. [Quy tắc hiển thị và ghi của kho tài liệu](#7-quy-tac-hien-thi-va-ghi-cua-kho-tai-lieu)
8. [Cổng học sinh - tài liệu lớp (tương lai, Giai đoạn 3)](#8-cong-hoc-sinh---tai-lieu-lop-tuong-lai-giai-doan-3)
9. [Mã lỗi](#9-ma-loi)
10. [Interface Java dùng chung](#10-interface-java-dung-chung)
11. [Cấu hình `application.yml`](#11-cau-hinh-applicationyml)
12. [Migration V19_x](#12-migration-v19_x)
13. [Hợp đồng frontend và bảng sở hữu file](#13-hop-dong-frontend-va-bang-so-huu-file)
14. [Yêu cầu thay đổi hợp đồng](#14-yeu-cau-thay-doi-hop-dong)

---

## 1. Quyết định đã chốt

Người dùng duyệt **phương án C (07/10/2026)**: làm toàn bộ khuyến nghị F1-F13 của kế hoạch, riêng **F6 đổi
hướng**: không ẩn `MENU_FILE_EXPLORER` mà **xây Kho tài liệu dùng chung** ngay trong dự án này.

| # | Quyết định |
|---|------------|
| Q1 | `MENU_FILE_EXPLORER` ("Kho Tài liệu", `/documents/files`) được dùng lại cho Kho tài liệu; màn hình template `FileManager` bị thay. V19_3 **không** ẩn menu này; V19_4 đặt `IS_HIDDEN=0`. |
| Q2 | Menu quản trị mới `MENU_FILE_MANAGER` ("Quản lý file", `/system/files`) dưới `DIR_SYSTEM`, chỉ `ROLE_ADMIN`. |
| Q3 | Phân trang mọi API mới: query `pageNo` (bắt đầu từ 1), `pageSize` (mặc định 20, tối đa 100); phản hồi `PageResponse` `{content,pageNo,pageSize,totalRows,totalPages}`. |
| Q4 | HTTP status theo `GlobalExceptionHandler` hiện có: lỗi nghiệp vụ 400, mã `*_NOT_FOUND` 404, `ForbiddenException` 403, vượt multipart 413. `FILE_INFECTED` trả **400** (kế hoạch cũ ghi 422). |
| Q5 | Kho tài liệu dùng `SYS_ATTACHED_FILES` làm nơi chứa blob, module file `LIBRARY`, `REFERENCE_ID = DOC_ITEMS.ID`. Upload thư viện chỉ qua `/api/v1/documents/items`; `POST /api/v1/files/upload` với `moduleName=LIBRARY` bị từ chối `INVALID_MODULE`. Xem / tải dùng lại `/api/v1/files/view|download/{fileId}`. |
| Q6 | Thay file của tài liệu: tăng `VERSION_NO`, ghi `VERSION_NOTE`, file cũ vào thùng rác (admin khôi phục được blob nhưng tài liệu vẫn trỏ file mới). Không có bảng lịch sử phiên bản. Khôi phục tài liệu đã xoá: ngoài phạm vi. |
| Q7 | Chia sẻ cho `ALL_STAFF` / `ROLE` / `CLASS`, gắn vào thư mục (kế thừa xuống cây con) hoặc từng tài liệu. `CLASS` có cờ `VISIBLE_TO_STUDENTS` để Giai đoạn 3 hiển thị trên cổng học sinh; endpoint cổng là việc tương lai. |
| Q8 | `ROLE_ADMIN` có mọi quyền mới. `ROLE_TEACHER`, `ROLE_ACCOUNTANT`: VIEW/UPLOAD/DOWNLOAD kho tài liệu (giữ như V1). `ROLE_ADMISSION` **mới được** VIEW/DOWNLOAD. Vai trò đã tuỳ biến chỉ được **thêm** quyền còn thiếu, không bị ghi đè. |
| Q9 | UPLOAD = quyền "chủ sở hữu": tạo tài liệu, sửa / xoá / chia sẻ tài liệu **của mình** (chia sẻ chỉ cho lớp mình dạy). UPDATE / DELETE / SHARE = áp dụng cho mọi tài liệu mình thấy. MANAGE = toàn quyền kho (CRUD thư mục, thấy mọi thứ). |
| Q10 | Thêm 2 interface tách phụ thuộc giữa luồng: `FileLifecycleService` (A cài, B và D gọi) và `FileSettingsSource` (A định nghĩa và dùng, B cài bằng bảng V19_2). |
| Q11 | Mã lỗi tách 3 file theo luồng: `FileErrorCodes` (A), `FileAdminErrorCodes` (B), `DocErrorCodes` (D). Mã có sẵn trước V19 giữ nguyên. |
| Q12 | `FileResponseDto.checksumSha256` trả SHA-256 đầy đủ (frontend tự rút gọn); thêm `fileExt`, `uploadedBy`, `canDelete`. |
| Q13 | Entity `FileEntity` đã có 15 cột mới: **phải chạy V19_1 trước khi triển khai code** (Hibernate đọc / ghi các cột này). `@PrePersist` gán mặc định `STATUS`, `STORAGE_BACKEND`, `SCAN_STATUS` vì Hibernate ghi NULL tường minh. |
| Q14 | Upload qua frontend dùng hàm mới `src/api/uploadWithProgress.ts` (Luồng C) thay vì sửa `client.ts`, để `client.ts` không phải chia khối. |

## 2. Quy ước chung

- Mọi API JSON trả `ApiResponse<T>`: `{ "code": "00", "message": "SUCCESS", "data": T, "timestamp": "2026-10-07T10:00:00" }`.
  Lỗi: `code` = mã lỗi (mục 9), `message` tiếng Việt hiển thị được, `data = null`.
- Endpoint trả file (view / download) trả **nhị phân**, không bọc `ApiResponse`; lỗi vẫn là JSON `ApiResponse`.
  `view` = `Content-Disposition: inline` (chỉ ảnh / PDF, định dạng khác ép `attachment`), `download` = `attachment`
  (tên file mã hoá RFC 5987). Header thêm: `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`.
- Xác thực: `Authorization: Bearer <access token>`; tất cả API trong tài liệu này thuộc vùng **STAFF**
  (tài khoản học sinh nhận 403 `STAFF_ONLY`), trừ mục 8 (vùng PORTAL).
- Phân trang: `PageResponse<T>` = `{ content: T[], pageNo: number, pageSize: number, totalRows: number, totalPages: number }`;
  `pageNo` từ 1. Tham số sai (`pageNo < 1`, `pageSize > 100`) -> 400 `VALIDATION_ERROR`.
- Thời gian: `LocalDateTime` dạng ISO không múi giờ (`2026-10-07T10:00:00`), giờ máy chủ (Asia/Bangkok). Ngày lọc: `yyyy-MM-dd`.
- Kích thước: luôn là **byte** (`long`).
- Validate thất bại: 400 `VALIDATION_ERROR`, `message` = thông báo của ràng buộc đầu tiên.
- Mọi thao tác ghi được ghi `SYS_AUDIT_LOGS` (mã hành động ở mục 9.4).

## 3. Quyền (menu / function)

Mã quyền dạng `MENU_CODE:FUNCTION_CODE` (`Permissions.java` / `src/app/auth/permissions.ts`).

### 3.1 Quản lý file - `MENU_FILE_MANAGER` (V19_3, Luồng B)

| Hằng số (BE và FE) | Mã | Dùng cho | ROLE_ADMIN |
|---|---|---|---|
| `FILE_MANAGER_VIEW` | `MENU_FILE_MANAGER:VIEW` | Mở `/system/files`, tìm kiếm, chi tiết, thống kê, đọc cấu hình | x |
| `FILE_MANAGER_DOWNLOAD` | `MENU_FILE_MANAGER:DOWNLOAD` | Xem / tải mọi file kể cả trong thùng rác | x |
| `FILE_MANAGER_DELETE` | `MENU_FILE_MANAGER:DELETE` | Xoá mềm (đơn / hàng loạt) | x |
| `FILE_MANAGER_RESTORE` | `MENU_FILE_MANAGER:RESTORE` | Khôi phục từ thùng rác | x |
| `FILE_MANAGER_PURGE` | `MENU_FILE_MANAGER:PURGE` | Xoá vĩnh viễn | x |
| `FILE_MANAGER_CONFIG` | `MENU_FILE_MANAGER:CONFIG` | Sửa cấu hình module, quota | x |
| `FILE_MANAGER_MAINTAIN` | `MENU_FILE_MANAGER:MAINTAIN` | Quét mồ côi, backfill checksum, xử lý kết quả quét | x |

### 3.2 Kho tài liệu - `MENU_FILE_EXPLORER` (V1 + V19_4, Luồng D)

| Hằng số | Mã | Ý nghĩa | ADMIN | TEACHER | ACCOUNTANT | ADMISSION |
|---|---|---|---|---|---|---|
| `DOC_LIBRARY_VIEW` (= `FILE_VIEW` cũ) | `MENU_FILE_EXPLORER:VIEW` | Mở kho, thấy tài liệu được chia sẻ | x | x | x | x (mới) |
| `DOC_LIBRARY_DOWNLOAD` (= `FILE_DOWNLOAD`) | `MENU_FILE_EXPLORER:DOWNLOAD` | Xem / tải tài liệu thấy được | x | x | x | x (mới) |
| `DOC_LIBRARY_UPLOAD` (= `FILE_UPLOAD`) | `MENU_FILE_EXPLORER:UPLOAD` | Tạo tài liệu; sửa / xoá / chia sẻ (lớp mình dạy) tài liệu của mình | x | x | x | |
| `DOC_LIBRARY_UPDATE` | `MENU_FILE_EXPLORER:UPDATE` | Sửa metadata / thay file mọi tài liệu thấy được | x | | | |
| `DOC_LIBRARY_DELETE` (V1 đã có) | `MENU_FILE_EXPLORER:DELETE` | Xoá mọi tài liệu thấy được | x | | | |
| `DOC_LIBRARY_SHARE` | `MENU_FILE_EXPLORER:SHARE` | Chia sẻ cho mọi đích (ALL_STAFF / ROLE / CLASS), chia sẻ thư mục | x | | | |
| `DOC_LIBRARY_MANAGE` | `MENU_FILE_EXPLORER:MANAGE` | Toàn quyền: CRUD thư mục, thấy mọi tài liệu | x | | | |

Ngoài ra V19_4 bảo đảm `DIR_DOCUMENT:VIEW` cho ADMIN / TEACHER / ACCOUNTANT / ADMISSION để thư mục menu hiện ra.

> Lưu ý: `FILE_VIEW/UPLOAD/DOWNLOAD` cũ vẫn là cổng thô (`@RequirePermission`) của `/api/v1/files/**`; quyết định
> thật nằm ở `FileAccessPolicy` + `FileModuleDescriptor` theo module (Luồng A). Sau V19_4, `ROLE_ADMISSION` có
> `FILE_VIEW`; vì vậy **chỉ chạy V19_4 cùng lần triển khai có Luồng A** (chính sách module `COMMON` / module lạ
> fail-closed), nếu không ADMISSION sẽ đọc được file `COMMON`.

## 4. API file lõi `/api/v1/files` (Luồng A)

Các endpoint đã có giữ nguyên đường dẫn và tham số; chỉ thêm trường vào phản hồi và thêm 2 endpoint mới.

### 4.1 `FileResponseDto` (mở rộng)

```json
{
  "id": 101, "originalName": "hoc-ba.pdf", "contentType": "application/pdf", "fileSize": 245760,
  "moduleName": "STUDENT", "referenceId": 12,
  "viewUrl": "/api/v1/files/view/101", "downloadUrl": "/api/v1/files/download/101",
  "createdAt": "2026-10-07T09:15:00",
  "fileExt": "pdf",
  "checksumSha256": "9f86d0...(64 hex, null = chưa tính)",
  "uploadedBy": "giaovien01",
  "canDelete": true
}
```

`canDelete`: người gọi được xoá mềm qua `DELETE /api/v1/files/{id}` (null = không tính, ví dụ khi trả từ API khác).

### 4.2 Endpoint

| Method | Đường dẫn | Cổng quyền (any of) | Request | Phản hồi |
|---|---|---|---|---|
| POST | `/api/v1/files/upload` | `FILE_UPLOAD`, `STUDENT_UPDATE`, `CLASS_UPDATE`, ... (như cũ) | multipart: `file`, `moduleName`, `referenceId` | `FileResponseDto` |
| GET | `/api/v1/files/view/{id}` | như cũ (+ `DOC_LIBRARY_VIEW` trùng `FILE_VIEW`) | | nhị phân inline |
| GET | `/api/v1/files/download/{id}` | như cũ | | nhị phân attachment |
| GET | `/api/v1/files/by-ref?moduleName=&referenceId=` | như cũ | | `FileResponseDto[]` (chỉ `STATUS=ACTIVE`) |
| DELETE | `/api/v1/files/{id}?reason=` **(mới)** | `FILE_UPLOAD` + quyền ghi của module | `reason` tối đa 500 ký tự, tuỳ chọn | `FileResponseDto` (sau xoá mềm) |
| GET | `/api/v1/files/policies?module=` **(mới)** | đã đăng nhập (STAFF) | `module` tuỳ chọn; bỏ trống = mọi module active | `FileUploadPolicyDto[]` |

Quy tắc upload (theo thứ tự, lỗi đầu tiên trả về): module hợp lệ và active (`INVALID_MODULE`,
`FILE_MODULE_DISABLED`) -> module cho phép upload chung (`LIBRARY`, `STUDENT_IMPORT` không: `INVALID_MODULE`) ->
`referenceId` bắt buộc / tồn tại / còn hiệu lực (`FILE_REFERENCE_REQUIRED|NOT_FOUND|INACTIVE`) -> quyền ghi
(`FILE_FORBIDDEN`, 403) -> rỗng (`FILE_EMPTY`) -> dung lượng file (`FILE_TOO_LARGE`) -> đuôi + magic bytes
(`FILE_TYPE_NOT_ALLOWED`, `FILE_CONTENT_MISMATCH`) -> số file / tổng dung lượng trên bản ghi (`FILE_LIMIT_COUNT`,
`FILE_LIMIT_TOTAL`) -> quota học sinh / toàn hệ thống (`FILE_QUOTA_EXCEEDED`) -> dung lượng đĩa trống
(`STORAGE_LOW_DISK`) -> quét virus (`FILE_INFECTED`).

Xem / tải: file không `ACTIVE` -> 404 `FILE_NOT_FOUND` (trừ admin qua mục 5); blob mất -> 404 `FILE_BLOB_MISSING`;
`SCAN_STATUS=PENDING|ERROR` khi bật fail-closed -> 400 `FILE_SCAN_PENDING`; không có quyền -> 403 `FILE_FORBIDDEN`.
Module `LIBRARY`: quyền đọc = tài liệu chứa file thấy được theo mục 7.

`FileUploadPolicyDto`:

```json
{ "moduleName": "STUDENT", "maxFileSizeBytes": 20971520, "maxFilesPerRef": 30, "maxTotalBytesPerRef": null,
  "allowedExtensions": ["jpg","jpeg","png","gif","webp","pdf","doc","docx","xls","xlsx","csv","txt","zip"],
  "inlineExtensions": ["jpg","jpeg","png","gif","webp","pdf"], "referenceRequired": true }
```

## 5. API quản trị file `/api/v1/admin/files` (Luồng B)

Toàn bộ dưới `MENU_FILE_MANAGER`. Cột "Quyền" là `@RequirePermission` (một quyền).

| Method | Đường dẫn | Quyền | Request | Phản hồi `data` |
|---|---|---|---|---|
| GET | `/search` | VIEW | query `FileFilterRequest` | `PageResponse<FileAdminDto>` |
| GET | `/{id}` | VIEW | | `FileDetailDto` |
| GET | `/{id}/view` | DOWNLOAD | | nhị phân inline (mọi trạng thái trừ `PURGED`) |
| GET | `/{id}/download` | DOWNLOAD | | nhị phân attachment |
| POST | `/{id}/delete` | DELETE | body `FileDeleteRequest` | `FileAdminDto` |
| POST | `/bulk-delete` | DELETE | body `FileBulkRequest` (reason bắt buộc với xoá) | `FileBulkResultDto` |
| POST | `/{id}/restore` | RESTORE | | `FileAdminDto` |
| POST | `/bulk-restore` | RESTORE | body `FileBulkRequest` | `FileBulkResultDto` |
| POST | `/{id}/purge` | PURGE | body `FilePurgeRequest` | `FileAdminDto` (`status=PURGED`) |
| GET | `/stats` | VIEW | | `FileStatsDto` |
| GET | `/storage` | VIEW | | `StorageUsageDto` |
| GET | `/settings/modules` | VIEW | | `FileModuleSettingDto[]` |
| PUT | `/settings/modules` | CONFIG | body `FileModuleSettingsUpdateRequest` | `FileModuleSettingDto[]` (toàn bộ sau lưu) |
| GET | `/settings/quotas` | VIEW | | `FileQuotaDto[]` (kèm `usedBytes`) |
| PUT | `/settings/quotas` | CONFIG | body `FileQuotasUpdateRequest` | `FileQuotaDto[]` |
| POST | `/maintenance/orphan-scan` | MAINTAIN | | `FileScanRunDto` (`status=RUNNING`, chạy nền) |
| POST | `/maintenance/checksum-backfill` | MAINTAIN | | `FileScanRunDto` (`runType=CHECKSUM`) |
| GET | `/maintenance/scans?pageNo=&pageSize=` | MAINTAIN | | `PageResponse<FileScanRunDto>` (mới nhất trước) |
| GET | `/maintenance/scans/{runId}` | MAINTAIN | | `FileScanRunDto` |
| GET | `/maintenance/scans/{runId}/items?itemType=&resolved=&pageNo=&pageSize=` | MAINTAIN | `itemType` = `MISSING_BLOB|ORPHAN_BLOB|SIZE_MISMATCH|CHECKSUM_MISMATCH|REF_DELETED|REF_NOT_FOUND`, `resolved` true/false | `PageResponse<FileScanItemDto>` |
| POST | `/maintenance/scans/{runId}/items/{itemId}/resolve` | MAINTAIN | body `FileScanResolveRequest` | `FileScanItemDto` |

Ghi chú:
- Xoá vĩnh viễn: `confirmName` phải bằng đúng `originalName` (`FILE_PURGE_CONFIRM_MISMATCH`); chỉ file `DELETED`
  (`FILE_INVALID_STATE`). Không có endpoint `DELETE` HTTP để tránh body trong `DELETE`.
- Xử lý quét: `MARK_MISSING` = chuyển dòng file sang `DELETED` với lý do "mất file"; `QUARANTINE` = chuyển blob vào
  `.quarantine/`; `DELETE_BLOB` cần **thêm** quyền `FILE_MANAGER_PURGE` (thiếu -> 403); `IGNORE` = chỉ đánh dấu.
- Hàng loạt: tối đa 100 id (`FILE_BULK_TOO_MANY`), xử lý từng file độc lập, không rollback cả lô.
- Chỉ một lần quét chạy cùng lúc (`FILE_SCAN_RUNNING`, đảm bảo bằng unique index `UQ_FILE_SCAN_RUNS_RUNNING`).
- Hành động xử lý quét hợp lệ theo loại: `MISSING_BLOB` -> `MARK_MISSING|IGNORE`; `ORPHAN_BLOB` ->
  `QUARANTINE|DELETE_BLOB|IGNORE`; `SIZE_MISMATCH`, `CHECKSUM_MISMATCH`, `REF_DELETED`, `REF_NOT_FOUND` -> `IGNORE`
  (xử lý mục đã xử lý -> `FILE_SCAN_ITEM_RESOLVED`; hành động không hợp với loại -> 400 `VALIDATION_ERROR`).
  `REF_DELETED` / `REF_NOT_FOUND` chỉ để báo cáo; xoá file đó dùng `POST /{id}/delete`.
- `PUT /settings/modules` chỉ cập nhật module có trong danh sách, không xoá module; đuôi phải thuộc `SafeFileType`, `maxFileSizeBytes`
  không vượt multipart toàn cục `spring.servlet.multipart.max-file-size` (50 MB) (`FILE_SETTING_INVALID`); thay đổi có
  hiệu lực trong <= 60 giây (cache) và ghi nhật ký giá trị cũ / mới. `PUT /settings/quotas`: upsert theo `(scopeType, scopeId)`, `deleteIds` xoá dòng;
  không xoá được `GLOBAL` (`FILE_QUOTA_INVALID`).
- `GET /storage` là endpoint bổ sung so với kế hoạch (thẻ dung lượng nhẹ, không tính thống kê).

### 5.1 `FileFilterRequest` (query của `/search`)

| Tham số | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `module` | string | | mã module |
| `referenceId` | long | | |
| `uploadedByUserId` | long | | |
| `ownerStudentId` | long | | |
| `ext` | string | | 1-10 ký tự chữ số |
| `contentType` | string | | tiền tố MIME |
| `status` | `ACTIVE\|PENDING\|DELETED\|PURGED\|ALL` | `ACTIVE` | `DELETED` = thùng rác |
| `scanStatus` | `SKIPPED\|PENDING\|CLEAN\|INFECTED\|ERROR` | | |
| `refState` | `ACTIVE\|DELETED\|NOT_FOUND\|NONE` | | trạng thái bản ghi nghiệp vụ |
| `createdFrom`, `createdTo` | `yyyy-MM-dd` | | from <= to |
| `minSize`, `maxSize` | long (byte) | | min <= max |
| `q` | string <= 100 | | tìm trong tên gốc, mã / tên học sinh, người tải |
| `pageNo`, `pageSize` | int | 1, 20 | `pageSize` <= 100 |
| `sortBy` | `createdAt\|fileSize\|originalName\|deletedAt` | `createdAt` | |
| `sortDir` | `asc\|desc` | `desc` | |

### 5.2 Hình dạng phản hồi

`FileAdminDto`:

```json
{
  "id": 101, "originalName": "hoc-ba.pdf", "fileExt": "pdf", "contentType": "application/pdf", "fileSize": 245760,
  "moduleName": "STUDENT", "referenceId": 12,
  "usage": { "moduleName": "STUDENT", "referenceId": 12, "label": "HS000012 - Nguyễn Văn A",
             "uiRoute": "/students/list?id=12", "state": "ACTIVE" },
  "status": "ACTIVE", "scanStatus": "SKIPPED",
  "uploadedByUserId": 5, "uploadedByUsername": "giaovien01", "uploadedByFullName": "Trần Thị B", "uploadedByType": "STAFF",
  "ownerStudentId": 12, "ownerStudentCode": "HS000012", "ownerStudentName": "Nguyễn Văn A",
  "checksumSha256": "9f86d0...", "storageBackend": "LOCAL",
  "createdAt": "2026-10-07T09:15:00", "deletedAt": null, "deletedBy": null, "deleteReason": null,
  "purgeAfter": null, "purgedAt": null, "daysUntilPurge": null,
  "viewUrl": "/api/v1/admin/files/101/view", "downloadUrl": "/api/v1/admin/files/101/download"
}
```

`usage.state` thuộc `FileConstants.RefState`. `FileDetailDto` = `{ file: FileAdminDto, storageKey, storedName,
blobExists, scanDetail, scannedAt, auditTrail: AuditLogResponseDto[] }` (`auditTrail` = 20 sự kiện gần nhất
`RESOURCE_TYPE='FILE'` và `RESOURCE_ID=id`).

`FileBulkResultDto` = `{ succeeded: number[], failed: [{ id, errorCode, message }] }`.

`FileStatsDto` = `{ byStatus: Bucket[], byModule: Bucket[], byMonth: Bucket[12] (key yyyy-MM), topUploaders: TopUser[],
topStudents: TopUser[], storage: StorageUsageDto, filesWithoutChecksum, filesWithDeletedReference, generatedAt }`;
`Bucket = { key, label, fileCount, totalBytes }`; `TopUser = { id, code, fullName, fileCount, totalBytes, quotaBytes }`
(top 10).

`StorageUsageDto` = `{ backend, totalBytes, usableBytes, minFreeBytes, lowDisk, usedBytes, globalQuotaBytes,
globalQuotaPercent, warnPercent }`.

`FileModuleSettingDto` (GET và PUT) = `{ moduleName, maxFileSizeBytes, maxFilesPerRef|null, maxTotalBytesPerRef|null,
allowedExtensions: string[]|null, trashRetentionDays (1-3650), countToStudentQuota, active, updatedAt, updatedBy }`.

`FileQuotaDto` = `{ id, scopeType (GLOBAL|STUDENT_DEFAULT|STUDENT|USER), scopeId|null, scopeLabel, maxBytes,
warnPercent (1-100, mặc định 80), note, usedBytes, updatedAt, updatedBy }`.

`FileScanRunDto` = `{ id, runType (ORPHAN|CHECKSUM), status (RUNNING|DONE|FAILED), startedAt, finishedAt, startedBy,
dbRowsChecked, blobsChecked, missingBlobCount, orphanBlobCount, mismatchCount, unresolvedCount, detail }`.

`FileScanItemDto` = `{ id, runId, itemType, fileId, originalName, moduleName, referenceId, storageKey, dbSize,
blobSize, blobModifiedAt, resolution (null|MARKED_MISSING|QUARANTINED|BLOB_DELETED|IGNORED), resolvedAt, resolvedBy }`.

Request body:
- `FileDeleteRequest` `{ reason: string (bắt buộc, <= 500) }`
- `FileBulkRequest` `{ ids: number[] (1-100), reason?: string (<= 500) }`
- `FilePurgeRequest` `{ reason: string, confirmName: string }`
- `FileScanResolveRequest` `{ action: "MARK_MISSING"|"QUARANTINE"|"DELETE_BLOB"|"IGNORE", note?: string }`
- `FileModuleSettingsUpdateRequest` `{ settings: FileModuleSettingDto[] (<= 50) }`
- `FileQuotasUpdateRequest` `{ quotas: FileQuotaDto[] (<= 200), deleteIds: number[] }`

## 6. API kho tài liệu `/api/v1/documents` (Luồng D)

Cổng quyền tối thiểu của mọi endpoint: `DOC_LIBRARY_VIEW`; quyền chi tiết kiểm tra trong service theo mục 7.

| Method | Đường dẫn | Cổng `@RequirePermission` | Request | Phản hồi `data` |
|---|---|---|---|---|
| GET | `/folders/tree` | VIEW | | `DocFolderTreeNodeDto[]` (các nút gốc thấy được) |
| GET | `/folders/{id}` | VIEW | | `DocFolderDto` |
| POST | `/folders` | MANAGE | body `DocFolderUpsertRequest` | `DocFolderDto` |
| PUT | `/folders/{id}` | MANAGE | body `DocFolderUpsertRequest` (đổi `parentId` = di chuyển) | `DocFolderDto` |
| DELETE | `/folders/{id}` | MANAGE | | `null` (chỉ thư mục rỗng) |
| GET | `/folders/{id}/shares` | VIEW | | `DocShareDto[]` (trực tiếp + kế thừa) |
| PUT | `/folders/{id}/shares` | SHARE, MANAGE | body `DocSharesUpdateRequest` | `DocShareDto[]` |
| GET | `/items/search` | VIEW | query `DocItemFilterRequest` | `PageResponse<DocItemDto>` |
| GET | `/items/{id}` | VIEW | | `DocItemDto` |
| POST | `/items` | UPLOAD | multipart: `file` + các trường `DocItemCreateRequest` | `DocItemDto` |
| PUT | `/items/{id}` | UPLOAD, UPDATE, MANAGE | body `DocItemUpdateRequest` | `DocItemDto` |
| POST | `/items/{id}/file` | UPLOAD, UPDATE, MANAGE | multipart: `file`, `versionNote` | `DocItemDto` (`versionNo` + 1) |
| DELETE | `/items/{id}` | UPLOAD, DELETE, MANAGE | | `null` |
| GET | `/items/{id}/shares` | VIEW | | `DocShareDto[]` |
| PUT | `/items/{id}/shares` | UPLOAD, SHARE, MANAGE | body `DocSharesUpdateRequest` | `DocShareDto[]` |
| GET | `/share-targets` | UPLOAD, SHARE, MANAGE | | `DocShareTargetsDto` |
| GET | `/tags?q=` | VIEW | `q` tiền tố, tuỳ chọn | `string[]` (tối đa 20, trong tài liệu thấy được) |

Xem / tải file của tài liệu: `item.file.viewUrl` / `item.file.downloadUrl` (`/api/v1/files/view|download/{fileId}`),
cần `DOC_LIBRARY_VIEW` / `DOC_LIBRARY_DOWNLOAD` và tài liệu thấy được.

### 6.1 Request

`DocFolderUpsertRequest` `{ parentId: number|null (null = gốc), name: string (1-200, không trùng anh em, không phân biệt hoa thường), description?: string (<= 1000), sortOrder?: number (0-9999) }`

`POST /items` (multipart, `@ModelAttribute DocItemCreateRequest` + `@RequestPart file`):

| Phần | Kiểu | Ghi chú |
|---|---|---|
| `file` | file | bắt buộc; giới hạn module `LIBRARY` (50 MB, đuôi `SafeFileType`) |
| `folderId` | number | bỏ trống = gốc (chỉ MANAGE) |
| `title` | string <= 255 | bỏ trống = tên file bỏ đuôi |
| `description` | string <= 2000 | |
| `tags` | lặp lại `tags=a&tags=b` | <= 10 thẻ, mỗi thẻ <= 50 ký tự; lưu chữ thường, bỏ trùng |
| `versionNote` | string <= 500 | |

`DocItemUpdateRequest` `{ folderId: number|null, title: string (bắt buộc), description?, tags?: string[] }`
(đổi `folderId` = di chuyển; cần quyền với thư mục đích).

`POST /items/{id}/file` multipart: `file`, `versionNote?` (`DocItemReplaceFileRequest`).

`DocItemFilterRequest` (query):

| Tham số | Mặc định | Ghi chú |
|---|---|---|
| `folderId` | | null + `includeSubfolders=false` = tài liệu ở gốc; null + `true` = toàn kho |
| `includeSubfolders` | false | |
| `q` | | <= 100, tìm trong tiêu đề, mô tả, tên file |
| `tag` | | khớp đúng một thẻ |
| `ext` | | |
| `mine` | false | chỉ tài liệu mình sở hữu |
| `classId` | | tài liệu chia sẻ (trực tiếp / kế thừa) cho lớp này |
| `pageNo`, `pageSize` | 1, 20 | `pageSize` <= 100 |
| `sortBy` | `title` | `title\|createdAt\|updatedAt\|fileSize` |
| `sortDir` | `asc` | |

`DocSharesUpdateRequest` `{ shares: DocShareRequest[] (<= 50) }` - **thay toàn bộ** chia sẻ trực tiếp của
thư mục / tài liệu; danh sách rỗng = bỏ hết chia sẻ trực tiếp (chia sẻ kế thừa không đổi).

`DocShareRequest` `{ targetType: "ALL_STAFF"|"ROLE"|"CLASS", targetId: number|null, visibleToStudents: boolean, expiresAt: string|null }`:
`ALL_STAFF` không có `targetId`; `ROLE` / `CLASS` bắt buộc; `visibleToStudents=true` chỉ với `CLASS`;
`expiresAt` phải ở tương lai. Trùng đích trong cùng danh sách -> `DOC_SHARE_INVALID`.

### 6.2 Phản hồi

`DocFolderTreeNodeDto` `{ id, parentId, name, sortOrder, navigationOnly, itemCount, children: DocFolderTreeNodeDto[] }`
- `navigationOnly=true`: người dùng không thấy chính thư mục nhưng thấy thư mục con / tài liệu bên dưới; chỉ hiện
  để điều hướng, `itemCount` chỉ đếm tài liệu thấy được.

`DocFolderDto` `{ id, parentId, name, description, sortOrder, breadcrumbs: [{id,name}], childFolderCount, itemCount,
navigationOnly, shares: DocShareDto[], permissions: DocPermissionsDto, createdAt, createdBy, updatedAt, updatedBy }`

`DocItemDto`:

```json
{
  "id": 7, "folderId": 3, "folderName": "Đề cương", "title": "Đề cương Toán 10", "description": "Học kỳ 1",
  "tags": ["toan", "lop10"], "versionNo": 2, "versionNote": "Sửa chương 3",
  "file": { "id": 220, "originalName": "de-cuong-toan-10.pdf", "fileExt": "pdf", "fileSize": 1048576,
            "viewUrl": "/api/v1/files/view/220", "downloadUrl": "/api/v1/files/download/220", "...": "FileResponseDto" },
  "ownerUserId": 5, "ownerUsername": "giaovien01", "ownerFullName": "Trần Thị B",
  "shares": [ { "id": 31, "folderId": null, "itemId": 7, "targetType": "CLASS", "targetId": 15, "targetLabel": "10A1 - Toán nâng cao",
                "visibleToStudents": true, "expiresAt": null, "inherited": false,
                "inheritedFromFolderId": null, "inheritedFromFolderName": null, "createdAt": "...", "createdBy": "giaovien01" } ],
  "visibleToStudents": true,
  "permissions": { "canEdit": true, "canDelete": true, "canShare": true, "canUpload": false },
  "createdAt": "2026-10-07T09:00:00", "createdBy": "giaovien01", "updatedAt": "2026-10-07T10:00:00", "updatedBy": "giaovien01"
}
```

- `shares` trong danh sách tìm kiếm chỉ gồm chia sẻ trực tiếp; `GET /items/{id}` và `/items/{id}/shares` gồm cả kế thừa.
- `visibleToStudents` = có ít nhất một chia sẻ `CLASS` còn hạn (trực tiếp / kế thừa) bật cờ học sinh.
- `permissions.canUpload` chỉ có nghĩa với thư mục (được tạo tài liệu trong thư mục).

`DocShareTargetsDto` `{ allStaffAllowed, roles: [{id,code,name}], classes: [{id,code,name}] }`
- Có SHARE / MANAGE: `allStaffAllowed=true`, mọi vai trò, mọi lớp đang hoạt động.
- Chỉ UPLOAD: `allStaffAllowed=false`, `roles=[]`, `classes` = lớp mình dạy.

## 7. Quy tắc hiển thị và ghi của kho tài liệu

Ký hiệu: U = người dùng; `roles(U)` = vai trò của U; `taught(U)` = lớp U là GVCN (`EDU_CLASSES.TEACHER_ID`) hoặc
dạy ít nhất một buổi chưa huỷ (`EDU_CLASS_SESSIONS`) - **luôn tính thật**, không dùng nhánh "không giới hạn" của
`TeachingAssignmentGuard.taughtClassIdsIfRestricted()`.

Một chia sẻ `s` **áp dụng** cho tài liệu I khi `s.ITEM_ID = I.ID` hoặc `s.FOLDER_ID` là thư mục chứa I hay tổ tiên
của nó (`folder(I).TREE_PATH LIKE f.TREE_PATH || '%'`, thư mục `f` chưa xoá) và (`EXPIRES_AT IS NULL OR EXPIRES_AT > SYSDATE`).
`DOC_SHARES` không xoá mềm: `PUT .../shares` xoá cứng rồi thêm lại các dòng trực tiếp.
Chia sẻ áp dụng cho thư mục F tương tự (trực tiếp hoặc tổ tiên).

**Thấy tài liệu I** (cần `DOC_LIBRARY_VIEW`): MANAGE; hoặc `I.OWNER_USER_ID = U`; hoặc có chia sẻ áp dụng với
`ALL_STAFF`, `ROLE` thuộc `roles(U)`, `CLASS` thuộc `taught(U)`. Không thấy -> 404 `DOC_ITEM_NOT_FOUND`
(không lộ sự tồn tại); thấy nhưng không đủ quyền ghi -> 403 `DOC_FORBIDDEN`.

**Thấy thư mục F**: MANAGE; hoặc F là của U (`OWNER_USER_ID`); hoặc có chia sẻ áp dụng cho F. F là tổ tiên của một
thư mục / tài liệu thấy được -> hiện với `navigationOnly=true`.

| Thao tác | Điều kiện |
|---|---|
| Tạo / sửa / di chuyển / xoá thư mục, sắp xếp | MANAGE |
| Tạo tài liệu trong thư mục F | UPLOAD và thấy F (không `navigationOnly`); ở gốc: MANAGE |
| Sửa metadata, di chuyển, thay file | MANAGE; hoặc UPDATE và thấy I; hoặc UPLOAD và là chủ sở hữu (đích di chuyển phải cho phép tạo) |
| Xoá tài liệu | MANAGE; hoặc DELETE và thấy I; hoặc UPLOAD và là chủ sở hữu |
| Chia sẻ tài liệu | MANAGE / SHARE: mọi đích; UPLOAD + chủ sở hữu: chỉ `CLASS` thuộc `taught(U)` (sai -> 403 `DOC_SHARE_CLASS_FORBIDDEN`) |
| Chia sẻ thư mục | MANAGE hoặc SHARE (và thấy thư mục) |

Khác:
- Độ sâu tối đa 8 (`DOC_FOLDER_TOO_DEEP`); di chuyển vào chính nó / con cháu -> `DOC_FOLDER_CYCLE`; khi di chuyển
  service cập nhật `TREE_PATH`/`DEPTH` của cả cây con trong cùng transaction.
- Xoá thư mục: chỉ khi không còn thư mục con / tài liệu chưa xoá (`DOC_FOLDER_NOT_EMPTY`); xoá mềm (`IS_DELETED=1`)
  và xoá cứng các dòng chia sẻ trực tiếp của nó.
- Xoá tài liệu: xoá mềm `DOC_ITEMS`, xoá cứng chia sẻ trực tiếp, file đi vào thùng rác qua `FileLifecycleService.softDelete`.
- Đích chia sẻ không tồn tại / không hoạt động -> `DOC_SHARE_TARGET_NOT_FOUND`.
- Descriptor `LibraryFileModule` (`code=LIBRARY`, `allowGenericUpload=false`): `canRead` = tài liệu có
  `FILE_ID = file.id` thấy được; `canUpload` / `canDelete` qua API chung luôn false; `describeReference` trả
  nhãn = tiêu đề tài liệu, `uiRoute = /documents/files?item={id}`, `state` theo `IS_DELETED`.

## 8. Cổng học sinh - tài liệu lớp (tương lai, Giai đoạn 3)

Chưa làm trong dự án này; schema V19_4 đã sẵn sàng (`DOC_SHARES.VISIBLE_TO_STUDENTS`, index
`IX_DOC_SHARES_CLASS(CLASS_ID, VISIBLE_TO_STUDENTS)`).

- `GET /api/v1/portal/me/documents?classId=&q=&pageNo=&pageSize=` -> `PageResponse<PortalDocumentDto>`;
  `PortalDocumentDto = { id, title, description, tags, classId, className, fileName, fileExt, fileSize, updatedAt }`.
- `GET /api/v1/portal/me/documents/{itemId}/download` -> nhị phân; không thấy -> 404 `DOC_ITEM_NOT_FOUND`.
- Học sinh S thấy tài liệu I khi có chia sẻ `CLASS` áp dụng cho I (trực tiếp / thư mục tổ tiên), còn hạn,
  `VISIBLE_TO_STUDENTS=1`, và S đang ghi danh lớp đó (`EDU_CLASS_STUDENTS.STATUS = 'ENROLLED'`); tài liệu và file
  `ACTIVE`. Không bao giờ lộ `ownerUsername`, chia sẻ, `storageKey`.
- Khi làm: thêm `@PortalAccess`, audit `FILE_DOWNLOADED` với `userType=STUDENT`; `LibraryFileModule.canRead` cho
  `UploaderType.STUDENT` theo cùng quy tắc.

## 9. Mã lỗi

### 9.1 File lõi - `FileErrorCodes` (Luồng A)

| Mã | HTTP | Khi nào |
|---|---|---|
| `FILE_NOT_FOUND` | 404 | file không tồn tại / không `ACTIVE` |
| `FILE_FORBIDDEN` | 403 | không có quyền với file / module |
| `INVALID_MODULE` | 400 | module không hợp lệ hoặc không cho upload chung |
| `FILE_MODULE_DISABLED` | 400 | module đang tắt (`ACTIVE=0`) |
| `FILE_REFERENCE_REQUIRED` | 400 | thiếu `referenceId` |
| `FILE_REFERENCE_NOT_FOUND` | 404 | bản ghi nghiệp vụ không tồn tại |
| `FILE_REFERENCE_INACTIVE` | 400 | bản ghi nghiệp vụ đã xoá / ngừng |
| `FILE_EMPTY` | 400 | file rỗng |
| `FILE_TYPE_NOT_ALLOWED` | 400 | đuôi không thuộc allow-list module |
| `FILE_CONTENT_MISMATCH` | 400 | magic bytes không khớp đuôi (đã có) |
| `FILE_TYPE_INVALID` | 400 | import học sinh không phải `.xlsx` (đã có) |
| `FILE_TOO_LARGE` | 400 / 413 | vượt giới hạn module (400) hoặc multipart (413) |
| `FILE_LIMIT_COUNT` | 400 | vượt số file / bản ghi |
| `FILE_LIMIT_TOTAL` | 400 | vượt tổng dung lượng / bản ghi |
| `FILE_QUOTA_EXCEEDED` | 400 | vượt quota học sinh / toàn hệ thống |
| `STORAGE_LOW_DISK` | 400 | đĩa còn dưới `app.storage.min-free-disk` |
| `FILE_INFECTED` | 400 | quét virus phát hiện mã độc |
| `FILE_SCAN_PENDING` | 400 | chưa quét / quét lỗi khi fail-closed |
| `FILE_BLOB_MISSING` | 404 | metadata còn nhưng blob mất |
| `FILE_STORAGE_ERROR` | 400 | lỗi ghi / đọc kho lưu trữ (mã mới gom nhóm) |
| `FILE_STORE_ERROR`, `FILE_READ_ERROR`, `FILE_PATH_ERROR`, `INVALID_PATH` | 400 | mã cũ, giữ để tương thích |

### 9.2 Quản trị file - `FileAdminErrorCodes` (Luồng B)

| Mã | HTTP | Khi nào |
|---|---|---|
| `FILE_INVALID_STATE` | 400 | thao tác không hợp với trạng thái (khôi phục file ACTIVE, purge file chưa xoá...) |
| `FILE_PURGE_CONFIRM_MISMATCH` | 400 | `confirmName` khác tên file |
| `FILE_BULK_TOO_MANY` | 400 | > 100 id |
| `FILE_SCAN_RUNNING` | 400 | đã có lần quét đang chạy |
| `FILE_SCAN_RUN_NOT_FOUND` | 404 | |
| `FILE_SCAN_ITEM_NOT_FOUND` | 404 | |
| `FILE_SCAN_ITEM_RESOLVED` | 400 | mục đã xử lý |
| `FILE_SETTING_INVALID` | 400 | cấu hình module sai (đuôi ngoài `SafeFileType`, module không tồn tại...) |
| `FILE_QUOTA_INVALID` | 400 | quota sai (xoá GLOBAL, trùng phạm vi, scopeId thiếu...) |

### 9.3 Kho tài liệu - `DocErrorCodes` (Luồng D)

| Mã | HTTP | Khi nào |
|---|---|---|
| `DOC_FOLDER_NOT_FOUND` | 404 | thư mục không tồn tại / không thấy |
| `DOC_FOLDER_NAME_DUPLICATE` | 400 | trùng tên trong cùng thư mục cha |
| `DOC_FOLDER_NOT_EMPTY` | 400 | xoá thư mục còn nội dung |
| `DOC_FOLDER_CYCLE` | 400 | di chuyển vào chính nó / con cháu |
| `DOC_FOLDER_TOO_DEEP` | 400 | vượt độ sâu 8 |
| `DOC_ITEM_NOT_FOUND` | 404 | tài liệu không tồn tại / không thấy |
| `DOC_FORBIDDEN` | 403 | thấy nhưng không đủ quyền thao tác |
| `DOC_SHARE_INVALID` | 400 | đích chia sẻ sai dạng / trùng / hết hạn trong quá khứ |
| `DOC_SHARE_TARGET_NOT_FOUND` | 404 | vai trò / lớp không tồn tại |
| `DOC_SHARE_CLASS_FORBIDDEN` | 403 | chia sẻ cho lớp mình không dạy |

Mã chung: `VALIDATION_ERROR` (400), `STAFF_ONLY` (403), `FORBIDDEN` / thiếu quyền (403), `UNAUTHORIZED` (401).

### 9.4 Hành động nhật ký (`AuditActions`)

| Luồng | Hành động |
|---|---|
| A | `FILE_UPLOADED`, `FILE_VIEWED` (chỉ khi `app.files.audit.views=true`), `FILE_DOWNLOADED` (đã có), `FILE_DELETED`, `FILE_UPLOAD_REJECTED`, `FILE_ACCESS_DENIED`, `FILE_INFECTED` |
| B | `FILE_RESTORED`, `FILE_PURGED`, `FILE_SETTINGS_CHANGED`, `FILE_QUOTA_CHANGED`, `FILE_SCAN_STARTED`, `FILE_SCAN_ITEM_RESOLVED`, `FILE_CHECKSUM_BACKFILL_STARTED`; resource `FILE_SCAN_RUN` |
| D | `DOC_FOLDER_CREATED`, `DOC_FOLDER_UPDATED`, `DOC_FOLDER_MOVED`, `DOC_FOLDER_DELETED`, `DOC_ITEM_CREATED`, `DOC_ITEM_UPDATED`, `DOC_ITEM_MOVED`, `DOC_ITEM_FILE_REPLACED`, `DOC_ITEM_DELETED`, `DOC_SHARES_CHANGED`; resource `DOC_FOLDER`, `DOC_ITEM` |

## 10. Interface Java dùng chung

| Interface / lớp | Gói | Chủ | Mô tả |
|---|---|---|---|
| `BlobStore` | `storage` | A | `put(key, InputStream, expectedSize) -> BlobInfo` (ghi `.tmp` rồi move nguyên tử, tính SHA-256), `open`, `resource`, `exists`, `size`, `delete` (idempotent), `moveToQuarantine -> key mới`, `list(prefix) -> Stream<BlobEntry>` (bỏ `.tmp`, `.quarantine`), `usage() -> StorageUsage`, `backend()` |
| `BlobInfo` | `storage` | A | `record(key, size, checksumSha256, backend)` |
| `BlobEntry` | `storage` | A | `record(key, size, Instant lastModified)` |
| `StorageUsage` | `storage` | A | `record(backend, totalBytes, usableBytes, usedByBlobs)`, `isBelow(minFree)` |
| `VirusScanner` | `storage` | A | `scan(InputStream, displayName) -> ScanResult(status, detail)`, `mode()` |
| `FileModuleDescriptor` | `common.file` | A | `code()`, `canRead`, `canUpload`, `canDelete`, `referenceRequired()`, `allowGenericUpload()`, `assertReferenceUsable`, `describeReference -> FileReferenceInfo`, `ownerStudentId` |
| `FileReferenceInfo` | `common.file` | A | `record(moduleName, referenceId, label, uiRoute, state)` |
| `FileConstants` | `common.file` | A | trạng thái, module, phạm vi quota, hằng quét, giới hạn |
| `FileLifecycleService` | `service.file` | A | `softDelete(fileId, reason)`, `restore(fileId)`, `purge(fileId, reason)` |
| `FileSettingsSource` | `service.file` | A (B cài đặt) | `moduleSetting(module)`, `moduleSettings()`, `quota(scopeType, scopeId)` |
| `DocumentConstants` | `common.document` | D | `FILE_MODULE=LIBRARY`, đích chia sẻ, độ dài, độ sâu, page size |

Mỗi module nghiệp vụ đăng ký một bean `FileModuleDescriptor`; module không có descriptor bị từ chối (fail-closed).
Descriptor `STUDENT`, `STUDENT_IMPORT`, `CLASS`, `LEAD`, `TUITION`, `COMMON` thuộc Luồng A; `LIBRARY` thuộc Luồng D;
`ASSIGNMENT`, `SUBMISSION`, `FEEDBACK` để dành cho Cổng học sinh (module đang `ACTIVE=0`).

## 11. Cấu hình `application.yml`

```yaml
app:
  storage:
    base-dir: ${APP_STORAGE_DIR:./outputs}
    # Nền tảng file V19 (docs/files-api.md). Giới hạn/quota trong DB (SYS_FILE_MODULE_SETTINGS / SYS_FILE_QUOTAS,
    # màn "Quản lý file") được ưu tiên; các giá trị dưới đây chỉ là mặc định khi DB chưa có dòng.
    backend: ${APP_STORAGE_BACKEND:local}            # local | s3 (chưa làm)
    min-free-disk: ${APP_STORAGE_MIN_FREE_DISK:5GB}  # còn trống ít hơn -> từ chối upload (STORAGE_LOW_DISK)
    defaults:
      max-file-size: 20MB
      max-files-per-ref: 30
      max-total-per-ref: 200MB
    quota:
      global: ${APP_STORAGE_QUOTA_GLOBAL:50GB}
      student-default: ${APP_STORAGE_QUOTA_STUDENT:500MB}
      warn-percent: 80
    retention:
      trash-days: ${APP_FILES_TRASH_DAYS:30}         # thùng rác -> job xoá vĩnh viễn blob (giữ dòng PURGED)
      pending-hours: 24                              # upload chưa gắn bản ghi
      orphan-grace-hours: 24                         # đối soát bỏ qua blob mới hơn
    scan:
      mode: ${APP_FILES_SCAN_MODE:none}              # none | clamav (QĐ-F2: bật trước khi học sinh nộp bài qua Internet)
      clamav:
        host: ${APP_FILES_CLAMAV_HOST:clamav}
        port: ${APP_FILES_CLAMAV_PORT:3310}
        timeout: 15s
        fail-closed: true
    jobs:
      enabled: ${APP_FILES_JOBS_ENABLED:true}
      retention-cron: "0 30 2 * * *"                 # 02:30 hằng ngày (giờ server)
  files:
    audit:
      views: ${APP_FILES_AUDIT_VIEWS:false}          # QĐ-F11: chỉ ghi tải về + quản trị + cổng học sinh
    teacher-scope-enabled: ${APP_FILES_TEACHER_SCOPE:true}  # QĐ-F12: giáo viên chỉ xem file STUDENT/CLASS lớp mình dạy
```

Bảng V19_2 (`SYS_FILE_MODULE_SETTINGS`, `SYS_FILE_QUOTAS`) ưu tiên hơn `defaults` / `quota`; yml chỉ là giá trị
dự phòng khi chưa có dòng cấu hình hoặc chưa có bean `FileSettingsSource`.

## 12. Migration V19_x

Chạy theo thứ tự, mỗi script idempotent (chạy lại an toàn), ASCII, tiếng Việt qua `UNISTR`. Người dùng tự chạy.

| Script | Chủ | Nội dung |
|---|---|---|
| `V19_1__files_metadata_v2.sql` | B | 15 cột mới `SYS_ATTACHED_FILES`, `ORIGINAL_NAME` 255 CHAR, backfill `STATUS/FILE_EXT/OWNER_STUDENT_ID`, check + FK + 5 index, `PRC_GET_FILES_BY_REF` lọc `STATUS='ACTIVE'` |
| `V19_2__files_settings_maintenance.sql` | B | `SYS_FILE_MODULE_SETTINGS`, `SYS_FILE_QUOTAS`, `SYS_FILE_SCAN_RUNS`, `SYS_FILE_SCAN_ITEMS` + sequence, seed module / quota (chỉ thêm, không ghi đè) |
| `V19_3__file_manager_menu.sql` | B | `MENU_FILE_MANAGER` dưới `DIR_SYSTEM` (`/system/files`), 7 function, chỉ `ROLE_ADMIN`; không đụng `MENU_FILE_EXPLORER` |
| `V19_4__document_library.sql` | D | `DOC_FOLDERS`, `DOC_ITEMS`, `DOC_SHARES` + sequence, dòng `LIBRARY` trong `SYS_FILE_MODULE_SETTINGS`, `MENU_FILE_EXPLORER` hiện + 7 function + cấp quyền vai trò (chỉ thêm) |

Thứ tự triển khai: V19_1 -> V19_2 -> V19_3 -> V19_4, rồi mới chạy bản build có Luồng A (FileEntity cần cột V19_1).

## 13. Hợp đồng frontend và bảng sở hữu file

### 13.1 Frontend (nhánh `feat/files-ui`)

| File | Chủ | Nội dung hợp đồng |
|---|---|---|
| `src/api/types.ts` (khối `FileItem`) | C | thêm trường tuỳ chọn `fileExt`, `checksumSha256`, `uploadedBy`, `canDelete` |
| `src/api/fileAdmin.types.ts` | C | kiểu của mục 4.2 (`FileUploadPolicy`) và mục 5 (`FileAdminItem`, `FileDetail`, `FileFilter`, ...) |
| `src/api/fileAdmin.ts` | C | hàm gọi `/api/v1/admin/files/**` và `/api/v1/files/policies`, `deleteAttachment`; Luồng 0 để stub trả Promise bị từ chối với `FileAdminNotImplementedError` |
| `src/api/documents.types.ts` | D | kiểu của mục 6 |
| `src/api/documents.ts` | D | hàm gọi `/api/v1/documents/**`; Luồng 0 để stub trả Promise bị từ chối với `DocumentsNotImplementedError` |
| `src/app/auth/permissions.ts` | khối C / khối D | `FILE_MANAGER_*` + `ROUTE_PERMISSIONS.fileManager` (C); `DOC_LIBRARY_*` + `ROUTE_PERMISSIONS.documents` (D) |
| `src/jsx/routes/fileAdmin.routes.ts` | C | route `system/files` |
| `src/jsx/routes/documents.routes.ts` | D | route `documents/files`, redirect `file-manager` -> `/documents/files` |
| `src/jsx/routes/index.ts`, `src/jsx/Index.tsx`, `src/jsx/routes/fileRoutes.test.ts` | 0 (đóng băng) | gom `fileRoutes` / `fileRedirects`, gỡ route template Layout5 (`/file-manager`, `/documents/files` cũ) và xoá `src/jsx/components/FileManager/*` |
| `src/app/components/admin/files/FileManagerAdminPage.tsx` | C | trang giữ chỗ, Luồng C thay |
| `src/app/components/documents/DocumentLibraryPage.tsx` | D | trang giữ chỗ, Luồng D thay |

### 13.2 Bảng sở hữu file theo luồng

Luồng: **A** - Nền tảng file (backend, `feat/files-core`); **B** - Quản trị file + migration V19_1..3 (backend,
`feat/files-admin`); **C** - Giao diện quản lý file + đính kèm (frontend, `feat/files-admin-ui`); **D** - Kho tài
liệu (backend `feat/files-docs` + frontend `feat/files-docs-ui`). Tất cả tách từ nhánh hợp đồng
(`feat/files` / `feat/files-ui`).

Quy tắc: mỗi file thuộc **đúng một** luồng. Các file dùng chung theo **khối** (đánh dấu "Chỉ Luồng X sửa khối này")
được chia khối, mỗi khối thuộc đúng một luồng. File "0 (đóng băng)" chỉ đổi qua mục 14. File test đi theo chủ của
lớp được test.

**Backend** (`src/main/java/com/education/base/` viết tắt là `…/`)

| File / mẫu | Luồng |
|---|---|
| `…/storage/**` (`BlobStore`, `BlobInfo`, `BlobEntry`, `StorageUsage`, `VirusScanner`, cài đặt LocalDisk / ClamAV / Noop, chiến lược khoá) | A |
| `…/common/file/FileConstants.java`, `FileErrorCodes.java`, `FileModuleDescriptor.java`, `FileReferenceInfo.java`, `SafeFileType.java` | A |
| `…/entity/FileEntity.java`, `…/repository/FileRepository.java`, `…/repository/custom/FileRepositoryCustom*.java`, `…/mapper/FileMapper.java` | A |
| `…/service/FileStorageService.java`, `…/service/impl/FileStorageServiceImpl.java` | A |
| `…/service/file/FileLifecycleService.java`, `FileSettingsSource.java`, `FileModuleRegistry*`, `FileLimitService*`, `FileLifecycleServiceImpl*`, descriptor `Student/StudentImport/Class/Lead/Tuition/CommonFileModule` | A |
| `…/security/FileAccessPolicy.java`, `…/controller/FileController.java` | A |
| `…/dto/response/FileResponseDto.java`, `FileUploadPolicyDto.java` | A |
| `…/config/FileStorageProperties.java`, `…/config/FilesProperties.java` | A |
| `src/main/resources/application.yml` - khối `app.storage.*`, `app.files.*` | A |
| `…/audit/AuditActions.java` - khối "File core - Luồng A" | A |
| `src/main/resources/db/migration/V19_1__*.sql`, `V19_2__*.sql`, `V19_3__*.sql`, `…/migration/V19FilesScriptTest.java` | B |
| `…/common/file/FileAdminErrorCodes.java` | B |
| `…/dto/request/FileFilterRequest.java`, `FileDeleteRequest.java`, `FileBulkRequest.java`, `FilePurgeRequest.java`, `FileScanResolveRequest.java`, `FileModuleSettingsUpdateRequest.java`, `FileQuotasUpdateRequest.java` | B |
| `…/dto/response/FileAdminDto.java`, `FileUsageDto.java`, `FileDetailDto.java`, `FileStatsDto.java`, `FileStatBucketDto.java`, `FileTopUserDto.java`, `StorageUsageDto.java`, `FileBulkResultDto.java`, `FileScanRunDto.java`, `FileScanItemDto.java`, `FileModuleSettingDto.java`, `FileQuotaDto.java` | B |
| `…/entity/FileModuleSettingEntity.java`, `FileQuotaEntity.java`, `FileScanRunEntity.java`, `FileScanItemEntity.java` + repository tương ứng, `…/repository/spec/FileSpecifications.java` | B |
| `…/controller/FileAdminController.java`, `…/service/FileAdminService.java`, `…/service/impl/FileAdminServiceImpl.java`, `…/service/file/DbFileSettingsSource.java`, `FileMaintenanceService*`, `FileRetentionJob.java`, `…/config/SchedulingConfig.java` | B |
| `…/security/Permissions.java` - khối "Quản lý file - Luồng B" | B |
| `…/audit/AuditActions.java` - khối "Quản lý file (admin, job) - Luồng B" + dòng `RESOURCE_FILE_SCAN_RUN` | B |
| `src/main/resources/db/migration/V19_4__document_library.sql`, `…/migration/V19DocumentLibraryScriptTest.java` | D |
| `…/common/document/DocumentConstants.java`, `DocErrorCodes.java` | D |
| `…/dto/request/Doc*.java`, `…/dto/response/Doc*.java` | D |
| `…/entity/DocFolderEntity.java`, `DocItemEntity.java`, `DocShareEntity.java` + repository, `…/controller/Document*Controller.java`, `…/service/Document*Service.java`, `…/service/impl/Document*ServiceImpl.java`, `…/service/file/LibraryFileModule.java` | D |
| `…/security/Permissions.java` - khối "Kho tài liệu - Luồng D" | D |
| `…/audit/AuditActions.java` - khối "Kho tài liệu - Luồng D" + 2 dòng `RESOURCE_DOC_*` | D |
| `docs/files-api.md` | 0 (đóng băng; mọi luồng chỉ **thêm** dòng vào mục 14) |

**Frontend** (`src/` viết tắt là `src/`)

| File / mẫu | Luồng |
|---|---|
| `src/api/files.ts`, `src/api/uploadWithProgress.ts`, `src/api/fileAdmin.ts`, `src/api/fileAdmin.types.ts` | C |
| `src/api/types.ts` - khối `FileItem` | C |
| `src/app/components/admin/files/**` (màn `/system/files`), `src/app/components/files/**` (FileUploader, AttachmentList, FilePreview dùng chung) | C |
| `src/app/components/student/StudentDetailModal.tsx` (thay phần đính kèm) | C |
| `src/jsx/routes/fileAdmin.routes.ts` | C |
| `src/app/auth/permissions.ts` - khối "Quản lý file - Luồng C" (`FILE_MANAGER_*`, `ROUTE_PERMISSIONS.fileManager`) | C |
| `src/api/errorCodes.ts` - khối `FILE_*`; `src/i18n/locales.ts` - khối `fileManager` | C |
| `src/api/documents.ts`, `src/api/documents.types.ts` | D |
| `src/app/components/documents/**` (màn `/documents/files`) | D |
| `src/jsx/routes/documents.routes.ts` | D |
| `src/app/auth/permissions.ts` - khối "Kho tài liệu - Luồng D" (`DOC_LIBRARY_*`, `ROUTE_PERMISSIONS.documents`) | D |
| `src/api/errorCodes.ts` - khối `DOC_*`; `src/i18n/locales.ts` - khối `documents` | D |
| `src/jsx/routes/index.ts`, `src/jsx/Index.tsx`, `src/jsx/routes/fileRoutes.test.ts` | 0 (đóng băng) |

Phụ thuộc và thứ tự hợp nhất: **A -> B -> D (backend)**; **C -> D (frontend)**. D frontend có thể dùng component
dùng chung của C (`src/app/components/files/**`) sau khi C hợp nhất; trước đó dùng input file đơn giản.

## 14. Yêu cầu thay đổi hợp đồng

Quy trình:
1. Luồng cần đổi **thêm một dòng** vào bảng dưới (không sửa phần khác của tài liệu), commit riêng với tiêu đề
   `contract-change: <tóm tắt>`.
2. Luồng chủ của phần bị ảnh hưởng (theo bảng 13.2) xác nhận trong cột "Duyệt"; thay đổi phá vỡ tương thích cần
   người dùng duyệt.
3. Sau khi duyệt, luồng chủ sửa code / tài liệu tương ứng; các luồng khác rebase.

Thay đổi không cần yêu cầu: thêm trường **tuỳ chọn** vào phản hồi, thêm mã lỗi mới trong file mã lỗi của chính luồng,
thêm endpoint mới trong vùng đường dẫn của chính luồng (ghi lại ở đây để các luồng khác biết).

| # | Ngày | Luồng đề xuất | Nội dung thay đổi | Ảnh hưởng | Duyệt |
|---|---|---|---|---|---|
| - | - | - | (chưa có) | - | - |
| A1 | 2026-10-07 | A | `GlobalExceptionHandler` (không thuộc luồng nào): ánh xạ `FILE_BLOB_MISSING` -> 404 như mục 9.1 (hiện trả 400 vì chỉ mã `*_NOT_FOUND` là 404). Trùng yêu cầu B6. | FE nhận 400 thay vì 404 cho tới khi sửa; `code` đúng | chờ |
| A2 | 2026-10-07 | A | Không phá vỡ: thêm `default canDownload(user, file)` và `default referenceActive(refId)` vào `FileModuleDescriptor`; thêm `FileStorageService.loadResource(FileEntity)` (đọc mọi trạng thái trừ PURGED, cho màn quản trị); `FileLimitService.evictCache()` để B gọi sau khi lưu cấu hình. | B, D dùng được; không bắt buộc | - |
| A3 | 2026-10-07 | A | Hành vi: module `COMMON` fail-closed - đọc cần `MENU_FILE_EXPLORER:UPLOAD` + VIEW/DOWNLOAD; xoá chỉ file do chính mình upload. `FileLifecycleService` không ghi audit (B4), `purge` nhận cả `PENDING` (B3), `PURGE_AFTER` theo `TRASH_RETENTION_DAYS` của module (B5). | ADMISSION không đọc COMMON | - |
