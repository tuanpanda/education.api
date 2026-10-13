package com.education.base.common.lesson;

import lombok.experimental.UtilityClass;

import java.time.ZoneId;
import java.util.List;

/**
 * Hằng số nghiệp vụ học liệu (GĐ3, bảng {@code LMS_COURSES} / {@code LMS_LESSON_SECTIONS} / {@code LMS_LESSONS} /
 * {@code LMS_LESSON_RESOURCES} / {@code LMS_LESSON_PROGRESS} - V21_1..V21_3). Hợp đồng: {@code docs/portal-p3-api.md}.
 * <p>
 * Hợp đồng Luồng 0. Bot 1 (backend) được THÊM hằng số mới; đổi giá trị hiện có phải qua §9.
 */
@UtilityClass
public class LessonConstants {

    /** Giờ mở bài so bằng giờ server theo múi này (cột TIMESTAMP không múi giờ) - giống bài tập GĐ2. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** {@code LMS_COURSES.STATUS} ({@code CK_COURSE_STATUS}). ARCHIVED: không gắn thêm lớp mới, lớp cũ vẫn thấy bài. */
    @UtilityClass
    public static class CourseStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String ARCHIVED = "ARCHIVED";
        public static final String PATTERN = "ACTIVE|ARCHIVED";
    }

    /** {@code LMS_LESSONS.STATUS} ({@code CK_LES_STATUS}). */
    @UtilityClass
    public static class LessonStatus {
        public static final String DRAFT = "DRAFT";
        public static final String PUBLISHED = "PUBLISHED";
        public static final String PATTERN = "DRAFT|PUBLISHED";
    }

    /** Chủ sở hữu chương/bài: khoá mẫu hoặc lớp ({@code CK_LSEC_OWNER} / {@code CK_LES_OWNER}). */
    @UtilityClass
    public static class OwnerType {
        public static final String COURSE = "COURSE";
        public static final String CLASS = "CLASS";
        public static final String PATTERN = "COURSE|CLASS";
    }

    /** {@code LMS_LESSON_RESOURCES.RESOURCE_TYPE} ({@code CK_LRES_TYPE}). */
    @UtilityClass
    public static class ResourceType {
        public static final String FILE = "FILE";
        public static final String VIDEO = "VIDEO";
        public static final String LINK = "LINK";
        /** Bài tập GĐ2 ({@code LMS_ASSIGNMENTS}) cùng lớp - chỉ cho bài học thuộc lớp. */
        public static final String ASSIGNMENT = "ASSIGNMENT";
        public static final String PATTERN = "FILE|VIDEO|LINK|ASSIGNMENT";
        /** Loại tạo bằng JSON ({@code POST /lessons/{id}/resources}); FILE tạo bằng multipart. */
        public static final String JSON_PATTERN = "VIDEO|LINK|ASSIGNMENT";
    }

    /** {@code LMS_LESSON_RESOURCES.PROVIDER} ({@code CK_LRES_PROVIDER}). */
    @UtilityClass
    public static class VideoProvider {
        public static final String YOUTUBE = "YOUTUBE";
        public static final String DRIVE = "DRIVE";
        public static final String VIMEO = "VIMEO";
        /** Chỉ cho LINK. */
        public static final String OTHER = "OTHER";
    }

    /**
     * Allow-list host video (so khớp chính xác host, không theo hậu tố tự do). Link VIDEO ngoài danh sách -> 400
     * {@code LESSON_VIDEO_URL_NOT_ALLOWED}. Embed sinh ở server (§5 hợp đồng).
     */
    public static final List<String> VIDEO_HOSTS = List.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com",
            "drive.google.com", "vimeo.com", "www.vimeo.com", "player.vimeo.com");

    /** Origin được nhúng iframe - phải khớp CSP {@code frame-src} của nginx (Bot 2). */
    public static final List<String> EMBED_ORIGINS = List.of(
            "https://www.youtube-nocookie.com", "https://drive.google.com", "https://player.vimeo.com");

    /** {@code LMS_LESSON_PROGRESS.STATUS} ({@code CK_LPRG_STATUS}); NOT_STARTED = không có dòng (chỉ trong DTO). */
    @UtilityClass
    public static class ProgressStatus {
        public static final String NOT_STARTED = "NOT_STARTED";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String COMPLETED = "COMPLETED";
        /** Giá trị học sinh được gửi lên ({@code POST /portal/me/lessons/{id}/progress}). */
        public static final String REQUEST_PATTERN = "IN_PROGRESS|COMPLETED";
    }

    public static final int TITLE_MAX = 200;
    public static final int COURSE_CODE_MAX = 50;
    public static final int COURSE_NAME_MAX = 200;
    public static final int URL_MAX = 1000;
    /** Giới hạn ký tự nội dung bài học (HTML đã sanitize). */
    public static final int CONTENT_MAX = 200_000;
    public static final int MAX_SECTIONS_PER_OWNER = 100;
    public static final int MAX_LESSONS_PER_SECTION = 200;
    public static final int MAX_RESOURCES_PER_LESSON = 30;
}
