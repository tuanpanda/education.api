package com.education.base.mapper;

import com.education.base.dto.response.FileResponseDto;
import com.education.base.entity.FileEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Chuyển đổi {@link FileEntity} sang {@link FileResponseDto}, tự sinh thêm hai đường dẫn
 * API để xem trực tiếp và tải file.
 * <p>
 * Tắt builder của MapStruct để mapper dùng setter, nhờ đó {@code @AfterMapping} nhận trực tiếp
 * đối tượng DTO thay vì builder trung gian.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface FileMapper {

    String VIEW_URL_PREFIX = "/api/v1/files/view/";
    String DOWNLOAD_URL_PREFIX = "/api/v1/files/download/";

    @Mapping(target = "viewUrl", ignore = true)
    @Mapping(target = "downloadUrl", ignore = true)
    FileResponseDto toDto(FileEntity entity);

    List<FileResponseDto> toDtoList(List<FileEntity> entities);

    /**
     * Gắn link view/download dựa trên ID file sau khi các trường thường đã được map.
     */
    @AfterMapping
    default void fillAccessUrls(FileEntity entity, @MappingTarget FileResponseDto dto) {
        if (entity.getId() == null) {
            return;
        }
        dto.setViewUrl(VIEW_URL_PREFIX + entity.getId());
        dto.setDownloadUrl(DOWNLOAD_URL_PREFIX + entity.getId());
    }
}
