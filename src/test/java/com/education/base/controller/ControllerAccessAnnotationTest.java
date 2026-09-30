package com.education.base.controller;

import com.education.base.security.PermissionInterceptor;
import com.education.base.security.PermissionInterceptor.AccessType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chốt chặn cho chính sách TỪ CHỐI MẶC ĐỊNH của {@link PermissionInterceptor}: mọi handler của mọi
 * {@code @RestController} trong {@code com.education.base.controller} phải khai báo {@code @RequirePermission},
 * {@code @AuthenticatedOnly} hoặc {@code @PublicEndpoint}; và danh sách endpoint công khai là cố định.
 */
class ControllerAccessAnnotationTest {

    private static final String CONTROLLER_PACKAGE = "com.education.base.controller";

    /** Endpoint công khai hợp lệ - phải khớp {@code permitAll} trong {@code SecurityConfig}. */
    private static final Set<String> EXPECTED_PUBLIC = Set.of(
            "AuthController#login",
            "AuthController#refresh",
            "HealthController#health",
            "HomeController#redirectToSwaggerUi",
            "HomeController#favicon");

    private static List<Class<?>> restControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            result.add(ClassUtils.forName(definition.getBeanClassName(), ControllerAccessAnnotationTest.class.getClassLoader()));
        }
        return result;
    }

    private static List<Method> handlerMethods(Class<?> controller) {
        List<Method> result = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isStatic(method.getModifiers())
                    && AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                result.add(method);
            }
        }
        return result;
    }

    @Test
    void everyHandlerDeclaresAccessRule() throws Exception {
        List<Class<?>> controllers = restControllers();
        assertThat(controllers).as("@RestController trong " + CONTROLLER_PACKAGE).hasSizeGreaterThan(15);

        Set<String> undeclared = new TreeSet<>();
        int handlers = 0;
        for (Class<?> controller : controllers) {
            for (Method method : handlerMethods(controller)) {
                handlers++;
                if (PermissionInterceptor.accessRule(method, controller).type() == AccessType.UNDECLARED) {
                    undeclared.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(handlers).as("số handler được quét").isGreaterThan(50);
        assertThat(undeclared)
                .as("Handler thiếu @RequirePermission / @AuthenticatedOnly / @PublicEndpoint (bị chặn 403 mặc định)")
                .isEmpty();
    }

    @Test
    void publicEndpointsAreExactlyTheExpectedOnes() throws Exception {
        Set<String> publicHandlers = new TreeSet<>();
        for (Class<?> controller : restControllers()) {
            for (Method method : handlerMethods(controller)) {
                if (PermissionInterceptor.accessRule(method, controller).type() == AccessType.PUBLIC) {
                    publicHandlers.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(publicHandlers).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC);
    }
}
