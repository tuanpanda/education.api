package com.education.base.security;

import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalStudentContextTest {

    @Mock
    private UserStudentLinkRepository linkRepository;
    @InjectMocks
    private PortalStudentContext context;

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    @Test
    void student_resolvesStudentFromLinkTable_notFromPrincipalCache() {
        TestSecurityContexts.loginStudent(9L, 999L, false);
        when(linkRepository.findActiveSelfLinkByUserId(9L)).thenReturn(Optional.of(
                UserStudentLinkEntity.builder().userId(9L).studentId(42L).relation("SELF").status("ACTIVE").build()));

        assertThat(context.requireCurrentStudentId()).isEqualTo(42L);
    }

    @Test
    void studentWithoutLink_isNotFound() {
        TestSecurityContexts.loginStudent(9L, null, false);
        when(linkRepository.findActiveSelfLinkByUserId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> context.requireCurrentStudentId()).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(PortalStudentContext.STUDENT_LINK_NOT_FOUND);
    }

    @Test
    void staff_isStudentOnly() {
        TestSecurityContexts.loginAdmin(1L);

        assertThatThrownBy(() -> context.requireCurrentStudentId()).isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STUDENT_ONLY_CODE);
        verifyNoInteractions(linkRepository);
    }

    @Test
    void anonymous_isUnauthorized() {
        assertThatThrownBy(() -> context.requireCurrentStudentId()).isInstanceOf(UnauthorizedException.class);
    }
}
