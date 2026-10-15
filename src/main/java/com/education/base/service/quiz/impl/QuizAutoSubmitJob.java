package com.education.base.service.quiz.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GĐ4 - BR-27: mỗi phút tự nộp attempt IN_PROGRESS có {@code now > DEADLINE_AT + GRACE_SEC}. Tắt bằng
 * {@code app.quiz.jobs.enabled=false} ({@code APP_QUIZ_JOBS_ENABLED}). Mỗi attempt 1 transaction; lỗi 1 attempt không
 * chặn các attempt khác. Nộp lười ở API vẫn chạy khi job tắt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.quiz.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuizAutoSubmitJob {

    private final com.education.base.repository.QuizAttemptRepository attemptRepository;
    private final QuizGradingService gradingService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.quiz.jobs.auto-submit-delay-ms:60000}", initialDelay = 60_000)
    public void run() {
        LocalDateTime now = QuizRules.now(clock);
        List<Long> ids = attemptRepository.findExpiredInProgressIds(now.minusSeconds(QuizRules.graceSec()));
        int done = 0;
        for (Long id : ids) {
            try {
                gradingService.finish(id, true, now);
                done++;
            } catch (RuntimeException e) {
                log.error("Tự nộp attempt {} lỗi: {}", id, e.getMessage(), e);
            }
        }
        if (done > 0) {
            log.info("Tự nộp {} lượt làm bài quá hạn", done);
        }
    }
}
