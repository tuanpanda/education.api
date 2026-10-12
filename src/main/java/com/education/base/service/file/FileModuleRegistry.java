package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Registry fail-closed các {@link FileModuleDescriptor}: module không có descriptor bị từ chối (sửa S1).
 * <p>
 * File Excel import học sinh lưu {@code MODULE_NAME=STUDENT} + đường dẫn {@code STUDENT/IMPORT/...}; registry ánh xạ
 * chúng về descriptor {@code STUDENT_IMPORT}.
 */
@Component
public class FileModuleRegistry {

    static final String IMPORT_SUBFOLDER = "IMPORT";
    private static final String STUDENT_IMPORT_PATH_PREFIX = FileConstants.Module.STUDENT + "/" + IMPORT_SUBFOLDER + "/";

    private final Map<String, FileModuleDescriptor> descriptors;

    public FileModuleRegistry(List<FileModuleDescriptor> beans) {
        Map<String, FileModuleDescriptor> map = new TreeMap<>();
        for (FileModuleDescriptor d : beans) {
            String code = normalize(d.code());
            if (map.putIfAbsent(code, d) != null) {
                throw new IllegalStateException("Duplicate FileModuleDescriptor for module " + code);
            }
        }
        this.descriptors = Collections.unmodifiableMap(map);
    }

    public Optional<FileModuleDescriptor> find(String code) {
        return Optional.ofNullable(descriptors.get(normalize(code)));
    }

    /** Descriptor của module hoặc 400 {@code INVALID_MODULE}. */
    public FileModuleDescriptor require(String code) {
        return find(code).orElseThrow(() -> new OracleBusinessException(FileErrorCodes.INVALID_MODULE,
                "Module '" + (code == null ? "" : code) + "' không hỗ trợ file."));
    }

    /** Mã descriptor của một file đã lưu (STUDENT + đường dẫn IMPORT -> STUDENT_IMPORT). */
    public static String descriptorCode(FileEntity file) {
        String module = normalize(file.getModuleName());
        if (FileConstants.Module.STUDENT.equals(module) && file.getFilePath() != null
                && file.getFilePath().replace('\\', '/').toUpperCase(Locale.ROOT).startsWith(STUDENT_IMPORT_PATH_PREFIX)) {
            return FileConstants.Module.STUDENT_IMPORT;
        }
        return module;
    }

    /** Mã descriptor cho một lần lưu ({@code module} + thư mục con). */
    public static String descriptorCode(String module, String subFolder) {
        String m = normalize(module);
        if (FileConstants.Module.STUDENT.equals(m) && subFolder != null
                && IMPORT_SUBFOLDER.equalsIgnoreCase(subFolder.trim())) {
            return FileConstants.Module.STUDENT_IMPORT;
        }
        return m;
    }

    public Optional<FileModuleDescriptor> forFile(FileEntity file) {
        return find(descriptorCode(file));
    }

    public Collection<FileModuleDescriptor> all() {
        return descriptors.values();
    }

    public static String normalize(String code) {
        return code == null || code.isBlank() ? FileConstants.Module.COMMON : code.trim().toUpperCase(Locale.ROOT);
    }
}
