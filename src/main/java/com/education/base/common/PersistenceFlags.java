package com.education.base.common;

import lombok.experimental.UtilityClass;

/**
 * Giá trị cột {@code IS_DELETED} dùng chung toàn hệ thống.
 */
@UtilityClass
public class PersistenceFlags {

    public static final int NOT_DELETED = 0;

    public static final int DELETED = 1;
}
