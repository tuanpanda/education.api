package com.education.base.service.lesson.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.lesson.LessonConstants;
import com.education.base.common.lesson.LessonConstants.LessonStatus;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.LessonEntity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Luật giờ mở bài GĐ3 (docs/portal-p3-api.md §1), thuần - không truy cập DB.
 * <ul>
 *   <li>{@code effectiveAvailableFrom} = AVAILABLE_FROM nếu có; else buổi học (chưa CANCELLED/xoá) = ngày + giờ bắt đầu
 *       (giờ null/hỏng -> 00:00); else PUBLISHED_AT.</li>
 *   <li>{@code availableNow} = PUBLISHED, chưa xoá, effectiveAvailableFrom &lt;= now.</li>
 * </ul>
 */
public final class LessonRules {

    static final String SESSION_CANCELLED = "CANCELLED";

    private LessonRules() {
    }

    /** "Bây giờ" theo giờ server Việt Nam. */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(LessonConstants.ZONE));
    }

    static boolean deleted(Integer flag) {
        return flag != null && flag != PersistenceFlags.NOT_DELETED;
    }

    /** Buổi còn dùng được để tính giờ mở (tồn tại, chưa xoá, chưa huỷ, có ngày). */
    static boolean usableSession(ClassSessionEntity s) {
        return s != null && !deleted(s.getIsDeleted()) && !SESSION_CANCELLED.equals(s.getStatus())
                && s.getSessionDate() != null;
    }

    public static LocalDateTime effectiveAvailableFrom(LessonEntity l, ClassSessionEntity session) {
        if (l == null) {
            return null;
        }
        if (l.getAvailableFrom() != null) {
            return l.getAvailableFrom();
        }
        if (l.getSessionId() != null && usableSession(session)
                && java.util.Objects.equals(session.getId(), l.getSessionId())) {
            return session.getSessionDate().atTime(parseTime(session.getStartTime()));
        }
        return l.getPublishedAt();
    }

    public static boolean availableNow(LessonEntity l, ClassSessionEntity session, LocalDateTime now) {
        if (l == null || deleted(l.getIsDeleted()) || !LessonStatus.PUBLISHED.equals(l.getStatus())) {
            return false;
        }
        LocalDateTime from = effectiveAvailableFrom(l, session);
        return from != null && !from.isAfter(now);
    }

    static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return LocalTime.MIDNIGHT;
        }
    }

    static int percent(int done, int total) {
        return total <= 0 ? 0 : (int) Math.round(100.0 * done / total);
    }
}
