package com.education.base.repository.spec;

import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.entity.TuitionFeeEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B8: danh sách khoản phí không còn ẩn khoản phí của học sinh không {@code ACTIVE}.
 */
class TuitionFeeSpecificationsTest {

    private Root<TuitionFeeEntity> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> studentStatus;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class, RETURNS_MOCKS);
        query = mock(CriteriaQuery.class, RETURNS_MOCKS);
        cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        Join<Object, Object> student = mock(Join.class, RETURNS_MOCKS);
        studentStatus = mock(Path.class);
        when(root.join("student", JoinType.INNER)).thenReturn((Join) student);
        when(student.get("status")).thenReturn(studentStatus);
    }

    @Test
    void defaultFilter_doesNotRestrictStudentStatus() {
        TuitionFeeSpecifications.fromFilter(new TuitionFeeFilterRequest()).toPredicate(root, query, cb);

        verify(cb, never()).equal(eq(studentStatus), anyString());
        verify(cb, never()).equal(eq(studentStatus), (Object) any());
    }

    @Test
    void studentStatusFilter_restrictsToChosenStatus() {
        TuitionFeeFilterRequest filter = new TuitionFeeFilterRequest();
        filter.setStudentStatus("INACTIVE");

        TuitionFeeSpecifications.fromFilter(filter).toPredicate(root, query, cb);

        verify(cb).equal(studentStatus, "INACTIVE");
        verify(cb, never()).equal(studentStatus, "ACTIVE");
    }
}
