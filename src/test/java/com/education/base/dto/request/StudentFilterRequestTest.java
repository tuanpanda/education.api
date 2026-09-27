package com.education.base.dto.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentFilterRequestTest {

    @Test
    void newInstance_usesDefaultPaging() {
        StudentFilterRequest filter = new StudentFilterRequest();

        assertThat(filter.resolvePageNo()).isEqualTo(1);
        assertThat(filter.resolvePageSize()).isEqualTo(20);
    }

    @Test
    void resolvePageNo_normalizesNullAndNonPositive() {
        StudentFilterRequest filter = new StudentFilterRequest();

        filter.setPageNo(null);
        assertThat(filter.resolvePageNo()).isEqualTo(1);

        filter.setPageNo(0);
        assertThat(filter.resolvePageNo()).isEqualTo(1);

        filter.setPageNo(-5);
        assertThat(filter.resolvePageNo()).isEqualTo(1);

        filter.setPageNo(4);
        assertThat(filter.resolvePageNo()).isEqualTo(4);
    }

    @Test
    void resolvePageSize_clampsToUpperBound() {
        StudentFilterRequest filter = new StudentFilterRequest();

        filter.setPageSize(null);
        assertThat(filter.resolvePageSize()).isEqualTo(20);

        filter.setPageSize(0);
        assertThat(filter.resolvePageSize()).isEqualTo(20);

        filter.setPageSize(50);
        assertThat(filter.resolvePageSize()).isEqualTo(50);

        filter.setPageSize(9999);
        assertThat(filter.resolvePageSize()).isEqualTo(200);
    }
}
