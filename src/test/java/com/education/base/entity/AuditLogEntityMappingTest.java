package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/** Cùng quy ước với {@link EntityMappingConventionTest} cho bảng nhật ký (append-only). */
class AuditLogEntityMappingTest {

    @Test
    void mapsUppercaseTableSequenceAndColumns() throws Exception {
        assertThat(AuditLogEntity.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(AuditLogEntity.class.getAnnotation(Table.class).name()).isEqualTo("SYS_AUDIT_LOGS");

        SequenceGenerator generator = AuditLogEntity.class.getDeclaredField("id")
                .getAnnotation(SequenceGenerator.class);
        assertThat(generator.sequenceName()).isEqualTo("SEQ_SYS_AUDIT_LOGS");
        assertThat(generator.allocationSize()).isEqualTo(1);

        for (Field field : AuditLogEntity.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            assertThat(column).as("AuditLogEntity.%s thiếu @Column", field.getName()).isNotNull();
            assertThat(column.name()).isUpperCase();
            if (!"id".equals(field.getName())) {
                assertThat(column.updatable()).as("%s: nhật ký không được sửa", field.getName()).isFalse();
            }
        }
    }
}
