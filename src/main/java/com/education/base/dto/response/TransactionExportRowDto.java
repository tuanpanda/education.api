package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng của file Excel danh sách giao dịch ({@code /api/v1/reports/export/payment-transactions}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionExportRowDto {

    private Long id;

    private String transactionCode;

    private Long tuitionFeeId;

    private String feeCode;

    private Long studentId;

    private String studentCode;

    private String studentName;

    private Long classId;

    private String classCode;

    private String className;

    private BigDecimal amount;

    private String paymentMethod;

    private LocalDateTime paymentDate;

    private String bankBin;

    private String accountNo;

    private String bankReferenceNo;

    private String status;

    private String note;

    private String createdBy;
}
