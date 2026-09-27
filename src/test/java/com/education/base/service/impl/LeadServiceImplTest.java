package com.education.base.service.impl;

import com.education.base.dto.request.LeadConvertRequest;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.entity.LeadEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.LeadMapperImpl;
import com.education.base.repository.LeadRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.ClassService;
import com.education.base.service.FileStorageService;
import com.education.base.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private ClassService classService;
    @Mock
    private FileStorageService fileStorageService;

    private LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadServiceImpl(leadRepository, userRepository, studentRepository,
                studentService, classService, fileStorageService, new LeadMapperImpl(), new FileMapperImpl());
    }

    @Test
    void convert_createsStudentAndMarksLeadConverted() {
        when(leadRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.of(LeadEntity.builder()
                .id(7L).leadCode("LD01").fullName("Tran Van B").email("b@edu.com")
                .status("QUALIFIED").source("WEBSITE").isDeleted(0).build()));
        when(studentService.create(any())).thenReturn(StudentDetailResponse.builder()
                .id(100L).studentCode("SVLD01").fullName("Tran Van B").status("ACTIVE").build());
        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadConvertRequest request = LeadConvertRequest.builder()
                .studentCode("SVLD01")
                .note("Chuyen doi sau khi thi dau vao")
                .build();

        StudentDetailResponse student = service.convert(7L, request);
        assertThat(student.getId()).isEqualTo(100L);

        ArgumentCaptor<LeadEntity> captor = ArgumentCaptor.forClass(LeadEntity.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONVERTED");
        assertThat(captor.getValue().getConvertedStudentId()).isEqualTo(100L);
        verify(classService, never()).enroll(any(), any());
    }

    @Test
    void convert_alreadyConverted_throws() {
        when(leadRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.of(LeadEntity.builder()
                .id(7L).leadCode("LD01").fullName("A").status("CONVERTED").isDeleted(0).build()));

        LeadConvertRequest request = LeadConvertRequest.builder().note("retry").build();
        assertThatThrownBy(() -> service.convert(7L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("LEAD_ALREADY_CONVERTED");
        verify(studentService, never()).create(any());
    }
}
