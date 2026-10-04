package com.education.base.repository.spec;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.entity.AuditLogEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogSpecificationsTest {

    private Root<AuditLogEntity> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class, RETURNS_MOCKS);
        query = mock(CriteriaQuery.class, RETURNS_MOCKS);
        cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
    }

    @SuppressWarnings("unchecked")
    private <T> Path<T> path(String name) {
        Path<T> path = mock(Path.class, RETURNS_MOCKS);
        when(root.<T>get(name)).thenReturn(path);
        return path;
    }

    @Test
    void emptyFilter_addsNoPredicate() {
        AuditLogSpecifications.fromFilter(null).toPredicate(root, query, cb);

        verify(cb, never()).equal(any(), (Object) any());
        verify(cb, never()).like(any(), anyString(), anyChar());
    }

    @Test
    @SuppressWarnings("unchecked")
    void filters_areNormalizedAndDateRangeIsInclusiveOfToDate() {
        Path<LocalDateTime> eventTime = path("eventTime");
        Path<String> action = path("action");
        Path<String> result = path("result");
        Path<String> ip = path("ip");
        Path<String> resourceId = path("resourceId");
        Path<Long> userId = path("userId");
        Path<String> username = path("username");
        Expression<String> lowerUsername = mock(Expression.class);
        when(cb.lower(username)).thenReturn(lowerUsername);

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setFromDate(LocalDate.of(2026, 10, 1));
        filter.setToDate(LocalDate.of(2026, 10, 4));
        filter.setAction(" login_failed ");
        filter.setResult("failure");
        filter.setIp("192.168.1.");
        filter.setResourceId(" 21 ");
        filter.setUserId(7L);
        filter.setUsername("Thu_Ngan%");

        AuditLogSpecifications.fromFilter(filter).toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(eventTime, LocalDateTime.of(2026, 10, 1, 0, 0));
        verify(cb).lessThan(eventTime, LocalDateTime.of(2026, 10, 5, 0, 0));
        verify(cb).equal(action, "LOGIN_FAILED");
        verify(cb).equal(result, "FAILURE");
        verify(cb).equal(resourceId, "21");
        verify(cb).equal(userId, 7L);
        verify(cb).like(eq(ip), eq("192.168.1.%"), eq('\\'));
        verify(cb).like(eq(lowerUsername), eq("%thu\\_ngan\\%%"), eq('\\'));
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(AuditLogSpecifications.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
        assertThat(AuditLogSpecifications.contains(" Nhầm ")).isEqualTo("%nhầm%");
    }
}
