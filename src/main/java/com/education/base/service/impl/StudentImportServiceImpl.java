package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.excel.StudentExcelHelper;
import com.education.base.dto.request.StudentImportRowDto;
import com.education.base.dto.response.ImportRowErrorDto;
import com.education.base.dto.response.StudentImportResultResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.FileStorageService;
import com.education.base.service.StudentImportService;
import com.education.base.service.StudentService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Import học sinh từ Excel: kiểm tra hàng loạt, lưu các dòng hợp lệ, trả lỗi chi tiết từng dòng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentImportServiceImpl implements StudentImportService {

    static final long MAX_IMPORT_BYTES = 5L * 1024 * 1024;

    static final String IMPORT_SUBFOLDER = "IMPORT";

    private static final String DEFAULT_STATUS = "ACTIVE";

    private static final String ENROLLMENT_STATUS = "ENROLLED";

    private static final Set<String> ACTIVE_CLASS_STATUSES = Set.of("OPEN", "ONGOING");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\s.-]{8,20}$");

    private static final int IN_CLAUSE_CHUNK = 400;

    private final StudentExcelHelper studentExcelHelper;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final FileStorageService fileStorageService;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    @Override
    public byte[] generateTemplate() {
        List<StudentExcelHelper.ClassCatalogRow> catalog = classRepository
                .findByStatusInAndIsDeletedOrderByCreatedAtDesc(
                        List.of("OPEN", "ONGOING"), PersistenceFlags.NOT_DELETED)
                .stream()
                .filter(c -> c.getClassCode() != null && !c.getClassCode().isBlank())
                .sorted(Comparator.comparing(ClassEntity::getClassCode, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new StudentExcelHelper.ClassCatalogRow(
                        c.getClassCode(),
                        c.getClassName(),
                        c.getStatus(),
                        c.getGradeLevel()))
                .toList();
        return studentExcelHelper.generateStudentTemplate(catalog);
    }

    /**
     * Import học sinh. File Excel gốc chỉ được lưu (audit, {@code STUDENT/IMPORT/YYYY/MM/}) <b>sau khi</b>
     * transaction import commit thành công - file lỗi / rỗng / import thất bại không để lại file rác.
     * {@code CREATED_BY} ghi tên đăng nhập của người thực hiện import.
     */
    @Override
    public StudentImportResultResponse importStudents(MultipartFile file) {
        byte[] bytes = readAndValidateFile(file);
        String importedBy = SecurityUtils.currentUsername();

        List<StudentImportRowDto> rows = studentExcelHelper.parseStudentsFromExcel(new ByteArrayInputStream(bytes));
        if (rows.isEmpty()) {
            throw new OracleBusinessException("IMPORT_NO_DATA",
                    "File Excel không có dòng dữ liệu (bỏ qua dòng tiêu đề).");
        }
        StudentImportResultResponse result = new TransactionTemplate(transactionManager)
                .execute(status -> persistValidRows(rows, importedBy));
        storeImportFile(file, bytes);
        return result != null ? result : StudentImportResultResponse.builder().totalRows(rows.size()).build();
    }

    /**
     * Lưu file import để đối soát. Dữ liệu đã commit nên lỗi lưu file chỉ ghi log, không làm hỏng kết quả import.
     */
    private void storeImportFile(MultipartFile file, byte[] bytes) {
        try {
            fileStorageService.storeBytes(
                    bytes,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    StudentService.MODULE_NAME,
                    null,
                    IMPORT_SUBFOLDER);
        } catch (RuntimeException e) {
            log.error("Import học sinh đã hoàn tất nhưng không lưu được file gốc '{}': {}",
                    file.getOriginalFilename(), e.getMessage(), e);
        }
    }

    private StudentImportResultResponse persistValidRows(List<StudentImportRowDto> rows, String importedBy) {
        Set<String> existingCodes = loadExistingCodes(rows);
        Map<String, ClassEntity> classes = loadClasses(rows);
        Map<Long, Long> enrolledByClass = new HashMap<>();
        Map<String, Integer> fileCodeFirstRow = new LinkedHashMap<>();

        List<ImportRowErrorDto> errors = new ArrayList<>();
        List<StudentImportRowDto> valid = new ArrayList<>();
        Map<Long, Integer> incomingByClass = new HashMap<>();

        for (StudentImportRowDto row : rows) {
            List<ImportRowErrorDto> rowErrors = validateRow(row, existingCodes, fileCodeFirstRow, classes,
                    enrolledByClass, incomingByClass);
            if (rowErrors.isEmpty()) {
                valid.add(row);
            } else {
                errors.addAll(rowErrors);
            }
        }

        // Lưu theo lô: ID lấy từ sequence ngay khi persist, Hibernate gom INSERT theo hibernate.jdbc.batch_size;
        // STUDENT_CODE do trigger sinh được Hibernate đọc lại nhờ @Generated nên không cần refresh từng dòng.
        List<StudentEntity> students = new ArrayList<>(valid.size());
        for (StudentImportRowDto row : valid) {
            students.add(StudentEntity.builder()
                    .studentCode(blankToNull(row.getStudentCode()))
                    .fullName(row.getFullName().trim())
                    .status(resolveStatus(row.getStatus()))
                    .email(blankToNull(row.getEmail()))
                    .phone(blankToNull(row.getPhone()))
                    .dateOfBirth(StudentExcelHelper.parseDate(row.getDateOfBirth()))
                    .parentName(blankToNull(row.getParentName()))
                    .address(blankToNull(row.getAddress()))
                    .note(blankToNull(row.getNote()))
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .createdBy(importedBy)
                    .build());
        }
        List<StudentEntity> savedStudents = students.isEmpty() ? List.of() : studentRepository.saveAll(students);

        List<ClassStudentEntity> enrollments = new ArrayList<>();
        LocalDateTime enrolledAt = LocalDateTime.now();
        for (int i = 0; i < valid.size(); i++) {
            String classCode = blankToNull(valid.get(i).getClassCode());
            if (classCode != null) {
                ClassEntity clazz = classes.get(classCode.toUpperCase(Locale.ROOT));
                enrollments.add(ClassStudentEntity.builder()
                        .classId(clazz.getId())
                        .studentId(savedStudents.get(i).getId())
                        .status(ENROLLMENT_STATUS)
                        .enrolledAt(enrolledAt)
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .createdBy(importedBy)
                        .build());
            }
        }
        if (!enrollments.isEmpty()) {
            classStudentRepository.saveAll(enrollments);
        }
        if (!students.isEmpty()) {
            entityManager.flush();
        }

        log.info("Import học sinh: total={}, success={}, failure={}",
                rows.size(), valid.size(), errors.size());
        return StudentImportResultResponse.builder()
                .totalRows(rows.size())
                .successCount(valid.size())
                .failureCount(countFailedRows(errors))
                .errors(errors)
                .build();
    }

    private List<ImportRowErrorDto> validateRow(
            StudentImportRowDto row,
            Set<String> existingCodes,
            Map<String, Integer> fileCodeFirstRow,
            Map<String, ClassEntity> classes,
            Map<Long, Long> enrolledByClass,
            Map<Long, Integer> incomingByClass) {

        List<ImportRowErrorDto> errors = new ArrayList<>();
        String code = trim(row.getStudentCode());
        String name = trim(row.getFullName());

        if (!code.isEmpty()) {
            if (code.length() > 30) {
                errors.add(error(row, StudentExcelHelper.COL_STUDENT_CODE, "Mã học sinh không được vượt quá 30 ký tự."));
            } else {
                String upper = code.toUpperCase(Locale.ROOT);
                Integer first = fileCodeFirstRow.get(upper);
                if (first != null && first != row.getRowNumber()) {
                    errors.add(error(row, StudentExcelHelper.COL_STUDENT_CODE,
                            "Mã học sinh trùng trong file (dòng " + first + ")."));
                } else {
                    fileCodeFirstRow.putIfAbsent(upper, row.getRowNumber());
                }
                if (existingCodes.contains(upper)) {
                    errors.add(error(row, StudentExcelHelper.COL_STUDENT_CODE,
                            "Mã học sinh đã tồn tại trên hệ thống."));
                }
            }
        }

        if (name.isEmpty()) {
            errors.add(error(row, StudentExcelHelper.COL_FULL_NAME, "Họ và tên không được để trống."));
        } else if (name.length() > 150) {
            errors.add(error(row, StudentExcelHelper.COL_FULL_NAME, "Họ và tên không được vượt quá 150 ký tự."));
        }

        String status = trim(row.getStatus());
        if (!status.isEmpty() && !StudentExcelHelper.allowedStatuses().contains(status.toUpperCase(Locale.ROOT))) {
            errors.add(error(row, StudentExcelHelper.COL_STATUS,
                    "Trạng thái chỉ nhận ACTIVE, INACTIVE, GRADUATED, SUSPENDED."));
        }

        String email = trim(row.getEmail());
        if (!email.isEmpty()) {
            if (email.length() > 100) {
                errors.add(error(row, StudentExcelHelper.COL_EMAIL, "Email không được vượt quá 100 ký tự."));
            } else if (!EMAIL_PATTERN.matcher(email).matches()) {
                errors.add(error(row, StudentExcelHelper.COL_EMAIL, "Email không đúng định dạng."));
            }
        }

        String phone = trim(row.getPhone());
        if (!phone.isEmpty()) {
            if (phone.length() > 20 || !PHONE_PATTERN.matcher(phone).matches()) {
                errors.add(error(row, StudentExcelHelper.COL_PHONE, "Số điện thoại không hợp lệ."));
            }
        }

        String dobRaw = trim(row.getDateOfBirth());
        if (!dobRaw.isEmpty()) {
            LocalDate dob = StudentExcelHelper.parseDate(dobRaw);
            if (dob == null) {
                errors.add(error(row, StudentExcelHelper.COL_DOB, "Ngày sinh không đúng định dạng."));
            } else if (dob.isAfter(LocalDate.now())) {
                errors.add(error(row, StudentExcelHelper.COL_DOB, "Ngày sinh không được ở tương lai."));
            }
        }

        String parent = trim(row.getParentName());
        if (parent.length() > 100) {
            errors.add(error(row, StudentExcelHelper.COL_PARENT, "Phụ huynh không được vượt quá 100 ký tự."));
        }
        String address = trim(row.getAddress());
        if (address.length() > 255) {
            errors.add(error(row, StudentExcelHelper.COL_ADDRESS, "Địa chỉ không được vượt quá 255 ký tự."));
        }
        String note = trim(row.getNote());
        if (note.length() > 500) {
            errors.add(error(row, StudentExcelHelper.COL_NOTE, "Ghi chú không được vượt quá 500 ký tự."));
        }

        String classCode = trim(row.getClassCode());
        if (!classCode.isEmpty()) {
            ClassEntity clazz = classes.get(classCode.toUpperCase(Locale.ROOT));
            if (clazz == null) {
                errors.add(error(row, StudentExcelHelper.COL_CLASS_CODE, "Không tìm thấy lớp học."));
            } else if (!ACTIVE_CLASS_STATUSES.contains(clazz.getStatus())) {
                errors.add(error(row, StudentExcelHelper.COL_CLASS_CODE,
                        "Lớp không đang hoạt động (OPEN/ONGOING)."));
            } else if (clazz.getCapacity() != null && clazz.getCapacity() > 0) {
                long enrolled = enrolledByClass.computeIfAbsent(clazz.getId(),
                        id -> classStudentRepository.countByClassIdAndStatusAndIsDeleted(
                                id, ENROLLMENT_STATUS, PersistenceFlags.NOT_DELETED));
                int incoming = incomingByClass.getOrDefault(clazz.getId(), 0);
                if (enrolled + incoming >= clazz.getCapacity()) {
                    errors.add(error(row, StudentExcelHelper.COL_CLASS_CODE, "Lớp đã đủ sĩ số."));
                }
            }
        }

        if (errors.isEmpty()) {
            row.setStudentCode(code);
            row.setFullName(name);
            row.setStatus(resolveStatus(status));
            row.setEmail(email);
            row.setPhone(phone);
            row.setClassCode(classCode);
            row.setParentName(parent);
            row.setAddress(address);
            row.setNote(note);
            if (!classCode.isEmpty()) {
                ClassEntity clazz = classes.get(classCode.toUpperCase(Locale.ROOT));
                incomingByClass.merge(clazz.getId(), 1, Integer::sum);
            }
        }
        return errors;
    }

    private Set<String> loadExistingCodes(List<StudentImportRowDto> rows) {
        List<String> codes = rows.stream()
                .map(StudentImportRowDto::getStudentCode)
                .map(this::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> v.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < codes.size(); i += IN_CLAUSE_CHUNK) {
            List<String> chunk = codes.subList(i, Math.min(i + IN_CLAUSE_CHUNK, codes.size()));
            existing.addAll(studentRepository.findExistingCodesUpper(chunk));
        }
        return existing;
    }

    private Map<String, ClassEntity> loadClasses(List<StudentImportRowDto> rows) {
        List<String> codes = rows.stream()
                .map(StudentImportRowDto::getClassCode)
                .map(this::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> v.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        Map<String, ClassEntity> map = new HashMap<>();
        if (codes.isEmpty()) {
            return map;
        }
        for (int i = 0; i < codes.size(); i += IN_CLAUSE_CHUNK) {
            List<String> chunk = codes.subList(i, Math.min(i + IN_CLAUSE_CHUNK, codes.size()));
            for (ClassEntity clazz : classRepository.findByClassCodesUpperAndIsDeleted(
                    chunk, PersistenceFlags.NOT_DELETED)) {
                if (clazz.getClassCode() != null) {
                    map.put(clazz.getClassCode().toUpperCase(Locale.ROOT), clazz);
                }
            }
        }
        return map;
    }

    private byte[] readAndValidateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OracleBusinessException("FILE_EMPTY", "File tải lên không được để trống.");
        }
        String filename = file.getOriginalFilename();
        if (!StudentExcelHelper.isXlsxFilename(filename)) {
            throw new OracleBusinessException("FILE_TYPE_INVALID", "Chỉ chấp nhận file Excel .xlsx.");
        }
        if (!StudentExcelHelper.isXlsxContentType(file.getContentType())) {
            throw new OracleBusinessException("FILE_TYPE_INVALID", "Chỉ chấp nhận file Excel .xlsx.");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new OracleBusinessException("FILE_TOO_LARGE",
                    "File import không được vượt quá 5MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_IMPORT_BYTES) {
                throw new OracleBusinessException("FILE_TOO_LARGE",
                        "File import không được vượt quá 5MB.");
            }
            return bytes;
        } catch (IOException e) {
            throw new OracleBusinessException("EXCEL_READ_ERROR", "Không đọc được file Excel.", e);
        }
    }

    private ImportRowErrorDto error(StudentImportRowDto row, String column, String message) {
        return ImportRowErrorDto.builder()
                .rowNumber(row.getRowNumber())
                .columnName(column)
                .studentCode(trim(row.getStudentCode()))
                .errorMessage(message)
                .build();
    }

    private int countFailedRows(List<ImportRowErrorDto> errors) {
        return (int) errors.stream().map(ImportRowErrorDto::getRowNumber).distinct().count();
    }

    private String resolveStatus(String status) {
        String value = trim(status);
        if (value.isEmpty()) {
            return DEFAULT_STATUS;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
