package com.education.base.controller;

import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.service.StudentDiscountService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentDiscountController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class StudentDiscountControllerTest {

    private static final String BODY = "{\"studentId\":8,\"discountType\":\"PERCENT\",\"discountValue\":10,"
            + "\"validFrom\":\"2026-01-01\",\"reason\":\"Anh chị em\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private StudentDiscountService studentDiscountService;

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:VIEW")
    void search_withViewPermission_returnsPage() throws Exception {
        when(studentDiscountService.search(any())).thenReturn(PageResponse.of(List.of(dto()), 1, 20, 1));

        mockMvc.perform(get("/api/v1/fee-discounts/search").param("studentId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.content[0].discountType").value("PERCENT"))
                .andExpect(jsonPath("$.data.content[0].studentCode").value("SV01"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = "MENU_TUITION_FEE:VIEW")
    void search_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/fee-discounts/search"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentDiscountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:VIEW")
    void getById_returnsDiscount() throws Exception {
        when(studentDiscountService.getById(5L)).thenReturn(dto());

        mockMvc.perform(get("/api/v1/fee-discounts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:CREATE")
    void create_withCreatePermission_returnsSaved() throws Exception {
        when(studentDiscountService.create(any())).thenReturn(dto());

        mockMvc.perform(post("/api/v1/fee-discounts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:VIEW")
    void create_withOnlyViewPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/fee-discounts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentDiscountService);
    }

    @Test
    void create_invalidType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/fee-discounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":8,\"discountType\":\"FREE\",\"discountValue\":10,"
                                + "\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(studentDiscountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:UPDATE")
    void update_withUpdatePermission_returnsSaved() throws Exception {
        when(studentDiscountService.update(eq(5L), any())).thenReturn(dto());

        mockMvc.perform(put("/api/v1/fee-discounts/5").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:CREATE")
    void update_withoutUpdatePermission_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/fee-discounts/5").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentDiscountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:DELETE")
    void delete_withDeletePermission_softDeletes() throws Exception {
        mockMvc.perform(delete("/api/v1/fee-discounts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"));
        verify(studentDiscountService).softDelete(5L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_FEE_DISCOUNT:UPDATE")
    void delete_withoutDeletePermission_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/fee-discounts/5"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentDiscountService);
    }

    private static StudentDiscountDto dto() {
        return StudentDiscountDto.builder()
                .id(5L)
                .studentId(8L)
                .studentCode("SV01")
                .studentName("Nguyễn Văn A")
                .discountType("PERCENT")
                .discountValue(new BigDecimal("10"))
                .build();
    }
}
