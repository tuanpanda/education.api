# Hợp đồng API – Giai đoạn 3: Học liệu (khoá học, bài học, tài liệu, video, tiến độ)

> Luồng 0 (hợp đồng) – nhánh backend `feat/portal-p3`, frontend `feat/portal-p3-ui`.
> Hai bên build song song: **Bot 1 = backend** (`feat/portal-p3-api`), **Bot 2 = frontend** (`feat/portal-p3-web`).
> Mọi thay đổi hợp đồng ghi vào **§9** và báo bên kia trước khi code. Nền: GĐ2 đã deploy (`feat/education-api` @ `0d831c3`, `main` @ `6c4d5d3`), migration tới V20_4.

## 0. Quy ước chung (giữ nguyên GĐ2)

- `ApiResponse { success, code, message, data }`. `OracleBusinessException` → 400; mã kết thúc `_NOT_FOUND` → 404; `ForbiddenException` → 403; vượt multipart → 413. **GĐ3 không thêm mã 409** (quyết định ghép GĐ2: chỉ `GRADE_CONFLICT` là 409). `@Version` trên khoá/chương/bài chỉ để chống ghi đè ngầm; `ObjectOptimisticLockingFailureException` giữ ánh xạ hiện có.
- Thời gian: chuỗi ISO `yyyy-MM-ddTHH:mm:ss` không múi giờ = giờ server **Asia/Ho_Chi_Minh** (`LessonConstants.ZONE`). "Bây giờ" = `LocalDateTime.now(clock.withZone(ZONE))` với bean `Clock`. UI không tự quyết bài đã mở hay chưa; dùng `availableNow` / danh sách server trả.
- Phân trang staff 1-based: `pageNo`, `pageSize` (1..100, mặc định 20) → `PageResponse`.
- HTML (mô tả khoá, nội dung bài): sanitize bằng sanitizer hiện dùng cho thông báo/bài tập, trước khi lưu.
- CSRF như hiện có. Rate limit cổng dùng chung `/api/v1/portal/**`.

## 1. Dữ liệu (V21_1 → V21_4)

| Migration | Nội dung |
|---|---|
| `V21_1__lms_courses.sql` | `LMS_COURSES` + `SEQ_LMS_COURSES`; unique hàm `UX_COURSES_CODE` (UPPER(code) khi chưa xoá); `EDU_CLASSES.COURSE_ID` (nullable, `FK_CLASSES_COURSE`, `IX_CLASSES_COURSE`) |
| `V21_2__lms_lessons.sql` | `LMS_LESSON_SECTIONS`, `LMS_LESSONS`, `LMS_LESSON_RESOURCES` + 3 sequence. Chủ = đúng một trong `COURSE_ID`/`CLASS_ID` (`CK_LSEC_OWNER`, `CK_LES_OWNER`); `SESSION_ID` chỉ khi bài thuộc lớp (`CK_LES_SESSION`); hình dạng tài nguyên theo loại (`CK_LRES_SHAPE`) |
| `V21_3__lms_lesson_progress.sql` | `LMS_LESSON_PROGRESS` (ID + `SEQ_`, UNIQUE `(LESSON_ID, STUDENT_ID)`), trạng thái `IN_PROGRESS`/`COMPLETED`; không có dòng = `NOT_STARTED` |
| `V21_4__lesson_menus.sql` | Menu `MENU_COURSE` "Khóa học" `/academic/courses` (sort 8, icon `book`) và `MENU_LESSON` "Học liệu" `/academic/lessons` (sort 9, icon `book-open`), chức năng VIEW/CREATE/UPDATE/DELETE. ADMIN tất cả; TEACHER: COURSE chỉ VIEW, LESSON VIEW/CREATE/UPDATE. Thêm `SYS_FILE_MODULE_SETTINGS` `LESSON` (chỉ khi chưa có) |

Thứ tự chạy: V21_1 → V21_2 → V21_3 → V21_4 (người dùng tự chạy). Mỗi file có khối ROLLBACK ở đầu.
Entity: `CourseEntity`, `LessonSectionEntity`, `LessonEntity`, `LessonResourceEntity`, `LessonProgressEntity`.
`ClassEntity` **chưa** map `COURSE_ID` – Bot 1 thêm (`courseId`) khi viết liên kết khoá–lớp; chỉ deploy sau khi đã chạy V21_1.

### Mô hình
- **Khoá mẫu** (`LMS_COURSES`) có chương/bài riêng (chủ = COURSE). **Lớp** gắn khoá bằng `EDU_CLASSES.COURSE_ID` = **liên kết sống**: sửa bài khoá thì mọi lớp gắn thấy ngay. Lớp còn có chương/bài **riêng** (chủ = CLASS).
- **Chép sang lớp** (`copy-to-class`) tạo bản riêng của lớp (`SOURCE_SECTION_ID`/`SOURCE_LESSON_ID` trỏ bản gốc), bài chép về `DRAFT`, tài nguyên FILE dùng chung `FILE_ID` (không nhân bản blob), VIDEO/LINK chép nguyên, ASSIGNMENT không có ở khoá nên không chép. Không đổi `COURSE_ID` của lớp.
- Cây học sinh thấy ở một lớp = chương khoá gắn (trước) + chương riêng của lớp (sau), mỗi nhóm theo `SORT_ORDER, ID`.
- Bài: `DRAFT` ⇄ `PUBLISHED`. Khoá: `ACTIVE` / `ARCHIVED` (ARCHIVED: không gắn thêm lớp; lớp đang gắn vẫn thấy bài).

### Giờ mở bài (server, giờ VN)
`effectiveAvailableFrom` = `AVAILABLE_FROM` nếu có; else nếu có `SESSION_ID` (buổi chưa CANCELLED) = `SESSION_DATE + START_TIME` (START_TIME null → 00:00); else `PUBLISHED_AT`.
`availableNow` = `STATUS='PUBLISHED'` và `IS_DELETED=0` và `effectiveAvailableFrom <= now`. Buổi bị huỷ sau khi gắn → coi như không có buổi (mở theo `PUBLISHED_AT`), trả `sessionId` như cũ để màn nhân viên cảnh báo.

### Tiến độ
- `POST /portal/me/lessons/{id}/progress {status}`: chưa có dòng → tạo (`FIRST_VIEWED_AT = LAST_VIEWED_AT = now`, `VIEW_COUNT=1`, `COMPLETED_AT` = now nếu COMPLETED). Có dòng → `LAST_VIEWED_AT = now`; `IN_PROGRESS` tăng `VIEW_COUNT`; `COMPLETED` đặt trạng thái + `COMPLETED_AT` nếu chưa; **COMPLETED không lùi** về IN_PROGRESS (gửi IN_PROGRESS chỉ tăng lượt xem).
- `%` = round(100 × số bài `availableNow` đã COMPLETED / số bài `availableNow`) của lớp ngữ cảnh (0 nếu mẫu số 0). Bài gỡ khỏi cổng (unpublish/xoá/gỡ khoá) không tính nhưng dòng tiến độ giữ.

## 2. Endpoint nhân viên (Bot 1)

### 2.1 Khoá học – `/api/v1/lms/courses` (`CourseController`)
| Method & path | Quyền | Body / query | Trả về | Ghi chú |
|---|---|---|---|---|
| `GET /` | COURSE_VIEW | `keyword?, status?, pageNo, pageSize` | `PageResponse<CourseDto>` (description=null) | sắp `courseName` |
| `GET /{id}` | COURSE_VIEW | – | `CourseDto` | |
| `POST /` | COURSE_CREATE | `CourseUpsertRequest` | `CourseDto` | mã trùng → `COURSE_CODE_DUPLICATE` |
| `PUT /{id}` | COURSE_UPDATE | `CourseUpsertRequest` | `CourseDto` | |
| `DELETE /{id}` | COURSE_DELETE | – | `null` | còn lớp gắn → `COURSE_IN_USE`; xoá mềm chương/bài/tài nguyên của khoá |
| `GET /{id}/classes` | COURSE_VIEW | – | `CourseClassDto[]` | |
| `PUT /{id}/classes/{classId}` | COURSE_UPDATE | – | `CourseClassDto` | ARCHIVED / lớp đã gắn khoá khác → `COURSE_INVALID_STATE`; lớp không tồn tại/đã xoá → 404 theo cách `ClassServiceImpl` đang báo (Bot 1 dùng lại, không thêm mã mới) |
| `DELETE /{id}/classes/{classId}` | COURSE_UPDATE | – | `null` | lớp không gắn khoá này → `COURSE_INVALID_STATE` |
| `POST /{id}/copy-to-class/{classId}` | LESSON_CREATE | – | `CourseCopyResultDto` | phạm vi giáo viên với `classId` |

Cây chương/bài của khoá: dùng `GET /api/v1/lms/lessons/tree?ownerType=COURSE&ownerId=`.

### 2.2 Chương / bài / tài nguyên – `LessonController`
Quyền theo **chủ** (service kiểm tra, controller gắn `@RequirePermission` rộng = một trong các mã cột "Quyền"):
- chủ CLASS: `LESSON_*` tương ứng + `TeachingAssignmentGuard` (giáo viên ngoài lớp → 403 `NOT_CLASS_TEACHER`);
- chủ COURSE: ghi = `COURSE_UPDATE`; đọc = `COURSE_VIEW` hoặc `LESSON_VIEW`.
Xoá (bài/chương/tài nguyên) chủ CLASS cần `LESSON_DELETE`; chủ COURSE cần `COURSE_UPDATE`. Không tồn tại / đã xoá → 404 mã `*_NOT_FOUND` tương ứng.

| Method & path | Quyền (controller) | Body / query | Trả về | Ghi chú |
|---|---|---|---|---|
| `GET /api/v1/lms/lessons/tree` | LESSON_VIEW \| COURSE_VIEW | `ownerType, ownerId` | `LessonTreeDto` | CLASS kèm `inheritedSections` (khoá gắn, chỉ đọc) |
| `GET /api/v1/lms/lessons/manageable-classes` | LESSON_VIEW | – | `AnnouncementClassOptionDto[]` | giáo viên: lớp mình |
| `POST /api/v1/lms/lesson-sections` | LESSON_CREATE \| COURSE_UPDATE | `LessonSectionUpsertRequest` | `LessonSectionDto` | thêm cuối; quá `MAX_SECTIONS_PER_OWNER` → `LESSON_INVALID` |
| `PUT /api/v1/lms/lesson-sections/{id}` | LESSON_UPDATE \| COURSE_UPDATE | `{title}` | `LessonSectionDto` | |
| `DELETE /api/v1/lms/lesson-sections/{id}` | LESSON_DELETE \| COURSE_UPDATE | – | `null` | còn bài → `LESSON_SECTION_NOT_EMPTY` |
| `GET /api/v1/lms/lessons/{id}` | LESSON_VIEW \| COURSE_VIEW | – | `LessonDto` | |
| `POST /api/v1/lms/lessons` | LESSON_CREATE \| COURSE_UPDATE | `LessonUpsertRequest` | `LessonDto` (DRAFT) | `sessionId` sai lớp / bài khoá / buổi CANCELLED → `LESSON_SESSION_INVALID` |
| `PUT /api/v1/lms/lessons/{id}` | LESSON_UPDATE \| COURSE_UPDATE | `LessonUpsertRequest` | `LessonDto` | `sectionId` khác chủ → `LESSON_INVALID` |
| `DELETE /api/v1/lms/lessons/{id}` | LESSON_DELETE \| COURSE_UPDATE | – | `null` | xoá mềm bài + tài nguyên |
| `POST /api/v1/lms/lessons/{id}/publish` | LESSON_UPDATE \| COURSE_UPDATE | – | `LessonDto` | đã PUBLISHED → `LESSON_INVALID_STATE` |
| `POST /api/v1/lms/lessons/{id}/unpublish` | LESSON_UPDATE \| COURSE_UPDATE | – | `LessonDto` | |
| `PUT /api/v1/lms/lessons/reorder` | LESSON_UPDATE \| COURSE_UPDATE | `LessonReorderRequest` | `LessonTreeDto` | phải đủ mọi chương + bài của chủ, bài chuyển chương được (cùng chủ) → else `LESSON_REORDER_INVALID` |
| `POST /api/v1/lms/lessons/{id}/resources` | LESSON_UPDATE \| COURSE_UPDATE | `LessonResourceRequest` (VIDEO/LINK/ASSIGNMENT) | `LessonResourceDto` | §5; ASSIGNMENT: bài tập cùng lớp, chưa xoá, bài học thuộc lớp → else `LESSON_ASSIGNMENT_INVALID`; quá `MAX_RESOURCES_PER_LESSON` → `LESSON_RESOURCE_INVALID` |
| `POST /api/v1/lms/lessons/{id}/resources/file` | LESSON_UPDATE \| COURSE_UPDATE | multipart `file`, `title?` | `LessonResourceDto` | module LESSON, ref = lessonId; giới hạn `FileLimitService` |
| `PUT /api/v1/lms/lesson-resources/{rid}` | LESSON_UPDATE \| COURSE_UPDATE | `LessonResourceRequest` | `LessonResourceDto` | không đổi `resourceType`; FILE chỉ đổi title |
| `DELETE /api/v1/lms/lesson-resources/{rid}` | LESSON_UPDATE \| COURSE_UPDATE | – | `null` | FILE: xoá mềm file qua `FileLifecycleService` chỉ khi không còn tài nguyên khác dùng `FILE_ID` |
| `PUT /api/v1/lms/lessons/{id}/resources/reorder` | LESSON_UPDATE \| COURSE_UPDATE | `LessonResourceReorderRequest` | `LessonResourceDto[]` | đủ mọi tài nguyên → else `LESSON_REORDER_INVALID` |
| `GET /api/v1/lms/classes/{classId}/lesson-progress` | LESSON_VIEW | – | `ClassLessonProgressDto` | HS ENROLLED × bài `availableNow` của lớp; phạm vi giáo viên |

## 3. Endpoint cổng học sinh – `@PortalAccess` (Bot 1, `PortalLessonController`)

| Method & path | Body / query | Trả về | Ghi chú |
|---|---|---|---|
| `GET /api/v1/portal/me/lessons` | – | `PortalLessonClassDto[]` | mọi lớp ENROLLED (kể cả 0 bài) |
| `GET /api/v1/portal/me/classes/{classId}/lessons` | – | `PortalLessonTreeDto` | `requireEnrolled(classId)` → lớp ngoài phạm vi 404 (mã sẵn có của guard) |
| `GET /api/v1/portal/me/lessons/{id}` | `classId?` | `PortalLessonDetailDto` | không thấy / chưa mở / DRAFT → 404 `LESSON_NOT_FOUND`. `prev/nextLessonId` theo thứ tự cây của lớp ngữ cảnh |
| `POST /api/v1/portal/me/lessons/{id}/progress` | `classId?`, body `PortalLessonProgressRequest` | `PortalLessonProgressDto` | cùng điều kiện thấy bài |
| `GET /api/v1/portal/me/files/{fileId}` | `download?` | file | **mở rộng `PortalFileService`**: thêm LESSON – cho phép nếu có tài nguyên FILE chưa xoá dùng `fileId` thuộc bài HS đang thấy. Còn lại giữ luật GĐ2. Audit `FILE_DOWNLOADED` |

Tài nguyên ASSIGNMENT trên cổng chỉ hiện khi bài tập `PUBLISHED`/`CLOSED`; UI link sang `/portal/homework/{assignmentId}`.

## 4. Luật file – module `LESSON`

| Module | Ref | Max/file | Số file / ref | Tổng / ref | Đuôi cho phép | Quota HS |
|---|---|---|---|---|---|---|
| LESSON | `LMS_LESSONS.ID` (bài tải lên) | 50 MB | 20 | 300 MB | pdf,jpg,jpeg,png,webp,doc,docx,xls,xlsx,pptx,txt | không |

- `SafeFileType` hiện **chưa có `pptx`** → Bot 1 thêm `PPTX` (`application/vnd.openxmlformats-officedocument.presentationml.presentation`, magic ZIP như docx, không inline). Không thêm `ppt` cũ.
- Descriptor `service/file/LessonFileModule.java` – **stub từ chối mọi thứ, chưa `@Component`** (V21_4 bật module nhưng registry fail-closed tới khi Bot 1 viết luật):
  - đọc: `LESSON_VIEW` hoặc `COURSE_VIEW`; nếu mọi bài dùng file đều thuộc lớp thì cần `TeacherFileScope.classAllowed` ở ít nhất một lớp;
  - upload/xoá qua `/api/v1/files`: không (`allowGenericUpload=false`, `canUpload`/`canDelete` = false) – chỉ qua API §2.2;
  - `assertReferenceUsable`: bài tồn tại, chưa xoá; `describeReference` → "Bài học: <tiêu đề>", route `/academic/lessons?lesson=<id>`.
- Video **không** tải lên (không tự host). Học sinh không bao giờ đi qua `/api/v1/files/**`.

## 5. Video & link

- `VideoEmbedResolver` (interface, Bot 1 viết impl thuần + test bảng URL): YouTube (`watch?v=`, `youtu.be/`, `shorts/`, `embed/`, `live/`) → `https://www.youtube-nocookie.com/embed/{id}`; Drive `file/d/{id}` | `open?id=` → `https://drive.google.com/file/d/{id}/preview`; Vimeo → `https://player.vimeo.com/video/{num}`. Host so khớp chính xác `LessonConstants.VIDEO_HOSTS`; khác → `LESSON_VIDEO_URL_NOT_ALLOWED`.
- LINK: chỉ `http`/`https` tuyệt đối, ≤ 1000 ký tự (chặn `javascript:`, `data:`, `file:`) → `LESSON_URL_INVALID`; `PROVIDER='OTHER'` (hoặc YOUTUBE/DRIVE/VIMEO nếu host khớp, không bắt buộc).
- UI chỉ đặt `embedUrl` vào `<iframe>` (`sandbox="allow-scripts allow-same-origin allow-presentation"`, `allow="fullscreen; picture-in-picture"`, `referrerpolicy="strict-origin-when-cross-origin"`, `loading="lazy"`), không bao giờ nhúng `url` thô.
- **CSP**: `nginx.conf` của UI hiện `default-src 'self'` không có `frame-src` → iframe bị chặn. Bot 2 thêm `frame-src 'self' https://www.youtube-nocookie.com https://drive.google.com https://player.vimeo.com;` (giữ nguyên phần còn lại; có hiệu lực khi build lại image lúc deploy).
- Lưu ý vận hành: YouTube "không công khai" vẫn chia sẻ được link → không phải bảo vệ bản quyền.

## 6. DTO (Java ↔ TypeScript)

Java ở `dto/request|response`; TS ở `src/api/lessons.types.ts` (staff) và `src/portal/api/lessons.ts` (portal). Khớp tên trường 1-1.
- Staff: `CourseFilterRequest`, `CourseUpsertRequest`, `LessonSectionUpsertRequest`, `LessonUpsertRequest`, `LessonReorderRequest` (+`SectionOrder`), `LessonResourceRequest`, `LessonResourceReorderRequest`, `CourseDto`, `CourseClassDto`, `CourseCopyResultDto`, `LessonTreeDto`, `LessonSectionDto`, `LessonSummaryDto`, `LessonDto`, `LessonResourceDto`, `ClassLessonProgressDto` (+`LessonColumn`, `StudentRow`; `statuses` là map lessonId→trạng thái, JSON key chuỗi), `FileResponseDto` (sẵn có), `AnnouncementClassOptionDto` (sẵn có).
- Portal (tách riêng, không lộ `version`, `createdBy`, `sourceLessonId`, ghi chú nội bộ): `PortalLessonProgressRequest`, `PortalLessonClassDto`, `PortalLessonTreeDto`, `PortalLessonSectionDto`, `PortalLessonListItemDto`, `PortalLessonDetailDto`, `PortalLessonResourceDto`, `PortalLessonProgressDto`, `PortalFileDto` (sẵn có).
- Service: `CourseService`, `LessonService`, `PortalLessonService`, `VideoEmbedResolver` (chỉ interface). Repository: `Course/LessonSection/Lesson/LessonResource/LessonProgressRepository`.

## 7. Mã lỗi (`LessonErrorCodes`)

| Mã | HTTP | Khi nào | Thông báo gợi ý |
|---|---|---|---|
| `COURSE_NOT_FOUND` | 404 | khoá không tồn tại / xoá | Không tìm thấy khoá học. |
| `COURSE_CODE_DUPLICATE` | 400 | mã khoá trùng | Mã khoá học đã tồn tại. |
| `COURSE_IN_USE` | 400 | xoá khoá còn lớp gắn | Khoá học đang được lớp sử dụng, hãy gỡ lớp hoặc lưu trữ khoá. |
| `COURSE_INVALID_STATE` | 400 | gắn lớp vào khoá lưu trữ / lớp đã gắn khoá khác | Không thể gắn lớp với khoá học này. |
| `LESSON_SECTION_NOT_FOUND` | 404 | chương không tồn tại / ngoài phạm vi | Không tìm thấy chương. |
| `LESSON_SECTION_NOT_EMPTY` | 400 | xoá chương còn bài | Chương còn bài học, hãy xoá hoặc chuyển bài trước. |
| `LESSON_NOT_FOUND` | 404 | bài không tồn tại / xoá / (cổng) chưa mở, DRAFT, ngoài lớp | Không tìm thấy bài học. |
| `LESSON_INVALID` | 400 | dữ liệu sai, chuyển chương khác chủ, vượt giới hạn | Thông tin bài học không hợp lệ: … |
| `LESSON_INVALID_STATE` | 400 | chuyển trạng thái sai | Không thể thực hiện với trạng thái hiện tại của bài học. |
| `LESSON_SESSION_INVALID` | 400 | buổi sai lớp / bài khoá / buổi huỷ | Buổi học không thuộc lớp của bài. |
| `LESSON_REORDER_INVALID` | 400 | danh sách sắp xếp thiếu/thừa | Danh sách sắp xếp không khớp, hãy tải lại. |
| `LESSON_RESOURCE_NOT_FOUND` | 404 | tài nguyên không có / khác bài | Không tìm thấy tài liệu. |
| `LESSON_RESOURCE_INVALID` | 400 | thiếu url / sai loại / quá số lượng | Tài liệu không hợp lệ. |
| `LESSON_VIDEO_URL_NOT_ALLOWED` | 400 | video ngoài allow-list | Chỉ nhận video YouTube, Google Drive hoặc Vimeo. |
| `LESSON_URL_INVALID` | 400 | URL không phải http(s) | Đường dẫn không hợp lệ. |
| `LESSON_ASSIGNMENT_INVALID` | 400 | bài tập khác lớp / đã xoá / bài khoá | Bài tập không thuộc lớp của bài học. |
| `NOT_CLASS_TEACHER` | 403 | GV ngoài lớp | Bạn không phụ trách lớp này. |
| `FILE_*`, `VALIDATION_ERROR`, lỗi lớp/ghi danh sẵn có | sẵn có | | |

Audit (`AuditActions`, khối GĐ3): `LESSON_COURSE_CREATED/UPDATED/DELETED/LINKED/UNLINKED/COPIED`, `LESSON_SECTION_CHANGED`, `LESSON_CREATED/UPDATED/PUBLISHED/UNPUBLISHED/DELETED/REORDERED`, `LESSON_RESOURCE_ADDED/REMOVED`, `LESSON_COMPLETED`; RESOURCE_TYPE `COURSE`, `LESSON_SECTION`, `LESSON`, `LESSON_RESOURCE`. Tải file bài học ghi `FILE_DOWNLOADED` (sẵn có).

## 8. Phân công (ownership)

| Hạng mục | Bot 1 – backend (`feat/portal-p3-api`, `E:\JAVA\edu-wt\p3-api`) | Bot 2 – frontend (`feat/portal-p3-web`, `E:\JAVA\edu-wt\p3-web`) |
|---|---|---|
| Migration V21_1..V21_4 | chỉ sửa khi có yêu cầu §9 (không chạy DB) | – |
| Entity / repository / DTO / hằng số / mã lỗi / interface (Luồng 0) | thêm được; đổi/bỏ qua §9 | – |
| `service/lesson/impl/*`: `CourseServiceImpl`, `LessonServiceImpl`, `PortalLessonServiceImpl`, `VideoEmbedResolverImpl` (+ helper `LessonAvailability`, `LessonAccess` tuỳ ý) | ✅ | – |
| `controller/CourseController`, `controller/LessonController`, `controller/PortalLessonController` | ✅ | – |
| `LessonFileModule` (luật + `@Component`), `SafeFileType.PPTX`, mở rộng `PortalFileServiceImpl` cho LESSON, `ClassEntity.courseId`, test fail-closed liên quan (`LessonContractTest.lessonDescriptorIsStubNotBean`, test registry nếu có) | ✅ | – |
| Test backend (service, MockMvc, IDOR, Clock.fixed, embed URL) | ✅ | – |
| `docs/portal-p3-api.md` | cập nhật §9 | đọc; ghi yêu cầu §9 |
| TS types/API (`src/api/lessons.ts`, `src/api/lessons.types.ts`, `src/portal/api/lessons.ts`) | – | ✅ (giữ khớp DTO) |
| Màn staff `/academic/courses` (`src/app/components/academic/courses/**`), `/academic/lessons` (`src/app/components/academic/lessons/**`), route `src/jsx/routes/lessons.routes.ts` | – | ✅ |
| Cổng `/portal/lessons/**` (`src/portal/lessons/**`), lối vào trên `PortalHome`/`MyClasses`/`PortalMeHub` (chỉ thêm) | – | ✅ |
| `nginx.conf` – thêm `frame-src` (§5) | – | ✅ (chỉ dòng CSP) |
| i18n `src/i18n/lessons.ts`, test Vitest | – | ✅ |
| `permissions.ts` (COURSE_*, LESSON_*), `menuPaths`/`menuIcons` (`book`, `book-open`) | – | thêm được, không đổi khoá đã có |

Ghép: Luồng 0 merge `feat/portal-p3-api` → `feat/portal-p3` và `feat/portal-p3-web` → `feat/portal-p3-ui`, chạy toàn bộ test, rồi chờ người dùng duyệt push/merge/deploy.

## 9. Yêu cầu thay đổi hợp đồng

| # | Ngày | Bên yêu cầu | Nội dung | Ảnh hưởng | Trạng thái |
|---|---|---|---|---|---|
| 0 | 13/10/2026 | Luồng 0 | Bản hợp đồng đầu tiên (ke-hoach V20_x → V21_x; PK tiến độ đổi sang ID + SEQ_ + UNIQUE; liên kết bài tập qua tài nguyên `ASSIGNMENT` thay vì cột mới trên LMS_ASSIGNMENTS) | – | chốt |
| 1 | 13/10/2026 | Bot 1 (BE) | **Không đổi DTO/endpoint/mã lỗi.** Ghi chú hành vi đã chốt khi code: (a) `unpublish` bài đang DRAFT → 400 `LESSON_INVALID_STATE`; (b) cây cổng chỉ trả chương có ≥1 bài nhìn thấy; (c) `copy-to-class` chép thêm `content`, `availableFrom`, `estimatedMinutes`, không chép `sessionId`, chương chép nối sau chương riêng sẵn có, vượt `MAX_SECTIONS_PER_OWNER` → `LESSON_INVALID`; (d) xoá khoá: tài nguyên FILE của khoá chỉ xoá mềm file khi không còn bản chép nào dùng `FILE_ID`; (e) đọc bài/cây chủ CLASS của giáo viên dùng `TeachingAssignmentGuard.requireCanWrite` như bài tập GĐ2 (403 `NOT_CLASS_TEACHER`); (f) audit `LESSON_REORDERED` dùng RESOURCE_TYPE `CLASS`/`COURSE` theo chủ | FE: (a)(b) chỉ ảnh hưởng hiển thị, không đổi kiểu | chờ duyệt |
| 2 | 13/10/2026 | Bot 1 (BE) | File ngoài danh sách §8 phải sửa: `PortalFileServiceImplTest` (thêm tham số constructor `LessonFileAccess` + test LESSON). Helper mới public trong `service/lesson/impl`: `LessonAccess`, `LessonCatalog`, `LessonRules`, `LessonFileAccess` (dùng chung cho `LessonFileModule` và `PortalFileServiceImpl`). `SafeFileType.PPTX` nằm trong `allExtensions()` nên module dùng mặc định "mọi đuôi" (STUDENT, LIBRARY, module không có cấu hình DB) cũng nhận `pptx` | không đổi hợp đồng FE; vận hành: nếu không muốn pptx ở module khác thì cấu hình `SYS_FILE_MODULE_SETTINGS` | chờ duyệt |
