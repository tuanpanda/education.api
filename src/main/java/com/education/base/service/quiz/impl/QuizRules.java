package com.education.base.service.quiz.impl;

import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizConstants.AttemptStatus;
import com.education.base.common.quiz.QuizConstants.QuestionType;
import com.education.base.common.quiz.QuizConstants.QuizStatus;
import com.education.base.common.quiz.QuizConstants.ResultMode;
import com.education.base.common.quiz.QuizConstants.ScorePolicy;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** GĐ4 - luật thời gian / chấm / công bố (thuần, không truy cập DB). Giờ luôn theo {@link QuizConstants#ZONE}. */
public final class QuizRules {

    private QuizRules() {
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(QuizConstants.ZONE)).withNano(0);
    }

    public static boolean flag(Integer v) {
        return v != null && v == 1;
    }

    public static int flag(Boolean v) {
        return Boolean.TRUE.equals(v) ? 1 : 0;
    }

    /** Đang mở: PUBLISHED và OPEN_AT <= now < CLOSE_AT. */
    public static boolean isOpen(QuizEntity q, LocalDateTime now) {
        return QuizStatus.PUBLISHED.equals(q.getStatus()) && !now.isBefore(q.getOpenAt()) && now.isBefore(q.getCloseAt());
    }

    /** Đề đã từng mở (CLOSED đóng trước OPEN_AT thì không bao giờ hiện ở cổng - AC-13i). */
    public static boolean everOpened(QuizEntity q, LocalDateTime now) {
        if (QuizStatus.PUBLISHED.equals(q.getStatus())) {
            return !now.isBefore(q.getOpenAt());
        }
        if (QuizStatus.CLOSED.equals(q.getStatus())) {
            LocalDateTime end = q.getClosedAt() != null ? q.getClosedAt() : q.getCloseAt();
            return !end.isBefore(q.getOpenAt()) && !now.isBefore(q.getOpenAt());
        }
        return false;
    }

    /** Hiện ở danh sách cổng (kể cả UPCOMING). */
    public static boolean portalListed(QuizEntity q, LocalDateTime now) {
        if (q.getIsDeleted() != null && q.getIsDeleted() != 0) {
            return false;
        }
        if (QuizStatus.PUBLISHED.equals(q.getStatus())) {
            return true;
        }
        return QuizStatus.CLOSED.equals(q.getStatus()) && everOpened(q, now);
    }

    public static LocalDateTime deadline(LocalDateTime startedAt, QuizEntity q) {
        if (q.getDurationMin() == null) {
            return q.getCloseAt();
        }
        LocalDateTime d = startedAt.plusMinutes(q.getDurationMin());
        return d.isBefore(q.getCloseAt()) ? d : q.getCloseAt();
    }

    public static long remainingSeconds(LocalDateTime deadlineAt, LocalDateTime now) {
        return Math.max(0, Duration.between(now, deadlineAt).getSeconds());
    }

    /** Ân hạn mạng (giây), cấu hình {@code app.quiz.grace-sec} (mặc định 30, Q14) - đặt bởi {@link QuizSettings}. */
    private static volatile int graceSec = QuizConstants.GRACE_SEC;

    static void setGraceSec(int seconds) {
        graceSec = Math.max(0, seconds);
    }

    public static int graceSec() {
        return graceSec;
    }

    /** Quá hạn + ân hạn ({@code now > DEADLINE_AT + GRACE_SEC}): lưu bị từ chối, job / nộp lười chuyển AUTO_SUBMITTED. */
    public static boolean overdue(QuizAttemptEntity a, LocalDateTime now) {
        return now.isAfter(a.getDeadlineAt().plusSeconds(graceSec));
    }

    public static boolean finished(QuizAttemptEntity a) {
        return a != null && AttemptStatus.FINISHED.contains(a.getStatus());
    }

    public static boolean afterClose(QuizEntity q, LocalDateTime now) {
        return !now.isBefore(q.getCloseAt());
    }

    /** Được xem điểm (BR-30). */
    public static boolean scoreReleased(QuizEntity q, LocalDateTime now) {
        String mode = q.getResultMode() == null ? ResultMode.AFTER_CLOSE : q.getResultMode();
        return switch (mode) {
            case ResultMode.IMMEDIATE -> true;
            case ResultMode.MANUAL -> q.getResultsReleasedAt() != null;
            default -> afterClose(q, now);
        };
    }

    /** Được xem đáp án/giải thích/đúng-sai (BR-30a): đã được xem điểm VÀ now >= CLOSE_AT. */
    public static boolean answersReleased(QuizEntity q, LocalDateTime now) {
        return scoreReleased(q, now) && afterClose(q, now);
    }

    public static BigDecimal score10(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return null;
        }
        if (maxScore == null || maxScore.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal v = score.multiply(BigDecimal.valueOf(QuizConstants.SCALE10)).divide(maxScore, 2, RoundingMode.HALF_UP);
        BigDecimal ten = BigDecimal.TEN.setScale(2);
        if (v.signum() < 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return v.compareTo(ten) > 0 ? ten : v;
    }

    public static boolean isStep(BigDecimal v) {
        return v != null && v.remainder(QuizConstants.SCORE_STEP).signum() == 0;
    }

    /** SHORT_TEXT: NFC, trim, gộp khoảng trắng, không phân biệt hoa thường, CÓ phân biệt dấu. */
    public static String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
        return n.toLowerCase(Locale.forLanguageTag("vi"));
    }

    public static boolean isAuto(String type) {
        return !QuestionType.ESSAY.equals(type);
    }

    /** Chấm tự động: trả {@code null} cho ESSAY (GV chấm). */
    public static Boolean autoCorrect(QuizJson.Snapshot s, Collection<Long> selected, String text) {
        if (QuestionType.ESSAY.equals(s.type())) {
            return null;
        }
        if (QuestionType.SHORT_TEXT.equals(s.type())) {
            String a = normalizeText(text);
            if (a.isEmpty()) {
                return false;
            }
            return s.acceptedAnswers() != null && s.acceptedAnswers().stream().anyMatch(x -> normalizeText(x).equals(a));
        }
        Set<Long> chosen = selected == null ? Set.of() : new HashSet<>(selected);
        if (chosen.isEmpty()) {
            return false;
        }
        Set<Long> correct = new HashSet<>();
        for (QuizJson.SnapOption o : s.options()) {
            if (o.correct()) {
                correct.add(o.id());
            }
        }
        return chosen.equals(correct);
    }

    /** Điểm đề của HS theo SCORE_POLICY trên các attempt đã kết thúc (theo ATTEMPT_NO tăng). */
    public static BigDecimal finalScore(String policy, List<QuizAttemptEntity> attemptsAsc) {
        List<BigDecimal> scores = attemptsAsc.stream().filter(QuizRules::finished).map(QuizAttemptEntity::getScore)
                .filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        String p = policy == null ? ScorePolicy.HIGHEST : policy;
        return switch (p) {
            case ScorePolicy.LAST -> scores.get(scores.size() - 1);
            case ScorePolicy.AVERAGE -> scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
            default -> scores.stream().max(BigDecimal::compareTo).orElse(null);
        };
    }

    public static String preview(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replaceAll("\\s+", " ").strip();
        return text.length() > QuizConstants.PREVIEW_MAX ? text.substring(0, QuizConstants.PREVIEW_MAX) : text;
    }
}
