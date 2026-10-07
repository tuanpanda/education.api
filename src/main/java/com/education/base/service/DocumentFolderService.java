package com.education.base.service;

import com.education.base.dto.request.DocFolderUpsertRequest;
import com.education.base.dto.response.DocFolderDto;
import com.education.base.dto.response.DocFolderTreeNodeDto;

import java.util.List;

public interface DocumentFolderService {

    List<DocFolderTreeNodeDto> getTree();

    DocFolderDto getFolder(Long id);

    DocFolderDto create(DocFolderUpsertRequest request);

    DocFolderDto update(Long id, DocFolderUpsertRequest request);

    void delete(Long id);
}