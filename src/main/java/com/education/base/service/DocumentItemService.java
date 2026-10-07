package com.education.base.service;

import com.education.base.dto.request.DocItemCreateRequest;
import com.education.base.dto.request.DocItemFilterRequest;
import com.education.base.dto.request.DocItemUpdateRequest;
import com.education.base.dto.response.DocItemDto;
import com.education.base.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentItemService {

    PageResponse<DocItemDto> search(DocItemFilterRequest filter);

    DocItemDto getItem(Long id);

    DocItemDto create(MultipartFile file, DocItemCreateRequest request);

    DocItemDto update(Long id, DocItemUpdateRequest request);

    DocItemDto replaceFile(Long id, MultipartFile file, String versionNote);

    void delete(Long id);

    List<String> suggestTags(String prefix);
}