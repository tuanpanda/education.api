package com.education.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bật {@code @Scheduled} (job dọn thùng rác {@code FileRetentionJob}) và executor 1 luồng cho đối soát file chạy nền.
 * Hiện chạy 1 instance nên chưa cần ShedLock; khi scale nhiều instance phải thêm khoá phân tán.
 * <p>
 * Luồng B.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    public static final String FILE_MAINTENANCE_EXECUTOR = "fileMaintenanceExecutor";

    @Bean(name = FILE_MAINTENANCE_EXECUTOR)
    public TaskExecutor fileMaintenanceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("file-maint-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
