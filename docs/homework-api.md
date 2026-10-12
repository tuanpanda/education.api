# Hợp đồng API – Giai đoạn 2: Bài tập về nhà (Homework)

> Luồng 0 (hợp đồng) – nhánh backend `feat/homework`, frontend `feat/homework-ui`.
> Hai bên build song song: **Bot 1 = backend** (`feat/homework-api`), **Bot 2 = frontend** (`feat/homework-web`).
> Mọi thay đổi hợp đồng ghi vào mục cuối **"Yêu cầu thay đổi hợp đồng"** và báo bên kia trước khi code.

## 0. Quy ước chung

- Bao gói phản hồi `ApiResponse` như hiện có: `{ success, code, message, data }`. Lỗi: `success=false`, `code` = mã lỗi bảng §7.
- HTTP: `OracleBusinessException` → 400; mã kết thúc `_NOT_FOUND` → 404; `ForbiddenException` → 403; `GRADE_CONFLICT` → **409** (Bot 1 thêm ánh xạ: mã kết thúc `_CONFLICT` và `ObjectOptimisticLockingFailureException` → 409); vượt multipart → 413.
- Thời gian: chuỗi ISO `yyyy-MM-ddTHH:mm:ss`, **không múi giờ**, là giờ server **Asia/Ho_Chi_Minh** (`HomeworkConstants.ZONE`). "Bây giờ" = `LocalDateTime.now(clock.withZone(ZONE))` với bean `Clock` (test dùng `Clock.fixed`). UI không tự quyết hạn nộp; dùng các cờ `overdue/canSubmit/canEdit/willBeLate/serverNow` server trả.
- Phân trang staff 1-based: `pageNo` (≥1), `pageSize` (1..100, mặc định 20) → `PageResponse {content, pageNo, pageSize, totalRows, totalPages}`.
- HTML (mô tả bài, bài làm text, nhận xét): backend sanitize bằng sanitizer hiện dùng cho thông báo trước khi lưu.
- CSRF: mọi POST/PUT/DELETE cần header `X-XSRF-TOKEN` (client hiện có lo).

## 1. Dữ liệu (V20_1 → V20_4)

| Migration | Nội dung |
|---|---|
| `V20_1__lms_assignments.sql` | `LMS_ASSIGNMENTS` + `SEQ_LMS_ASSIGNMENTS`, index `(CLASS_ID, STATUS, DUE_AT)`, `(SESSION_ID)` |
| `V20_2__lms_submissions.sql` | `LMS_SUBMISSIONS` (UNIQUE `ASSIGNMENT_ID, STUDENT_ID`) + `LMS_SUBMISSION_HISTORY` (UNIQUE `SUBMISSION_ID, ATTEMPT_NO`) + 2 sequence |
| `V20_3__homework_menus.sql` | Menu `MENU_HOMEWORK` "Bài tập" `/academic/homework` (DIR_ACADEMIC, sort 7, icon `assignment`), chức năng VIEW/CREATE/UPDATE/DELETE/GRADE/EXPORT; ADMIN tất cả, TEACHER trừ DELETE. Bật `SYS_FILE_MODULE_SETTINGS` ASSIGNMENT/SUBMISSION/FEEDBACK (chỉ khi `IS_ACTIVE=0`) |
| `V20_4__homework_gradebook.sql` | `EDU_GRADES.ASSIGNMENT_ID` (nullable FK) + unique index hàm `UX_GRADES_ASSIGNMENT_STUDENT` |

Thứ tự chạy: V20_1 → V20_2 → V20_3 → V20_4 (người dùng tự chạy). Mỗi file có khối ROLLBACK ở đầu.
Entity: `AssignmentEntity`, `SubmissionEntity`, `SubmissionHistoryEntity` (cả hai bảng chính có `@Version VERSION`).
`GradeEntity` **chưa** map `ASSIGNMENT_ID` – Bot 1 thêm khi viết đồng bộ sổ điểm (chỉ deploy sau khi đã chạy V20_4).

### Trạng thái
- Bài: `DRAFT` → `PUBLISHED` → `CLOSED`; `CLOSED` → `PUBLISHED` (mở lại). Học sinh chỉ thấy `PUBLISHED`/`CLOSED`.
- Bài nộp: `DRAFT` → `SUBMITTED` → `GRADED`; `SUBMITTED|GRADED` → `RETURNED` (trả bài) → (HS sửa) → `SUBMITTED`; `SUBMITTED` → `DRAFT` (HS rút bài, xem §4).
- Ma trận (tính, không lưu): `NOT_SUBMITTED` (không có dòng hoặc DRAFT, chưa quá hạn), `MISSING` (như trên nhưng quá `DUE_AT`), `SUBMITTED`, `LATE` (SUBMITTED + IS_LATE=1), `RETURNED`, `GRADED`.

### Quy tắc hạn nộp (server, giờ VN)
- `now <= DUE_AT` → nộp đúng hạn, `IS_LATE=0`.
- `DUE_AT < now` và `ALLOW_LATE=1` và (`LATE_UNTIL` null hoặc `now <= LATE_UNTIL`) → cho nộp, `IS_LATE=1`.
- Còn lại, hoặc bài `CLOSED` → 400 `SUBMISSION_DEADLINE_PASSED`.
- Ngoại lệ: bài nộp đang `RETURNED` được nộp lại tới khi bài `CLOSED` (bất kể hạn); `IS_LATE` của lần nộp lại tính theo quy tắc trên.
- Validate khi lưu bài: `LATE_UNTIL` chỉ khi `allowLate=true` và phải `> DUE_AT` (`ASSIGNMENT_INVALID`); `DUE_AT` trong quá khứ chỉ cảnh báo phía UI, server cho phép.

### Tab cổng học sinh (một bài thuộc đúng 1 tab)
| tab | điều kiện |
|---|---|
| `todo` | chưa có bài nộp / DRAFT / RETURNED, **và** còn nộp được (`canSubmit=true`) |
| `overdue` | chưa có / DRAFT, **và** không còn nộp được (quá hạn hoặc CLOSED) |
| `submitted` | SUBMITTED |
| `graded` | GRADED |
Sắp xếp: todo/overdue theo `dueAt` tăng dần; submitted theo `submittedAt` giảm; graded theo `gradedAt` giảm.

## 2. Endpoint nhân viên – `/api/v1/lms/assignments` (`HomeworkController`, Bot 1)

Tất cả `@RequirePermission` theo cột Quyền **và** phạm vi giáo viên: user `ROLE_TEACHER` không phải admin chỉ thao tác lớp mình chủ nhiệm hoặc có buổi dạy (`TeachingAssignmentGuard.requireCanWrite` / `taughtClassIdsIfRestricted`) → 403 `NOT_CLASS_TEACHER`. Danh sách lọc ngầm theo lớp được phép. Bài xoá mềm/không tồn tại → 404 `ASSIGNMENT_NOT_FOUND`; bài nộp → 404 `SUBMISSION_NOT_FOUND`.

| Method & path | Quyền | Body / query | Trả về | Ghi chú |
|---|---|---|---|---|
| `GET /` | VIEW | `classId?, status?, keyword?, pageNo, pageSize` | `PageResponse<AssignmentDto>` (description=null, attachments=[], có `stats`) | sắp `dueAt` giảm |
| `GET /manageable-classes` | CREATE | – | `AnnouncementClassOptionDto[]` | giáo viên: lớp mình; khác: lớp đang hoạt động |
| `GET /{id}` | VIEW | – | `AssignmentDto` đầy đủ | |
| `POST /` | CREATE | `AssignmentUpsertRequest` | `AssignmentDto` (DRAFT) | `sessionId` phải thuộc lớp, chưa xoá, không CANCELLED → `ASSIGNMENT_SESSION_INVALID`; `maxFiles` ≤ `MAX_FILES_PER_REF` của SUBMISSION; TEXT → maxFiles bị đặt 0 |
| `PUT /{id}` | UPDATE | `AssignmentUpsertRequest` | `AssignmentDto` | Sau PUBLISHED không đổi `classId`, không đổi `submissionType` nếu đã có bài nộp → `ASSIGNMENT_INVALID_STATE`. Đổi maxScore/gradeWeight/sync → `resyncAssignment` |
| `POST /{id}/publish` | UPDATE | – | `AssignmentDto` | DRAFT/CLOSED → PUBLISHED, đặt `publishedAt` lần đầu |
| `POST /{id}/close` | UPDATE | – | `AssignmentDto` | PUBLISHED → CLOSED |
| `DELETE /{id}` | DELETE | – | `null` | xoá mềm; có bài nộp ≠ DRAFT → `ASSIGNMENT_HAS_SUBMISSIONS`; xoá mềm file ASSIGNMENT qua `FileLifecycleService` |
| `POST /{id}/attachments` | UPDATE | multipart `file` | `FileResponseDto` | module ASSIGNMENT, ref = assignmentId. Giới hạn `FileLimitService` |
| `DELETE /{id}/attachments/{fileId}` | UPDATE | – | `null` | file phải thuộc bài (ASSIGNMENT, ref=id) → else 404 `FILE_NOT_FOUND` |
| `GET /{id}/submissions` | VIEW | – | `SubmissionMatrixDto` | mọi HS `ENROLLED` (EDU_CLASS_STUDENTS) + HS đã có bài nộp dù đã rời lớp; sắp theo tên |
| `GET /submissions/{sid}` | VIEW | – | `SubmissionDetailDto` | phạm vi lớp của bài. Ghi audit xem? Không (chỉ tải file mới audit) |
| `PUT /submissions/{sid}/grade` | GRADE | `GradeSubmissionRequest {score, feedback, expectedVersion}` | `SubmissionDetailDto` | Chỉ SUBMITTED/GRADED/RETURNED (DRAFT → `SUBMISSION_INVALID_STATE`). `0 ≤ score ≤ maxScore` → else `GRADE_SCORE_OUT_OF_RANGE`. `expectedVersion ≠ version` → 409 `GRADE_CONFLICT`. Đặt GRADED, gradedBy/At, cập nhật history lần hiện tại OUTCOME=GRADED. Audit `HOMEWORK_GRADED`. Sổ điểm nếu bật |
| `POST /submissions/{sid}/return` | GRADE | `ReturnSubmissionRequest {reason, expectedVersion}` | `SubmissionDetailDto` | SUBMITTED/GRADED → RETURNED, xoá SCORE, history OUTCOME=RETURNED, `onUngraded`. Audit `HOMEWORK_RETURNED` |
| `POST /submissions/{sid}/feedback-files` | GRADE | multipart `file` | `FileResponseDto` | module FEEDBACK, ref = submissionId |
| `DELETE /submissions/{sid}/feedback-files/{fileId}` | GRADE | – | `null` | |
| `GET /{id}/export` | EXPORT | – | file `.xlsx` (`Content-Disposition: attachment; filename*=UTF-8''bai_tap_<id>_<yyyyMMdd>.xlsx`) | `HomeworkExportService`; audit `HOMEWORK_EXPORTED` |
| *(2b)* `GET /{id}/submissions.zip` | EXPORT | – | zip | ngoài phạm vi GĐ2 |

`canManage` = có UPDATE + trong phạm vi lớp; `canGrade` = có GRADE + trong phạm vi lớp.
File nhân viên xem/tải qua API file chung `/api/v1/files/{id}/view|download` (descriptor ASSIGNMENT/SUBMISSION/FEEDBACK kiểm quyền).

## 3. Endpoint cổng học sinh – `/api/v1/portal/me/assignments` (`PortalHomeworkController`, `@PortalAccess`)

Không nhận `studentId` từ client. Bài phải `PUBLISHED|CLOSED`, chưa xoá, thuộc lớp HS đang `ENROLLED` (`StudentScopeGuard.requireEnrolled(classId)`); sai → **404 `ASSIGNMENT_NOT_FOUND`** (không 403). Tài khoản nhân viên gọi → 403 `STUDENT_ONLY` (cơ chế sẵn có).

| Method & path | Body | Trả về | Quy tắc |
|---|---|---|---|
| `GET ?tab=todo\|submitted\|graded\|overdue` | – | `PortalAssignmentListItemDto[]` | thiếu tab = todo; tab sai → 400 `VALIDATION_ERROR` |
| `GET /summary` | – | `PortalHomeworkSummaryDto` | huy hiệu trang chủ; `dueSoonCount` = todo có `dueAt` ≤ now + 3 ngày |
| `GET /{id}` | – | `PortalAssignmentDetailDto` | `mySubmission` null nếu chưa có; `score` chỉ khi GRADED; `feedback` khi GRADED/RETURNED |
| `PUT /{id}/submission` | `PortalSubmissionDraftRequest {contentText}` | `PortalSubmissionDto` | tạo dòng DRAFT nếu chưa có. Chỉ khi DRAFT/RETURNED và `canEdit` → else `SUBMISSION_LOCKED` / `SUBMISSION_DEADLINE_PASSED`. Bài FILE gửi text khác rỗng → `SUBMISSION_TYPE_MISMATCH` |
| `POST /{id}/submission/files` | multipart `file` | `PortalFileDto` | tạo dòng DRAFT nếu chưa có; module SUBMISSION ref = submissionId; bài TEXT → `SUBMISSION_TYPE_MISMATCH`; số file ≥ `MAX_FILES` bài → `SUBMISSION_TOO_MANY_FILES`; còn lại `FileLimitService` (FILE_TOO_LARGE, FILE_TYPE_NOT_ALLOWED, FILE_LIMIT_TOTAL, FILE_QUOTA_EXCEEDED, FILE_CONTENT_MISMATCH…) |
| `DELETE /{id}/submission/files/{fileId}` | – | `null` | chỉ file của bài nộp mình, khi `canEdit`; sai → 404 `PORTAL_FILE_NOT_FOUND` |
| `POST /{id}/submit` | – | `PortalSubmissionDto` | DRAFT/RETURNED → SUBMITTED. Rỗng → `SUBMISSION_EMPTY` (TEXT cần text, FILE cần ≥1 file, BOTH cần một trong hai). `ATTEMPT_COUNT+1`, `FIRST_SUBMITTED_AT` lần đầu, `SUBMITTED_AT`, `IS_LATE`, thêm `LMS_SUBMISSION_HISTORY` (snapshot text + CSV file id). Audit `HOMEWORK_SUBMITTED` |
| `POST /{id}/unsubmit` | – | `PortalSubmissionDto` | SUBMITTED → DRAFT nếu `ALLOW_RESUBMIT=1`, chưa GRADED, còn hạn (đúng hạn hoặc trong hạn muộn); else `SUBMISSION_LOCKED` / `SUBMISSION_DEADLINE_PASSED` |
| `GET /api/v1/portal/me/files/{fileId}?download=false` | – | nội dung file | `PortalFileController` + `PortalFileService`. Cho phép: SUBMISSION/FEEDBACK của bài nộp **của chính HS**; ASSIGNMENT của bài nhìn thấy. Khác → 404 `PORTAL_FILE_NOT_FOUND`. Inline cho ảnh/PDF, `attachment` cho loại khác hoặc `download=true`; header `X-Content-Type-Options: nosniff`, CSP như API file; file `SCAN_STATUS` PENDING/INFECTED → `FILE_SCAN_PENDING`/`FILE_INFECTED`. Audit `FILE_DOWNLOADED` |

Cờ trong DTO (server tính): `canEdit` = (không có bài nộp | DRAFT | RETURNED) && còn nộp được; `canSubmit` = `canEdit`; `willBeLate` = now > dueAt; `overdue` = now > dueAt.
Gợi ý cache: không cache; UI gọi lại sau mỗi thao tác ghi.

## 4. Luật file theo module

Cấu hình (`SYS_FILE_MODULE_SETTINGS`, V20_3 bật; admin sửa được ở `/system/files` tab Cấu hình):

| Module | Ref | Max/file | Số file / ref | Tổng / ref | Đuôi cho phép | Quota HS |
|---|---|---|---|---|---|---|
| ASSIGNMENT | `LMS_ASSIGNMENTS.ID` | 20 MB | 10 | 100 MB | pdf,jpg,jpeg,png,webp,doc,docx,xls,xlsx,txt | không |
| SUBMISSION | `LMS_SUBMISSIONS.ID` | 10 MB | 5 (và ≤ `MAX_FILES` của bài) | 25 MB | pdf,jpg,jpeg,png,webp,docx,xlsx,txt — **không zip, không HEIC** | có (500 MB/HS) |
| FEEDBACK | `LMS_SUBMISSIONS.ID` | 10 MB | 5 | 25 MB | pdf,jpg,jpeg,png,webp,docx,xlsx,txt | không |

Descriptor (`service/file/*FileModule.java` – hiện là **stub từ chối mọi thứ, chưa `@Component`**; Bot 1 viết luật + thêm `@Component` + sửa 3 test đang giả định fail-closed):
- **Assignment**: đọc = `HOMEWORK_VIEW` + `TeacherFileScope.classAllowed(classId của bài)`; upload/xoá = `HOMEWORK_UPDATE|CREATE` + phạm vi; `allowGenericUpload=false` (chỉ qua `/lms/assignments/{id}/attachments`); `assertReferenceUsable` = bài tồn tại, chưa xoá; `describeReference` → label "Bài tập: <tiêu đề> (<mã lớp>)", route `/academic/homework?id=<id>`.
- **Submission**: đọc = `HOMEWORK_VIEW` + phạm vi lớp của bài; upload qua generic = không; xoá qua `/api/v1/files` = không; `ownerStudentId` = `STUDENT_ID` (tính quota); route `/academic/homework?id=<asgId>&submission=<sid>`.
- **Feedback**: đọc = `HOMEWORK_VIEW` + phạm vi; upload/xoá chỉ qua API chấm (GRADE).
- Học sinh **không bao giờ** đi qua `/api/v1/files/**`; chỉ `/api/v1/portal/me/files/{id}`.
- HEIC/ảnh lớn: frontend nén sang JPEG (≤ 2560 px cạnh dài, chất lượng 0.85, mục tiêu < 3 MB) trong `beforeUpload` của `FileUploader`; server vẫn từ chối HEIC (`FILE_TYPE_NOT_ALLOWED`).

## 5. Đồng bộ sổ điểm (V20_4, `HomeworkGradebookSync`)

- Chỉ khi `syncToGradebook=true`. Upsert `EDU_GRADES` theo `(ASSIGNMENT_ID, STUDENT_ID)` chưa xoá: `GRADE_TYPE='ASSIGNMENT'` (CHECK hiện có không có `HOMEWORK`), `SCORE = round(score / maxScore × 10, 2)` (CHECK 0..10), `WEIGHT = gradeWeight`, `EXAM_DATE = DUE_AT::date`, `NOTE = "BT: " + title` (≤255).
- Trả bài / xoá bài / tắt cờ → xoá mềm dòng đã đẩy. Đổi maxScore/weight → đồng bộ lại.
- Lỗi đồng bộ không làm hỏng việc chấm: log + audit `HOMEWORK_GRADEBOOK_SYNC_FAILED`.
- Màn Điểm hiện có hiển thị các dòng này như điểm ASSIGNMENT bình thường; Bot 1 chặn sửa/xoá tay qua API điểm các dòng có `ASSIGNMENT_ID` → 400 `GRADE_SYNCED_READONLY` ("Điểm này lấy từ bài tập, hãy sửa ở màn Bài tập.").

## 6. DTO (Java ↔ TypeScript)

Java ở `dto/request|response`, TS ở `education.ui src/api/homework.types.ts` (staff) và `src/portal/api/homework.ts` (portal). Hai phía phải khớp tên trường 1-1.
- Staff: `AssignmentUpsertRequest`, `AssignmentFilterRequest`, `GradeSubmissionRequest`, `ReturnSubmissionRequest`, `AssignmentDto`, `AssignmentStatsDto`, `SubmissionMatrixDto`, `SubmissionRowDto`, `SubmissionDetailDto`, `SubmissionHistoryDto`, `FileResponseDto` (sẵn có).
- Portal (tách riêng, không lộ `gradedBy`, `version`, `createdBy`): `PortalSubmissionDraftRequest`, `PortalAssignmentListItemDto`, `PortalAssignmentDetailDto`, `PortalSubmissionDto`, `PortalFileDto`, `PortalHomeworkSummaryDto`, `SubmissionHistoryDto`.
- Boolean trong DTO là `Boolean` (JSON `true/false`); số tiền/điểm `BigDecimal` → JSON number.

## 7. Mã lỗi

| Mã | HTTP | Khi nào | Thông báo gợi ý |
|---|---|---|---|
| `ASSIGNMENT_NOT_FOUND` | 404 | không tồn tại / xoá / HS ngoài phạm vi / DRAFT với HS | Không tìm thấy bài tập. |
| `SUBMISSION_NOT_FOUND` | 404 | bài nộp không tồn tại / ngoài phạm vi | Không tìm thấy bài nộp. |
| `PORTAL_FILE_NOT_FOUND` | 404 | file cổng không thuộc HS | Không tìm thấy file. |
| `ASSIGNMENT_INVALID_STATE` | 400 | chuyển trạng thái sai, sửa lớp sau khi đăng | Không thể thực hiện với trạng thái hiện tại của bài tập. |
| `ASSIGNMENT_INVALID` | 400 | lateUntil ≤ dueAt, maxFiles vượt module | Thông tin bài tập không hợp lệ: … |
| `ASSIGNMENT_SESSION_INVALID` | 400 | buổi không thuộc lớp / huỷ | Buổi học không thuộc lớp đã chọn. |
| `ASSIGNMENT_HAS_SUBMISSIONS` | 400 | xoá bài đã có bài nộp | Bài đã có học sinh nộp, hãy đóng bài thay vì xoá. |
| `SUBMISSION_DEADLINE_PASSED` | 400 | quá hạn / CLOSED | Đã hết hạn nộp bài. |
| `SUBMISSION_LOCKED` | 400 | sửa khi SUBMITTED/GRADED, rút bài khi không cho nộp lại | Bài đã nộp/đã chấm, không sửa được. |
| `SUBMISSION_EMPTY` | 400 | nộp rỗng | Em chưa làm bài hoặc chưa đính kèm file. |
| `SUBMISSION_TYPE_MISMATCH` | 400 | text cho bài FILE, file cho bài TEXT | Bài này không nhận kiểu nộp này. |
| `SUBMISSION_TOO_MANY_FILES` | 400 | vượt MAX_FILES của bài | Bài này chỉ cho tối đa N file. |
| `SUBMISSION_INVALID_STATE` | 400 | chấm/trả bài DRAFT | Học sinh chưa nộp bài. |
| `GRADE_SCORE_OUT_OF_RANGE` | 400 | điểm < 0 / > maxScore | Điểm phải từ 0 đến {maxScore}. |
| `GRADE_CONFLICT` | 409 | expectedVersion lệch | Bài vừa được người khác chấm/cập nhật. Tải lại để xem. |
| `NOT_CLASS_TEACHER` | 403 | GV ngoài lớp | Bạn không phụ trách lớp này. |
| `GRADE_SYNCED_READONLY` | 400 | sửa/xoá tay điểm đồng bộ từ bài tập | Điểm này lấy từ bài tập, hãy sửa ở màn Bài tập. |
| `GRADEBOOK_SYNC_FAILED` | (chỉ audit/log) | đồng bộ sổ điểm lỗi | – |
| `FILE_*` | theo `docs/files-api.md` | lỗi file | thông báo tiếng Việt kèm giới hạn (sẵn có) |
| `VALIDATION_ERROR` | 400 | Bean Validation | sẵn có |

## 8. Phân công (ownership)

| Hạng mục | Bot 1 – backend (`feat/homework-api`, `E:\JAVA\edu-wt\hw-api`) | Bot 2 – frontend (`feat/homework-web`, `E:\JAVA\edu-wt\hw-web`) |
|---|---|---|
| Migration V20_1..V20_4 | chỉ sửa khi có yêu cầu §9 (không chạy DB) | – |
| Entity / repository / DTO / hằng số / mã lỗi / interface (Luồng 0) | thêm được; đổi/bỏ phải qua §9 | – |
| `HomeworkServiceImpl`, `PortalHomeworkServiceImpl`, `PortalFileServiceImpl`, `HomeworkExportServiceImpl`, `HomeworkGradebookSyncImpl` | ✅ | – |
| `HomeworkController` (`/api/v1/lms/assignments`), `PortalHomeworkController`, `PortalFileController` | ✅ | – |
| `AssignmentFileModule`, `SubmissionFileModule`, `FeedbackFileModule` (luật + `@Component`) + sửa test fail-closed liên quan | ✅ | – |
| `GlobalExceptionHandler` (409), `GradeEntity.assignmentId`, chặn sửa tay điểm đồng bộ | ✅ | – |
| Test backend (service, controller MockMvc, IDOR, hạn nộp với Clock.fixed, optimistic lock) | ✅ | – |
| `docs/homework-api.md` | cập nhật khi đổi hợp đồng (§9) | đọc; ghi yêu cầu vào §9 |
| TS types/API (`src/api/homework*.ts`, `src/portal/api/homework.ts`) | – | ✅ (giữ khớp DTO) |
| Màn staff `/academic/homework` (`src/app/components/academic/homework/**`), route `src/jsx/routes/homework.routes.ts` | – | ✅ |
| Màn portal `/portal/homework/**` (`src/portal/homework/**`), badge trang chủ, `PortalApp.tsx` route con | – | ✅ |
| i18n vi/en (`src/i18n/homework.ts`), test Vitest, mock | – | ✅ |
| `permissions.ts` (HOMEWORK_*), `menuPaths`/`menuIcons` | – | thêm được, không đổi khoá đã có |

Ghép: Luồng 0 (người điều phối) merge `feat/homework-api` → `feat/homework` và `feat/homework-web` → `feat/homework-ui`, chạy toàn bộ test, rồi chờ người dùng duyệt push/merge.

## 9. Yêu cầu thay đổi hợp đồng

Ghi theo mẫu, thêm dòng mới ở cuối, không xoá dòng cũ:

| # | Ngày | Bên yêu cầu | Nội dung | Ảnh hưởng | Trạng thái |
|---|---|---|---|---|---|
| 0 | 12/10/2026 | Luồng 0 | Bản hợp đồng đầu tiên | – | chốt |
