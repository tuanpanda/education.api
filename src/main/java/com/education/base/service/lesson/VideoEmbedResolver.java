package com.education.base.service.lesson;

/**
 * Chuyển URL video người dùng dán thành URL nhúng an toàn (§5 hợp đồng). Bot 1 viết {@code VideoEmbedResolverImpl}
 * (thuần, không gọi mạng) + test bảng URL.
 * <ul>
 *   <li>YouTube: {@code youtube.com/watch?v=ID}, {@code youtu.be/ID}, {@code youtube.com/shorts/ID}, {@code /embed/ID},
 *       {@code /live/ID} -> {@code https://www.youtube-nocookie.com/embed/ID} (ID 11 ký tự [A-Za-z0-9_-]; giữ
 *       {@code t}/{@code start} thành {@code ?start=giây}).</li>
 *   <li>Google Drive: {@code drive.google.com/file/d/ID/...} hoặc {@code open?id=ID} -> {@code https://drive.google.com/file/d/ID/preview}.</li>
 *   <li>Vimeo: {@code vimeo.com/NUM}, {@code player.vimeo.com/video/NUM} -> {@code https://player.vimeo.com/video/NUM}.</li>
 * </ul>
 * Chỉ http/https; host phải nằm trong {@code LessonConstants.VIDEO_HOSTS} (so khớp chính xác) -> else 400
 * {@code LESSON_VIDEO_URL_NOT_ALLOWED}. Không nhận dạng được ID cũng 400 cùng mã.
 */
public interface VideoEmbedResolver {

    VideoEmbed resolve(String url);

    /** @param provider YOUTUBE / DRIVE / VIMEO; @param normalizedUrl URL gốc đã trim (lưu cột URL). */
    record VideoEmbed(String provider, String embedUrl, String normalizedUrl) {
    }
}
