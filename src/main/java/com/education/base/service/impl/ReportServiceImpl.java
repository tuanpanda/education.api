package com.education.base.service.impl;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.repository.ReportRepository;
import com.education.base.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter) {
        return reportRepository.getDashboardMetrics(filter);
    }
}
