package com.education.base.service;

import com.education.base.dto.request.MenuReorderRequest;
import com.education.base.dto.request.MenuUpsertRequest;
import com.education.base.dto.response.AdminMenuResponseDto;
import com.education.base.dto.response.FunctionOptionDto;

import java.util.List;

/**
 * Quản trị cây menu ({@code SYS_MENUS}) và chức năng của từng menu ({@code SYS_FUNCTIONS}).
 */
public interface MenuAdminService {

    List<AdminMenuResponseDto> getTree();

    AdminMenuResponseDto getById(Long id);

    AdminMenuResponseDto create(MenuUpsertRequest request);

    AdminMenuResponseDto update(Long id, MenuUpsertRequest request);

    void delete(Long id);

    List<AdminMenuResponseDto> reorder(MenuReorderRequest request);

    /** Danh mục mã chức năng đang dùng trên các menu (kèm chức năng chuẩn). */
    List<FunctionOptionDto> listFunctions();
}
