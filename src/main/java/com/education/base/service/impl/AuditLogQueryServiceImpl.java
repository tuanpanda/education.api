package com.education.base.service.impl;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.AuditLogEntity;
import com.education.base.repository.AuditLogRepository;
import com.education.base.repository.spec.AuditLogSpecifications;
import com.education.base.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponseDto> search(AuditLogFilterRequest filter) {
        AuditLogFilterRequest criteria = filter == null ? new AuditLogFilterRequest() : filter;
        Page<AuditLogEntity> page = auditLogRepository.findAll(
                AuditLogSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePage() - 1, criteria.resolveSize(),
                        Sort.by(Sort.Order.desc("eventTime"), Sort.Order.desc("id"))));
        return PageResponse.of(page.getContent().stream().map(AuditLogQueryServiceImpl::toDto).toList(),
                criteria.resolvePage(), criteria.resolveSize(), page.getTotalElements());
    }

    static AuditLogResponseDto toDto(AuditLogEntity entity) {
        return AuditLogResponseDto.builder()
                .id(entity.getId())
                .eventTime(entity.getEventTime())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .userType(entity.getUserType())
                .action(entity.getAction())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .ip(entity.getIp())
                .userAgent(entity.getUserAgent())
                .result(entity.getResult())
                .detail(entity.getDetail())
                .build();
    }
}
