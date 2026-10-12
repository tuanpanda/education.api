package com.education.base.service;

import com.education.base.dto.request.DocSharesUpdateRequest;
import com.education.base.dto.response.DocShareDto;
import com.education.base.dto.response.DocShareTargetsDto;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.DocShareEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DocumentShareService {

    List<DocShareDto> getFolderShares(Long folderId);

    List<DocShareDto> putFolderShares(Long folderId, DocSharesUpdateRequest request);

    List<DocShareDto> getItemShares(Long itemId);

    List<DocShareDto> putItemShares(Long itemId, DocSharesUpdateRequest request);

    DocShareTargetsDto getShareTargets();

    /** Chia sẻ trực tiếp của thư mục, có / không kèm chia sẻ kế thừa từ tổ tiên. */
    List<DocShareDto> describeFolderShares(DocumentAccessService.Context ctx, DocFolderEntity folder,
                                           boolean includeInherited);

    /** Chia sẻ trực tiếp của tài liệu, có / không kèm chia sẻ kế thừa từ thư mục chứa nó và tổ tiên. */
    List<DocShareDto> describeItemShares(DocumentAccessService.Context ctx, DocItemEntity item,
                                         boolean includeInherited);

    /** Chia sẻ trực tiếp theo itemId (cho danh sách). */
    Map<Long, List<DocShareEntity>> directItemShares(Collection<Long> itemIds);

    /** Chuyển sang DTO (nhãn đích nạp theo lô). */
    List<DocShareDto> toDtos(DocumentAccessService.Context ctx, List<DocShareEntity> shares);

    /** Có chia sẻ CLASS còn hạn bật cờ học sinh (trực tiếp hoặc kế thừa). */
    boolean visibleToStudents(DocumentAccessService.Context ctx, DocItemEntity item, List<DocShareEntity> direct,
                              Map<Long, List<DocShareEntity>> folderShares);

    /** Chia sẻ trực tiếp của các thư mục (theo folderId). */
    Map<Long, List<DocShareEntity>> folderShares(Collection<Long> folderIds);
}