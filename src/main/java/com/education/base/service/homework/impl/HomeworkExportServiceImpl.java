package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import com.education.base.dto.response.SubmissionRowDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.AuditService;
import com.education.base.service.FinanceExportService;
import com.education.base.service.homework.HomeworkExportService;
import com.education.base.service.homework.HomeworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** GĐ2 - xuất ma trận bài nộp ra Excel (.xlsx). Phạm vi lớp kiểm qua {@link HomeworkService#getSubmissionMatrix}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkExportServiceImpl implements HomeworkExportService {

    static final List<String> HEADERS = List.of("STT", "Mã HS", "Họ tên", "Trạng thái", "Số lần nộp", "Nộp lúc",
            "Nộp muộn", "Điểm", "Thang điểm", "Chấm lúc", "Số file");
    static final String SHEET = "Bai tap";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HomeworkService homeworkService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportAssignment(Long assignmentId) {
        SubmissionMatrixDto m = homeworkService.getSubmissionMatrix(assignmentId);
        byte[] content;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS.get(i));
                c.setCellStyle(bold);
            }
            int r = 1;
            for (SubmissionRowDto row : m.getRows()) {
                Row x = sheet.createRow(r);
                x.createCell(0).setCellValue(r);
                x.createCell(1).setCellValue(nz(row.getStudentCode()));
                x.createCell(2).setCellValue(nz(row.getStudentName()));
                x.createCell(3).setCellValue(stateLabel(row.getState()));
                x.createCell(4).setCellValue(row.getAttemptCount() == null ? 0 : row.getAttemptCount());
                x.createCell(5).setCellValue(time(row.getSubmittedAt()));
                x.createCell(6).setCellValue(Boolean.TRUE.equals(row.getLate()) ? "Có" : "");
                if (row.getScore() != null) {
                    x.createCell(7).setCellValue(row.getScore().doubleValue());
                } else {
                    x.createCell(7).setBlank();
                }
                x.createCell(8).setCellValue(m.getAssignment().getMaxScore() == null ? 0
                        : m.getAssignment().getMaxScore().doubleValue());
                x.createCell(9).setCellValue(time(row.getGradedAt()));
                x.createCell(10).setCellValue(row.getFileCount() == null ? 0 : row.getFileCount());
                r++;
            }
            for (int i = 0; i < HEADERS.size(); i++) {
                sheet.setColumnWidth(i, i == 2 ? 30 * 256 : 16 * 256);
            }
            wb.write(out);
            content = out.toByteArray();
        } catch (IOException e) {
            log.error("Lỗi xuất Excel bài tập {}: {}", assignmentId, e.getMessage(), e);
            throw new OracleBusinessException("EXPORT_ERROR", "Không tạo được file Excel.");
        }
        String fileName = "bai_tap_" + assignmentId + "_" + HomeworkRules.now(clock).format(FILE_DATE) + ".xlsx";
        auditService.success(AuditActions.HOMEWORK_EXPORTED, AuditActions.RESOURCE_ASSIGNMENT, assignmentId,
                Map.of("rows", m.getRows().size()));
        return new ExportFileDto(fileName, FinanceExportService.XLSX_CONTENT_TYPE, content);
    }

    static String stateLabel(String state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case "NOT_SUBMITTED" -> "Chưa nộp";
            case "MISSING" -> "Thiếu bài";
            case "SUBMITTED" -> "Đã nộp";
            case "LATE" -> "Nộp muộn";
            case "RETURNED" -> "Trả lại";
            case "GRADED" -> "Đã chấm";
            default -> state;
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String time(LocalDateTime t) {
        return t == null ? "" : t.format(CELL_TIME);
    }
}
