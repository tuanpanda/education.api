package com.education.base.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Khóa chính kép của {@code SYS_USER_ROLES} ({@code USER_ID}, {@code ROLE_ID}).
 * Bảng này không dùng sequence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId implements Serializable {

    private Long userId;

    private Long roleId;
}
