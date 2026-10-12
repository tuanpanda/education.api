package com.education.base.repository;

import com.education.base.entity.FileModuleSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code SYS_FILE_MODULE_SETTINGS} - Luồng B. */
@Repository
public interface FileModuleSettingRepository extends JpaRepository<FileModuleSettingEntity, String> {
}
