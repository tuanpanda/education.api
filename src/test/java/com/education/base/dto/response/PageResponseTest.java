package com.education.base.dto.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void of_computesTotalPagesRoundingUp() {
        assertThat(PageResponse.of(List.of("a"), 1, 10, 21).getTotalPages()).isEqualTo(3);
        assertThat(PageResponse.of(List.of("a"), 1, 10, 20).getTotalPages()).isEqualTo(2);
        assertThat(PageResponse.of(List.of("a"), 1, 10, 1).getTotalPages()).isEqualTo(1);
    }

    @Test
    void of_emptyResult_hasZeroTotalPages() {
        PageResponse<String> page = PageResponse.of(List.of(), 1, 20, 0);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalRows()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    @Test
    void of_nullContent_becomesEmptyList() {
        assertThat(PageResponse.of(null, 1, 20, 0).getContent()).isEmpty();
    }

    @Test
    void of_zeroPageSize_isClampedToOneToAvoidDivisionByZero() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 1, 0, 5);

        assertThat(page.getPageSize()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(5);
    }
}
