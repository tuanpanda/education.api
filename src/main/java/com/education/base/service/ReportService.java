package com.education.base.service;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;

public interface ReportService {

    DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter);
}
