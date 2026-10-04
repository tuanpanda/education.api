package com.education.base.service.impl;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.AuditLogEntity;
import com.education.base.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogQueryServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void search_newestFirst_withClampedPaging() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogEntity entity = AuditLogEntity.builder()
                .id(5L).eventTime(LocalDateTime.of(2026, 10, 4, 8, 0)).userId(1L).username("admin")
                .action("LOGIN_SUCCESS").result("SUCCESS").ip("10.0.0.1").userAgent("UA").detail("{}").build();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(entity), invocation.getArgument(1), 401));
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setPage(3);
        filter.setSize(1000);

        PageResponse<AuditLogResponseDto> page = new AuditLogQueryServiceImpl(repository).search(filter);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AuditLogFilterRequest.MAX_SIZE);
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("eventTime"), Sort.Order.desc("id"));
        assertThat(page.getPageNo()).isEqualTo(3);
        assertThat(page.getTotalRows()).isEqualTo(401);
        assertThat(page.getTotalPages()).isEqualTo(3);
        AuditLogResponseDto dto = page.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getAction()).isEqualTo("LOGIN_SUCCESS");
        assertThat(dto.getUserAgent()).isEqualTo("UA");
    }
}
