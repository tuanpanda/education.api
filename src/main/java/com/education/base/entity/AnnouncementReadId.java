package com.education.base.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Khóa kép {@code (ANNOUNCEMENT_ID, USER_ID)} của {@code EDU_ANNOUNCEMENT_READS}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementReadId implements Serializable {

    private Long announcementId;

    private Long userId;
}
