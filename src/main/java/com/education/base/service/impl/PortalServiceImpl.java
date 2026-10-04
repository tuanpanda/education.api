package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.response.PortalClassDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortalServiceImpl implements PortalService {

    static final String STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND";

    private final PortalStudentContext portalStudentContext;
    private final StudentRepository studentRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    @Transactional(readOnly = true)
    public PortalMeResponse me() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        StudentEntity student = studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(STUDENT_NOT_FOUND,
                        "Không tìm thấy hồ sơ học sinh. Vui lòng liên hệ trung tâm."));
        List<PortalClassDto> classes = new ArrayList<>();
        for (ClassStudentEntity enrollment : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            ClassEntity clazz = enrollment.getClazz();
            if (clazz == null) {
                continue;
            }
            classes.add(PortalClassDto.builder()
                    .classId(clazz.getId())
                    .classCode(clazz.getClassCode())
                    .className(clazz.getClassName())
                    .status(clazz.getStatus())
                    .build());
        }
        return PortalMeResponse.builder()
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .gender(null)
                .classes(classes)
                .parentName(student.getParentName())
                .phone(student.getPhone())
                .email(student.getEmail())
                .build();
    }
}
