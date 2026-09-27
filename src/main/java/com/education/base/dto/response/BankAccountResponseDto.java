package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponseDto {

    private Long id;
    private String accountCode;
    private String bankBin;
    private String bankName;
    private String accountNo;
    private String accountName;
    private boolean active;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
