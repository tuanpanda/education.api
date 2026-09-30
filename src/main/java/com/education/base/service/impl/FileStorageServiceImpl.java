package com.education.base.service.impl;

import com.education.base.common.file.SafeFileType;
import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Triển khai {@link FileStorageService}.
 * <p>
 * Chiến lược lưu trữ: {@code {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{tenfile}}.
 * Database chỉ lưu đường dẫn TƯƠNG ĐỐI so với {@code baseDir} để đảm bảo tính di động khi deploy.
 * <p>
 * Định dạng file được kiểm tra bằng allow-list {@link SafeFileType} (phần mở rộng + magic bytes);
 * {@code CONTENT_TYPE} lưu xuống là MIME do server nhận diện, không phải giá trị client gửi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final String DEFAULT_MODULE = "COMMON";
    private static final String DEFAULT_FILE_NAME = "unnamed";

    private final FileRepository fileRepository;
    private final FileStorageProperties fileStorageProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId) {
        return storeFile(file, moduleName, referenceId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId, String subFolder) {
        if (file == null || file.isEmpty()) {
            throw new OracleBusinessException("FILE_EMPTY", "File tải lên không được để trống.");
        }
        SafeFileType type = SafeFileType.detect(file.getOriginalFilename(), readHeader(file));
        PathTarget target = prepareTarget(file.getOriginalFilename(), moduleName, subFolder);
        try {
            Files.createDirectories(target.path().getParent());
            file.transferTo(target.path());
        } catch (IOException e) {
            log.error("Lỗi khi lưu file vật lý vào '{}': {}", target.path(), e.getMessage(), e);
            throw new OracleBusinessException("FILE_STORE_ERROR", "Không thể lưu file vào hệ thống.");
        }
        return saveMetadata(target, type.mimeType(), file.getSize(), moduleName, referenceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeBytes(byte[] content, String originalFilename, String contentType,
                                 String moduleName, Long referenceId, String subFolder) {
        if (content == null || content.length == 0) {
            throw new OracleBusinessException("FILE_EMPTY", "File tải lên không được để trống.");
        }
        SafeFileType type = SafeFileType.detect(originalFilename,
                Arrays.copyOf(content, Math.min(content.length, SafeFileType.HEADER_BYTES)));
        PathTarget target = prepareTarget(originalFilename, moduleName, subFolder);
        try {
            Files.createDirectories(target.path().getParent());
            Files.write(target.path(), content);
        } catch (IOException e) {
            log.error("Lỗi khi lưu file vật lý vào '{}': {}", target.path(), e.getMessage(), e);
            throw new OracleBusinessException("FILE_STORE_ERROR", "Không thể lưu file vào hệ thống.");
        }
        return saveMetadata(target, type.mimeType(), content.length, moduleName, referenceId);
    }

    /** Đọc các byte đầu file để nhận diện định dạng bằng magic bytes. */
    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SafeFileType.HEADER_BYTES);
        } catch (IOException e) {
            log.error("Không đọc được nội dung file upload '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new OracleBusinessException("FILE_READ_ERROR", "Không đọc được nội dung file tải lên.");
        }
    }

    private PathTarget prepareTarget(String originalFilename, String moduleName, String subFolder) {
        String cleanName = cleanFileName(originalFilename);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + cleanName;
        String module = normalizeModule(moduleName);
        String extra = normalizeSubFolder(subFolder);
        LocalDate today = LocalDate.now();
        String relativeDir = extra == null
                ? module + "/" + today.format(YEAR_FORMATTER) + "/" + today.format(MONTH_FORMATTER)
                : module + "/" + extra + "/" + today.format(YEAR_FORMATTER) + "/" + today.format(MONTH_FORMATTER);
        String relativePath = relativeDir + "/" + storedName;
        Path baseDir = resolveBaseDir();
        Path targetFile = assertInsideBaseDir(baseDir.resolve(relativePath), baseDir,
                "INVALID_PATH", "Đường dẫn lưu file không hợp lệ.");
        return new PathTarget(cleanName, storedName, relativePath, module, targetFile);
    }

    private FileEntity saveMetadata(PathTarget target, String contentType, long fileSize,
                                    String moduleName, Long referenceId) {
        try {
            FileEntity saved = fileRepository.save(FileEntity.builder()
                    .originalName(target.cleanName())
                    .storedName(target.storedName())
                    .filePath(target.relativePath())
                    .contentType(contentType)
                    .fileSize(fileSize)
                    .moduleName(target.module())
                    .referenceId(referenceId)
                    .isDeleted(0)
                    .build());
            log.info("Đã lưu file '{}' (id={}) tại đường dẫn tương đối '{}'",
                    saved.getOriginalName(), saved.getId(), saved.getFilePath());
            return saved;
        } catch (RuntimeException e) {
            deleteQuietly(target.path());
            throw e;
        }
    }

    private record PathTarget(String cleanName, String storedName, String relativePath, String module, Path path) {
    }

    @Override
    public Resource loadFileAsResource(Long fileId) {
        FileEntity entity = getFileEntity(fileId);
        if (entity.getFilePath() == null || entity.getFilePath().isBlank()) {
            throw new OracleBusinessException("FILE_PATH_ERROR", "Đường dẫn file không hợp lệ.");
        }

        Path baseDir = resolveBaseDir();
        Path filePath = assertInsideBaseDir(baseDir.resolve(entity.getFilePath()), baseDir,
                "PATH_TRAVERSAL_DETECTED", "Đường dẫn file không hợp lệ.");

        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new OracleBusinessException("FILE_NOT_FOUND", "Không tìm thấy file trên hệ thống lưu trữ.");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new OracleBusinessException("FILE_NOT_FOUND", "Không tìm thấy file trên hệ thống lưu trữ.");
            }
            return resource;
        } catch (MalformedURLException e) {
            log.error("Đường dẫn file không hợp lệ cho id={}: {}", fileId, e.getMessage(), e);
            throw new OracleBusinessException("FILE_PATH_ERROR", "Đường dẫn file không hợp lệ.");
        }
    }

    @Override
    public FileEntity getFileEntity(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(f -> f.getIsDeleted() == null || f.getIsDeleted() == 0)
                .orElseThrow(() -> new OracleBusinessException(
                        "FILE_NOT_FOUND", "Không tìm thấy thông tin file với ID: " + fileId));
    }

    @Override
    public List<FileEntity> getFilesByRef(String moduleName, Long referenceId) {
        return fileRepository.getFilesByRef(normalizeModule(moduleName), referenceId);
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
            log.warn("Đã xóa file vật lý '{}' vì không ghi được metadata vào Database.", file);
        } catch (IOException e) {
            log.error("Không thể xóa file vật lý '{}' sau khi lưu metadata thất bại: {}", file, e.getMessage(), e);
        }
    }

    private Path resolveBaseDir() {
        return Paths.get(fileStorageProperties.getBaseDir()).toAbsolutePath().normalize();
    }

    /**
     * Chuẩn hóa đường dẫn tuyệt đối rồi bắt buộc kết quả phải nằm trong {@code baseDir},
     * chặn Directory Traversal ({@code ../}) và đường dẫn tuyệt đối xen vào {@code FILE_PATH}.
     */
    private Path assertInsideBaseDir(Path candidate, Path baseDir, String errorCode, String message) {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path base = baseDir.toAbsolutePath().normalize();
        if (!normalized.startsWith(base) || normalized.equals(base)) {
            log.error("Phát hiện Path Traversal: candidate='{}', baseDir='{}'", normalized, base);
            throw new OracleBusinessException(errorCode, message);
        }
        return normalized;
    }

    private String normalizeModule(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return DEFAULT_MODULE;
        }
        String module = moduleName.trim().toUpperCase(Locale.ROOT);
        if (!module.matches("[A-Z0-9_-]+")) {
            throw new OracleBusinessException("INVALID_MODULE",
                    "Tên module chỉ được chứa chữ, số, gạch ngang và gạch dưới.");
        }
        return module;
    }

    private String normalizeSubFolder(String subFolder) {
        if (subFolder == null || subFolder.isBlank()) {
            return null;
        }
        String folder = subFolder.trim().toUpperCase(Locale.ROOT);
        if (!folder.matches("[A-Z0-9_]+")) {
            throw new OracleBusinessException("INVALID_MODULE",
                    "Thư mục con chỉ được chứa chữ, số và gạch dưới.");
        }
        return folder;
    }

    /**
     * Loại bỏ thành phần thư mục và các ký tự không an toàn khỏi tên file người dùng gửi lên,
     * chặn các trường hợp như {@code ../../evil.txt}.
     */
    private String cleanFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        String name = Paths.get(originalFilename.replace("\\", "/")).getFileName().toString();
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() || name.replace(".", "").isBlank() ? DEFAULT_FILE_NAME : name;
    }
}
