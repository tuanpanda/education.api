package com.education.base.mapper;

import com.education.base.dto.request.LeadCreateRequest;
import com.education.base.dto.request.LeadUpdateRequest;
import com.education.base.dto.response.LeadDetailResponse;
import com.education.base.dto.response.LeadReportDto;
import com.education.base.entity.LeadEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface LeadMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "convertedStudentId", ignore = true)
    @Mapping(target = "convertedStudent", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    LeadEntity toEntity(LeadCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "leadCode", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "convertedStudentId", ignore = true)
    @Mapping(target = "convertedStudent", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(LeadUpdateRequest request, @MappingTarget LeadEntity entity);

    @Mapping(target = "assignedToName", ignore = true)
    @Mapping(target = "convertedStudentCode", ignore = true)
    @Mapping(target = "convertedStudentName", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    LeadDetailResponse toDetail(LeadEntity entity);

    @Mapping(target = "assignedToName", ignore = true)
    @Mapping(target = "convertedStudentCode", ignore = true)
    LeadReportDto toReport(LeadEntity entity);
}
