package com.education.base.common.file;

import com.education.base.exception.OracleBusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Danh sách trắng (allow-list) định dạng file được phép upload / phục vụ.
 * <p>
 * Loại file được xác định phía server bằng <b>phần mở rộng + magic bytes</b>, không tin
 * {@code Content-Type} client gửi lên. Chỉ ảnh (jpg/png/gif/webp) và PDF được hiển thị inline;
 * mọi loại khác luôn trả {@code Content-Disposition: attachment}. SVG, HTML, XML, script... bị chặn.
 */
public enum SafeFileType {

    JPEG("image/jpeg", true, "jpg", "jpeg"),
    PNG("image/png", true, "png"),
    GIF("image/gif", true, "gif"),
    WEBP("image/webp", true, "webp"),
    PDF("application/pdf", true, "pdf"),
    DOC("application/msword", false, "doc"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, "docx"),
    XLS("application/vnd.ms-excel", false, "xls"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, "xlsx"),
    CSV("text/csv", false, "csv"),
    TXT("text/plain", false, "txt"),
    ZIP("application/zip", false, "zip");

    /** Số byte đầu file cần đọc để nhận diện định dạng. */
    public static final int HEADER_BYTES = 8192;

    public static final String OCTET_STREAM = "application/octet-stream";

    /** Phần mở rộng luôn bị từ chối (nội dung có thể thực thi script khi trình duyệt render). */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "svg", "svgz", "html", "htm", "xhtml", "xht", "shtml", "xml", "xsl", "xslt",
            "js", "mjs", "hta", "swf", "jsp", "php", "exe", "bat", "cmd", "ps1", "sh");

    /** Dấu hiệu markup ở đầu file text (HTML/SVG/XML) - bị từ chối kể cả khi đặt tên .txt/.csv. */
    private static final String[] MARKUP_PREFIXES = {
            "<!doctype", "<html", "<head", "<body", "<svg", "<?xml", "<script", "<iframe", "<object",
            "<embed", "<meta", "<link", "<style", "<img", "<a ", "<div", "<xml", "<!--"};

    private static final byte[] SIG_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIG_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] SIG_GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] SIG_ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] SIG_ZIP_EMPTY = {'P', 'K', 0x05, 0x06};

    private final String mimeType;
    private final boolean inlineSafe;
    private final Set<String> extensions;

    SafeFileType(String mimeType, boolean inlineSafe, String... extensions) {
        this.mimeType = mimeType;
        this.inlineSafe = inlineSafe;
        this.extensions = Set.of(extensions);
    }

    /** MIME chuẩn lưu vào {@code SYS_ATTACHED_FILES.CONTENT_TYPE}. */
    public String mimeType() {
        return mimeType;
    }

    /** {@code Content-Type} trả về client (text kèm charset UTF-8). */
    public String responseContentType() {
        return (this == CSV || this == TXT) ? mimeType + ";charset=UTF-8" : mimeType;
    }

    /** Chỉ ảnh và PDF được phép hiển thị inline. */
    public boolean isInlineSafe() {
        return inlineSafe;
    }

    /**
     * Nhận diện và kiểm tra file upload: phần mở rộng phải thuộc allow-list và magic bytes phải khớp.
     *
     * @param filename tên file gốc (đã hoặc chưa làm sạch).
     * @param header   các byte đầu file (tối đa {@link #HEADER_BYTES}).
     * @throws OracleBusinessException {@code FILE_TYPE_NOT_ALLOWED} hoặc {@code FILE_CONTENT_MISMATCH}.
     */
    public static SafeFileType detect(String filename, byte[] header) {
        String ext = extensionOf(filename);
        if (ext == null) {
            throw notAllowed();
        }
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new OracleBusinessException("FILE_TYPE_NOT_ALLOWED",
                    "Không cho phép tải lên file ." + ext + " (SVG/HTML/script).");
        }
        SafeFileType type = byExtension(ext).orElseThrow(SafeFileType::notAllowed);
        byte[] head = header == null ? new byte[0] : header;
        if (!type.matchesContent(head)) {
            throw new OracleBusinessException("FILE_CONTENT_MISMATCH",
                    "Nội dung file không đúng định dạng ." + ext + ".");
        }
        return type;
    }

    /**
     * Loại file an toàn để phục vụ một file đã lưu: {@code CONTENT_TYPE} đã lưu phải thuộc allow-list
     * <b>và</b> khớp phần mở rộng của tên file. Dữ liệu cũ không khớp (ví dụ {@code text/html}) trả rỗng
     * → controller phục vụ dạng {@code application/octet-stream} + attachment.
     */
    public static Optional<SafeFileType> forStoredFile(String storedContentType, String originalName) {
        if (storedContentType == null || storedContentType.isBlank()) {
            return Optional.empty();
        }
        String mime = storedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(mime) || "image/pjpeg".equals(mime)) {
            mime = JPEG.mimeType;
        }
        String ext = extensionOf(originalName);
        for (SafeFileType type : values()) {
            if (type.mimeType.equals(mime) && ext != null && type.extensions.contains(ext)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static Optional<SafeFileType> byExtension(String ext) {
        if (ext == null) {
            return Optional.empty();
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensions.contains(normalized)).findFirst();
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean matchesContent(byte[] head) {
        return switch (this) {
            case JPEG -> startsWith(head, SIG_JPEG, 0);
            case PNG -> startsWith(head, SIG_PNG, 0);
            case GIF -> startsWith(head, SIG_GIF87, 0) || startsWith(head, SIG_GIF89, 0);
            case WEBP -> startsWith(head, SIG_RIFF, 0) && startsWith(head, SIG_WEBP, 8);
            case PDF -> startsWith(head, SIG_PDF, 0);
            case DOC, XLS -> startsWith(head, SIG_OLE, 0);
            case DOCX, XLSX -> startsWith(head, SIG_ZIP, 0);
            case ZIP -> startsWith(head, SIG_ZIP, 0) || startsWith(head, SIG_ZIP_EMPTY, 0);
            case CSV, TXT -> isPlainText(head);
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Text thuần: không có byte NUL / ký tự điều khiển nhị phân, không mở đầu bằng markup HTML/SVG/XML. */
    private static boolean isPlainText(byte[] head) {
        for (byte b : head) {
            int v = b & 0xFF;
            if (v == 0 || (v < 0x20 && v != '\t' && v != '\n' && v != '\r' && v != '\f' && v != 0x1B)) {
                return false;
            }
        }
        String text = new String(head, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String start = text.stripLeading().toLowerCase(Locale.ROOT);
        for (String prefix : MARKUP_PREFIXES) {
            if (start.startsWith(prefix)) {
                return false;
            }
        }
        return !start.contains("<script") && !start.contains("<svg") && !start.contains("<html");
    }

    private static OracleBusinessException notAllowed() {
        return new OracleBusinessException("FILE_TYPE_NOT_ALLOWED",
                "Định dạng file không được hỗ trợ. Chỉ chấp nhận ảnh (jpg, png, gif, webp), pdf, "
                        + "doc/docx, xls/xlsx, csv, txt, zip.");
    }
}
