package com.education.base.service.lesson.impl;

import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.LessonEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LessonRulesTest {

    static final LocalDateTime NOW = LessonFakeDb.NOW;

    static LessonEntity lesson(String status, LocalDateTime availableFrom, Long sessionId, LocalDateTime publishedAt) {
        return LessonEntity.builder().id(1L).status(status).availableFrom(availableFrom).sessionId(sessionId)
                .publishedAt(publishedAt).isDeleted(0).build();
    }

    static ClassSessionEntity session(long id, LocalDate date, String start, String status) {
        return ClassSessionEntity.builder().id(id).classId(7L).sessionDate(date).startTime(start).status(status)
                .isDeleted(0).build();
    }

    @Test
    void now_usesVietnamZoneRegardlessOfClockZone() {
        Clock utc = Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneOffset.UTC);
        assertThat(LessonRules.now(utc)).isEqualTo(NOW);
    }

    @Test
    void availableFrom_futureAndPast() {
        assertThat(LessonRules.availableNow(lesson("PUBLISHED", NOW.plusMinutes(1), null, NOW.minusDays(2)), null, NOW))
                .isFalse();
        assertThat(LessonRules.availableNow(lesson("PUBLISHED", NOW, null, NOW.minusDays(2)), null, NOW)).isTrue();
        assertThat(LessonRules.availableNow(lesson("PUBLISHED", NOW.minusHours(1), null, null), null, NOW)).isTrue();
    }

    @Test
    void draftOrDeleted_neverAvailable() {
        assertThat(LessonRules.availableNow(lesson("DRAFT", NOW.minusDays(1), null, NOW.minusDays(1)), null, NOW))
                .isFalse();
        LessonEntity deleted = lesson("PUBLISHED", NOW.minusDays(1), null, NOW.minusDays(1));
        deleted.setIsDeleted(1);
        assertThat(LessonRules.availableNow(deleted, null, NOW)).isFalse();
        assertThat(LessonRules.effectiveAvailableFrom(lesson("DRAFT", null, null, null), null)).isNull();
    }

    @Test
    void session_opensAtSessionStart_andAvailableFromWins() {
        ClassSessionEntity s = session(5L, NOW.toLocalDate(), "09:30", "SCHEDULED");
        LessonEntity l = lesson("PUBLISHED", null, 5L, NOW.minusDays(3));
        assertThat(LessonRules.effectiveAvailableFrom(l, s)).isEqualTo(NOW.withHour(9).withMinute(30));
        assertThat(LessonRules.availableNow(l, s, NOW)).isTrue();

        ClassSessionEntity later = session(6L, NOW.toLocalDate(), "14:00", "SCHEDULED");
        LessonEntity l2 = lesson("PUBLISHED", null, 6L, NOW.minusDays(3));
        assertThat(LessonRules.availableNow(l2, later, NOW)).isFalse();

        LessonEntity explicit = lesson("PUBLISHED", NOW.minusHours(2), 6L, NOW.minusDays(3));
        assertThat(LessonRules.availableNow(explicit, later, NOW)).isTrue();
    }

    @Test
    void session_nullOrBadStartTime_isMidnight() {
        ClassSessionEntity s = session(5L, NOW.toLocalDate().plusDays(1), null, "SCHEDULED");
        assertThat(LessonRules.effectiveAvailableFrom(lesson("PUBLISHED", null, 5L, NOW.minusDays(3)), s))
                .isEqualTo(NOW.toLocalDate().plusDays(1).atStartOfDay());
        ClassSessionEntity bad = session(5L, NOW.toLocalDate(), "9h", "SCHEDULED");
        assertThat(LessonRules.effectiveAvailableFrom(lesson("PUBLISHED", null, 5L, NOW.minusDays(3)), bad))
                .isEqualTo(NOW.toLocalDate().atStartOfDay());
    }

    @Test
    void cancelledSession_fallsBackToPublishedAt() {
        ClassSessionEntity cancelled = session(5L, NOW.toLocalDate().plusDays(7), "08:00", "CANCELLED");
        LessonEntity l = lesson("PUBLISHED", null, 5L, NOW.minusDays(1));
        assertThat(LessonRules.effectiveAvailableFrom(l, cancelled)).isEqualTo(NOW.minusDays(1));
        assertThat(LessonRules.availableNow(l, cancelled, NOW)).isTrue();
        // buổi không còn (null) cũng theo PUBLISHED_AT
        assertThat(LessonRules.effectiveAvailableFrom(l, null)).isEqualTo(NOW.minusDays(1));
    }

    @Test
    void percent_rounds_andZeroDenominator() {
        assertThat(LessonRules.percent(1, 3)).isEqualTo(33);
        assertThat(LessonRules.percent(2, 3)).isEqualTo(67);
        assertThat(LessonRules.percent(0, 0)).isZero();
    }
}
