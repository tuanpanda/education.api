package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.request.LeadConvertRequest;
import com.education.base.dto.request.LeadCreateRequest;
import com.education.base.dto.request.LeadFilterRequest;
import com.education.base.dto.request.LeadUpdateRequest;
import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.response.LeadDetailResponse;
import com.education.base.dto.response.LeadReportDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.entity.LeadEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.mapper.LeadMapper;
import com.education.base.repository.LeadRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.spec.LeadSpecifications;
import com.education.base.service.ClassService;
import com.education.base.service.FileStorageService;
import com.education.base.service.LeadService;
import com.education.base.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final ClassService classService;
    private final FileStorageService fileStorageService;
    private final LeadMapper leadMapper;
    private final FileMapper fileMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadReportDto> search(LeadFilterRequest filter) {
        LeadFilterRequest criteria = filter == null ? new LeadFilterRequest() : filter;
        Page<LeadEntity> page = leadRepository.findAll(
                LeadSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePageNo() - 1, criteria.resolvePageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<LeadReportDto> content = new ArrayList<>();
        for (LeadEntity entity : page.getContent()) {
            content.add(toReport(entity));
        }
        return PageResponse.of(content, criteria.resolvePageNo(), criteria.resolvePageSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public LeadDetailResponse getDetail(Long id) {
        return toDetail(requireActiveLead(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadDetailResponse create(LeadCreateRequest request) {
        String leadCode = request.getLeadCode().trim();
        if (leadRepository.existsByLeadCode(leadCode)) {
            throw new OracleBusinessException("LEAD_CODE_DUPLICATED",
                    "Mã lead '" + leadCode + "' đã tồn tại.");
        }
        validateAssignee(request.getAssignedToId());
        LeadEntity entity = leadMapper.toEntity(request);
        entity.setLeadCode(leadCode);
        entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("NEW");
        }
        return toDetail(leadRepository.save(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadDetailResponse update(Long id, LeadUpdateRequest request) {
        LeadEntity entity = requireActiveLead(id);
        if ("CONVERTED".equals(entity.getStatus())) {
            throw new OracleBusinessException("LEAD_ALREADY_CONVERTED",
                    "Lead đã chuyển thành học sinh, không được sửa hồ sơ nguồn.");
        }
        validateAssignee(request.getAssignedToId());
        leadMapper.updateEntity(request, entity);
        return toDetail(leadRepository.save(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        LeadEntity entity = requireActiveLead(id);
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setUpdatedAt(LocalDateTime.now());
        leadRepository.save(entity);
        log.info("Đã xóa mềm lead id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentDetailResponse convert(Long id, LeadConvertRequest request) {
        LeadEntity lead = requireActiveLead(id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new OracleBusinessException("LEAD_ALREADY_CONVERTED",
                    "Lead '" + lead.getLeadCode() + "' đã được chuyển thành học sinh.");
        }
        if ("LOST".equals(lead.getStatus())) {
            throw new OracleBusinessException("LEAD_ALREADY_LOST",
                    "Lead '" + lead.getLeadCode() + "' đã đóng (LOST), không thể chuyển đổi.");
        }

        String fullName = (request.getFullName() == null || request.getFullName().isBlank())
                ? lead.getFullName()
                : request.getFullName().trim();
        String email = (request.getEmail() == null || request.getEmail().isBlank())
                ? lead.getEmail()
                : request.getEmail().trim();

        StudentCreateRequest studentRequest = new StudentCreateRequest(fullName, email, "ACTIVE");
        studentRequest.setPhone(lead.getPhone());
        StudentDetailResponse student = studentService.create(studentRequest);

        lead.setConvertedStudentId(student.getId());
        lead.setStatus("CONVERTED");
        lead.setNote(request.getNote());
        leadRepository.save(lead);

        if (request.getEnrollClassId() != null) {
            classService.enroll(request.getEnrollClassId(), EnrollStudentsRequest.builder()
                    .classId(request.getEnrollClassId())
                    .studentIds(List.of(student.getId()))
                    .build());
        }

        log.info("Đã chuyển lead id={} thành học sinh id={}", id, student.getId());
        return student;
    }

    private LeadEntity requireActiveLead(Long id) {
        if (id == null) {
            throw new OracleBusinessException("LEAD_ID_REQUIRED", "ID lead không được để trống.");
        }
        return leadRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "LEAD_NOT_FOUND", "Không tìm thấy lead với ID: " + id));
    }

    private void validateAssignee(Long assignedToId) {
        if (assignedToId == null) {
            return;
        }
        userRepository.findByIdAndIsDeleted(assignedToId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "USER_NOT_FOUND", "Không tìm thấy người phụ trách với ID: " + assignedToId));
    }

    private LeadDetailResponse toDetail(LeadEntity entity) {
        LeadDetailResponse detail = leadMapper.toDetail(entity);
        fillNames(entity, detail);
        detail.setAttachments(fileMapper.toDtoList(
                fileStorageService.getFilesByRef(DomainConstants.Module.LEAD, entity.getId())));
        return detail;
    }

    private LeadReportDto toReport(LeadEntity entity) {
        LeadReportDto dto = leadMapper.toReport(entity);
        if (entity.getAssignedToId() != null) {
            userRepository.findById(entity.getAssignedToId())
                    .map(UserEntity::getFullName)
                    .ifPresent(dto::setAssignedToName);
        }
        if (entity.getConvertedStudentId() != null) {
            studentRepository.findById(entity.getConvertedStudentId())
                    .map(StudentEntity::getStudentCode)
                    .ifPresent(dto::setConvertedStudentCode);
        }
        return dto;
    }

    private void fillNames(LeadEntity entity, LeadDetailResponse detail) {
        if (entity.getAssignedToId() != null) {
            userRepository.findById(entity.getAssignedToId()).ifPresent(user ->
                    detail.setAssignedToName(user.getFullName()));
        }
        if (entity.getConvertedStudentId() != null) {
            studentRepository.findById(entity.getConvertedStudentId()).ifPresent(student -> {
                detail.setConvertedStudentCode(student.getStudentCode());
                detail.setConvertedStudentName(student.getFullName());
            });
        }
    }
}
