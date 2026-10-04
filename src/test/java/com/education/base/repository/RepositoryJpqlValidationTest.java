package com.education.base.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra cú pháp + ngữ nghĩa mọi JPQL {@code @Query} (không native) của các repository bằng Hibernate thật, KHÔNG
 * cần cơ sở dữ liệu: dựng {@link SessionFactory} từ toàn bộ entity với {@code allow_jdbc_metadata_access=false}
 * rồi {@code createQuery} từng câu (lỗi tên thuộc tính / join / tham số sẽ lộ ra ở đây thay vì lúc khởi động
 * ứng dụng). Bổ sung cho V17: truy vấn {@code UserStudentLinkRepository.searchStudentAccounts}.
 */
class RepositoryJpqlValidationTest {

    private static final String ENTITY_PACKAGE = "com.education.base.entity";
    private static final String REPOSITORY_PACKAGE = "com.education.base.repository";

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void bootHibernateWithoutDatabase() throws Exception {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("jakarta.persistence.database-product-name", "Oracle")
                .applySetting("jakarta.persistence.database-major-version", "21")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entity : scan(ENTITY_PACKAGE, new AnnotationTypeFilter(Entity.class), false)) {
            sources.addAnnotatedClass(entity);
        }
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void everyJpqlQueryCompiles() throws Exception {
        Map<String, String> failures = new TreeMap<>();
        int checked = 0;
        boolean sawStudentAccountSearch = false;
        for (Class<?> repository : scan(REPOSITORY_PACKAGE, new AssignableTypeFilter(Repository.class), true)) {
            for (Method method : repository.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || query.nativeQuery()) {
                    continue;
                }
                List<String> statements = new ArrayList<>();
                statements.add(query.value());
                if (!query.countQuery().isBlank()) {
                    statements.add(query.countQuery());
                }
                for (String jpql : statements) {
                    checked++;
                    try (EntityManager em = sessionFactory.createEntityManager()) {
                        em.createQuery(jpql);
                    } catch (RuntimeException ex) {
                        failures.put(repository.getSimpleName() + "#" + method.getName(), ex.getMessage());
                    }
                }
                if (repository == UserStudentLinkRepository.class && method.getName().equals("searchStudentAccounts")) {
                    sawStudentAccountSearch = true;
                    assertThat(query.countQuery()).as("searchStudentAccounts cần countQuery riêng").isNotBlank();
                }
            }
        }
        assertThat(sawStudentAccountSearch).isTrue();
        assertThat(checked).isGreaterThan(5);
        assertThat(failures).as("JPQL không hợp lệ").isEmpty();
    }

    /** Bảo đảm cơ chế kiểm tra thật sự bắt lỗi (không âm thầm chấp nhận mọi câu). */
    @Test
    void invalidJpqlIsRejected() {
        try (EntityManager em = sessionFactory.createEntityManager()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> em.createQuery("select l.noSuchField from UserStudentLinkEntity l"))
                    .isInstanceOf(IllegalArgumentException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> em.createQuery("select s from StudentEntity s left join NoSuchEntity x on x.id = s.id"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static List<Class<?>> scan(String basePackage, org.springframework.core.type.filter.TypeFilter filter,
                                       boolean interfaces) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return interfaces ? definition.getMetadata().isInterface()
                        : definition.getMetadata().isConcrete();
            }
        };
        scanner.addIncludeFilter(filter);
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
            result.add(ClassUtils.forName(definition.getBeanClassName(),
                    RepositoryJpqlValidationTest.class.getClassLoader()));
        }
        return result;
    }
}
