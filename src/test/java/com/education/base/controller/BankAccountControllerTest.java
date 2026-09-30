package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.service.BankAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BankAccountController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class BankAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private BankAccountService bankAccountService;

    @Test
    void list_returnsAccounts() throws Exception {
        when(bankAccountService.list()).thenReturn(List.of(BankAccountResponseDto.builder()
                .id(1L)
                .accountCode("BA-DEFAULT")
                .accountNo("1234567890")
                .active(true)
                .build()));

        mockMvc.perform(get("/api/v1/bank-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void activate_returnsActiveAccount() throws Exception {
        when(bankAccountService.activate(eq(2L))).thenReturn(BankAccountResponseDto.builder()
                .id(2L)
                .active(true)
                .accountNo("222")
                .build());

        mockMvc.perform(post("/api/v1/bank-accounts/2/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void create_validatesBin() throws Exception {
        mockMvc.perform(post("/api/v1/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountCode\":\"BA1\",\"bankBin\":\"abc\",\"bankName\":\"VCB\","
                                + "\"accountNo\":\"123456\",\"accountName\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void getActive_isOpenToAnyAuthenticatedUser() throws Exception {
        when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .id(2L).active(true).accountNo("222").build());

        mockMvc.perform(get("/api/v1/bank-accounts/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").value("222"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void list_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/bank-accounts"))
                .andExpect(status().isForbidden());
    }
}
