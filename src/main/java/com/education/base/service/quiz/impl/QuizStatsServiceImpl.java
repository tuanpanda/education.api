package com.education.base.service.quiz.impl;

import com.education.base.audit.AuditActions;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.QuizAttemptMatrixDto;
import com.education.base.dto.response.QuizStatsDto;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.QuizAnswerRepository;
import com.education.base.repository.QuizAttemptRepository;
import com.education.base.service.AuditService;
import com.education.base.service.FinanceExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** GĐ4 - thống kê đề + xuất Excel ma trận điểm. Phạm vi lớp kiểm qua {@link QuizServiceImpl}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizStatsServiceImpl {

    static final List<String> HEADERS = List.of("STT", "Mã HS", "Họ tên", "Trạng thái", "Số lượt", "Điểm cao nhất",
            "Điểm đề", "Điểm thang 10", "Rời màn hình", "Cảnh báo");
    static final String[] BUCKETS = {"0-1", "1-2", "2-3", "3-4", "4-5", "5-6", "6-7", "7-8", "8-9", "9-10"};
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final QuizServiceImpl quizService;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizJson json;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public QuizStatsDto stats(Long quizId) {
        QuizAttemptMatrixDto m = quizService.matrix(quizId, null);
        QuizEntity q = quizService.requireQuiz(quizId);
        List<BigDecimal> finals = m.getRows().stream().map(QuizAttemptMatrixDto.Row::getFinalScore10)
                .filter(Objects::nonNull).toList();
        long[] dist = new long[BUCKETS.length];
        for (BigDecimal s : finals) {
            int b = Math.min(BUCKETS.length - 1, s.setScale(0, RoundingMode.FLOOR).intValue());
            dist[Math.max(0, b)]++;
        }
        List<QuizStatsDto.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKETS.length; i++) {
            buckets.add(QuizStatsDto.Bucket.builder().bucket(BUCKETS[i]).count(dist[i]).build());
        }
        BigDecimal passRate = null;
        if (q.getPassScore() != null && !finals.isEmpty()) {
            long passed = m.getRows().stream().filter(r -> r.getFinalScore() != null
                    && r.getFinalScore().compareTo(q.getPassScore()) >= 0).count();
            passRate = BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(finals.size()), 4, RoundingMode.HALF_UP);
        }
        List<QuizAttemptEntity> finished = attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(quizId).stream()
                .filter(QuizRules::finished).toList();
        List<QuizStatsDto.QuestionStat> questions = questionStats(finished);
        return QuizStatsDto.builder().submittedCount((long) finals.size())
                .avgScore10(finals.isEmpty() ? null : finals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(finals.size()), 2, RoundingMode.HALF_UP))
                .maxScore10(finals.stream().max(BigDecimal::compareTo).orElse(null))
                .minScore10(finals.stream().min(BigDecimal::compareTo).orElse(null))
                .passRate(passRate).distribution(buckets).questions(questions).build();
    }

    private List<QuizStatsDto.QuestionStat> questionStats(List<QuizAttemptEntity> finished) {
        if (finished.isEmpty()) {
            return List.of();
        }
        List<QuizAnswerEntity> answers = new ArrayList<>();
        for (List<Long> chunk : QuestionServiceImpl.chunks(finished.stream().map(QuizAttemptEntity::getId).toList())) {
            answers.addAll(answerRepository.findByAttemptIdIn(chunk));
        }
        Map<Long, List<QuizAnswerEntity>> byQuestion = new LinkedHashMap<>();
        answers.forEach(a -> byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a));
        List<QuizStatsDto.QuestionStat> out = new ArrayList<>();
        for (Map.Entry<Long, List<QuizAnswerEntity>> e : byQuestion.entrySet()) {
            QuizJson.Snapshot s = json.snapshot(e.getValue().get(0).getSnapshot());
            long graded = e.getValue().stream().filter(a -> a.getIsCorrect() != null).count();
            long correct = e.getValue().stream().filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1).count();
            Map<Long, Long> picks = new HashMap<>();
            for (QuizAnswerEntity a : e.getValue()) {
                for (Long id : json.longs(a.getSelectedOptionIds())) {
                    picks.merge(id, 1L, Long::sum);
                }
            }
            List<QuizStatsDto.OptionStat> opts = s == null || s.options() == null ? List.of() : s.options().stream()
                    .map(o -> QuizStatsDto.OptionStat.builder().optionId(o.id()).content(QuizRules.preview(o.content()))
                            .isCorrect(o.correct()).pickCount(picks.getOrDefault(o.id(), 0L)).build()).toList();
            out.add(QuizStatsDto.QuestionStat.builder().questionId(e.getKey())
                    .contentPreview(s == null ? null : QuizRules.preview(s.content())).type(s == null ? null : s.type())
                    .correctRate(graded == 0 ? null : BigDecimal.valueOf(correct)
                            .divide(BigDecimal.valueOf(graded), 4, RoundingMode.HALF_UP))
                    .optionStats(opts).build());
        }
        return out;
    }

    /** 1 dòng / HS (kể cả chưa làm), cột tổng hợp + cột điểm từng câu của lượt kết thúc gần nhất. */
    @Transactional(rollbackFor = Exception.class)
    public ExportFileDto export(Long quizId) {
        QuizAttemptMatrixDto m = quizService.matrix(quizId, null);
        Map<Long, QuizAttemptEntity> lastFinished = new HashMap<>();
        for (QuizAttemptEntity a : attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(quizId)) {
            if (QuizRules.finished(a)) {
                lastFinished.put(a.getStudentId(), a);
            }
        }
        Map<Long, Map<Long, BigDecimal>> points = new HashMap<>();
        Map<Long, Integer> columns = new LinkedHashMap<>();
        List<Long> attemptIds = lastFinished.values().stream().map(QuizAttemptEntity::getId).toList();
        Map<Long, Long> studentOfAttempt = new HashMap<>();
        lastFinished.forEach((sid, a) -> studentOfAttempt.put(a.getId(), sid));
        for (List<Long> chunk : QuestionServiceImpl.chunks(attemptIds)) {
            List<QuizAnswerEntity> answers = new ArrayList<>(answerRepository.findByAttemptIdIn(chunk));
            answers.sort(java.util.Comparator.comparing(QuizAnswerEntity::getQuestionId));
            for (QuizAnswerEntity ans : answers) {
                columns.putIfAbsent(ans.getQuestionId(), columns.size());
                points.computeIfAbsent(studentOfAttempt.get(ans.getAttemptId()), k -> new HashMap<>())
                        .put(ans.getQuestionId(), ans.getPointsAwarded());
            }
        }
        byte[] content;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Kiem tra");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row h = sheet.createRow(0);
            List<String> headers = new ArrayList<>(HEADERS);
            columns.keySet().forEach(qid -> headers.add("Câu #" + qid));
            for (int i = 0; i < headers.size(); i++) {
                h.createCell(i).setCellValue(headers.get(i));
                h.getCell(i).setCellStyle(bold);
                sheet.setColumnWidth(i, (i == 2 ? 30 : 14) * 256);
            }
            int r = 1;
            for (QuizAttemptMatrixDto.Row row : m.getRows()) {
                Row x = sheet.createRow(r);
                x.createCell(0).setCellValue(r);
                x.createCell(1).setCellValue(nz(row.getStudentCode()));
                x.createCell(2).setCellValue(nz(row.getFullName()));
                x.createCell(3).setCellValue(statusLabel(row.getStatus()));
                x.createCell(4).setCellValue(row.getAttemptsUsed() == null ? 0 : row.getAttemptsUsed());
                num(x, 5, row.getBestScore());
                num(x, 6, row.getFinalScore());
                num(x, 7, row.getFinalScore10());
                x.createCell(8).setCellValue(row.getFocusLostCount() == null ? 0 : row.getFocusLostCount());
                x.createCell(9).setCellValue(String.join(", ", row.getFlags() == null ? List.of() : row.getFlags()));
                Map<Long, BigDecimal> p = points.getOrDefault(row.getStudentId(), Map.of());
                for (Map.Entry<Long, Integer> c : columns.entrySet()) {
                    num(x, HEADERS.size() + c.getValue(), p.get(c.getKey()));
                }
                r++;
            }
            wb.write(out);
            content = out.toByteArray();
        } catch (IOException e) {
            log.error("Lỗi xuất Excel đề {}: {}", quizId, e.getMessage(), e);
            throw new OracleBusinessException("EXPORT_ERROR", "Không tạo được file Excel.");
        }
        auditService.success(AuditActions.QUIZ_EXPORTED, AuditActions.RESOURCE_QUIZ, quizId, Map.of("rows", m.getRows().size()));
        String name = "kiem_tra_" + quizId + "_" + QuizRules.now(clock).format(FILE_DATE) + ".xlsx";
        return new ExportFileDto(name, FinanceExportService.XLSX_CONTENT_TYPE, content);
    }

    static String statusLabel(String s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case "NOT_STARTED" -> "Chưa làm";
            case "IN_PROGRESS" -> "Đang làm";
            case "SUBMITTED" -> "Đã nộp";
            case "AUTO_SUBMITTED" -> "Tự nộp";
            case "GRADED" -> "Đã chấm";
            default -> s;
        };
    }

    private static void num(Row x, int col, BigDecimal v) {
        if (v == null) {
            x.createCell(col).setBlank();
        } else {
            x.createCell(col).setCellValue(v.doubleValue());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
