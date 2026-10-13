package com.education.base.service.lesson.impl;

import com.education.base.common.lesson.LessonConstants;
import com.education.base.common.lesson.LessonConstants.VideoProvider;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.lesson.VideoEmbedResolver;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chuyển URL video sang URL nhúng (§5 hợp đồng). Thuần: không gọi mạng; host so khớp chính xác VIDEO_HOSTS. */
@Component
public class VideoEmbedResolverImpl implements VideoEmbedResolver {

    static final String YOUTUBE_EMBED = "https://www.youtube-nocookie.com/embed/";
    static final String DRIVE_EMBED = "https://drive.google.com/file/d/%s/preview";
    static final String VIMEO_EMBED = "https://player.vimeo.com/video/";

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "www.youtube-nocookie.com");
    private static final Set<String> VIMEO_HOSTS = Set.of("vimeo.com", "www.vimeo.com");
    private static final Pattern YT_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern DRIVE_ID = Pattern.compile("[A-Za-z0-9_-]{10,200}");
    private static final Pattern VIMEO_ID = Pattern.compile("\\d{1,20}");
    private static final Pattern YT_TIME = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s?)?");

    @Override
    public VideoEmbed resolve(String url) {
        if (url == null || url.isBlank()) {
            throw notAllowed();
        }
        String trimmed = url.trim();
        if (trimmed.length() > LessonConstants.URL_MAX) {
            throw notAllowed();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw notAllowed();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getRawUserInfo() != null) {
            throw notAllowed();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!LessonConstants.VIDEO_HOSTS.contains(host)) {
            throw notAllowed();
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String[] seg = segments(path);
        Map<String, String> q = query(uri.getRawQuery());

        if ("youtu.be".equals(host)) {
            return youtube(seg.length >= 1 ? seg[0] : null, q, trimmed);
        }
        if (YOUTUBE_HOSTS.contains(host)) {
            if (seg.length == 1 && "watch".equals(seg[0])) {
                return youtube(q.get("v"), q, trimmed);
            }
            if (seg.length >= 2 && Set.of("shorts", "embed", "live").contains(seg[0])) {
                return youtube(seg[1], q, trimmed);
            }
            throw notAllowed();
        }
        if ("drive.google.com".equals(host)) {
            String id = null;
            if (seg.length >= 3 && "file".equals(seg[0]) && "d".equals(seg[1])) {
                id = seg[2];
            } else if (seg.length == 1 && "open".equals(seg[0])) {
                id = q.get("id");
            }
            if (id == null || !DRIVE_ID.matcher(id).matches()) {
                throw notAllowed();
            }
            return new VideoEmbed(VideoProvider.DRIVE, String.format(DRIVE_EMBED, id), trimmed);
        }
        if ("player.vimeo.com".equals(host)) {
            if (seg.length >= 2 && "video".equals(seg[0])) {
                return vimeo(seg[1], trimmed);
            }
            throw notAllowed();
        }
        if (VIMEO_HOSTS.contains(host) && seg.length >= 1) {
            return vimeo(seg[0], trimmed);
        }
        throw notAllowed();
    }

    /** Provider theo host (cho LINK: YOUTUBE/DRIVE/VIMEO nếu khớp, không bắt buộc); else OTHER. */
    static String providerOfHost(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (!LessonConstants.VIDEO_HOSTS.contains(h)) {
            return VideoProvider.OTHER;
        }
        if (h.contains("youtu")) {
            return VideoProvider.YOUTUBE;
        }
        if (h.contains("vimeo")) {
            return VideoProvider.VIMEO;
        }
        return VideoProvider.DRIVE;
    }

    private static VideoEmbed youtube(String id, Map<String, String> q, String original) {
        if (id == null || !YT_ID.matcher(id).matches()) {
            throw notAllowed();
        }
        long start = seconds(q.getOrDefault("t", q.get("start")));
        return new VideoEmbed(VideoProvider.YOUTUBE, YOUTUBE_EMBED + id + (start > 0 ? "?start=" + start : ""),
                original);
    }

    private static VideoEmbed vimeo(String id, String original) {
        if (id == null || !VIMEO_ID.matcher(id).matches()) {
            throw notAllowed();
        }
        return new VideoEmbed(VideoProvider.VIMEO, VIMEO_EMBED + id, original);
    }

    /** "90", "90s", "1m30s", "1h2m3s" -> giây; hỏng -> 0. */
    static long seconds(String t) {
        if (t == null || t.isBlank() || t.length() > 20) {
            return 0;
        }
        Matcher m = YT_TIME.matcher(t.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return 0;
        }
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long s = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        return h * 3600 + min * 60 + s;
    }

    private static String[] segments(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.isEmpty() ? new String[0] : p.split("/");
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                out.putIfAbsent(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // tham số hỏng: bỏ qua
            }
        }
        return out;
    }

    static OracleBusinessException notAllowed() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_VIDEO_URL_NOT_ALLOWED,
                "Chỉ nhận video YouTube, Google Drive hoặc Vimeo.");
    }
}
