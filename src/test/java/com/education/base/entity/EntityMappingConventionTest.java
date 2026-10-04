package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.IdClass;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khóa các quy ước DB-First: tên bảng/cột viết HOA, sequence Oracle với
 * {@code allocationSize = 1}, field thường map {@code @Column}, quan hệ {@code FetchType.LAZY}.
 */
class EntityMappingConventionTest {

    private static Stream<Object[]> sequencedEntities() {
        return Stream.of(
                new Object[]{StudentEntity.class, "EDU_STUDENTS", "SEQ_EDU_STUDENTS"},
                new Object[]{ClassEntity.class, "EDU_CLASSES", "SEQ_EDU_CLASSES"},
                new Object[]{ClassStudentEntity.class, "EDU_CLASS_STUDENTS", "SEQ_EDU_CLASS_STUDENTS"},
                new Object[]{AttendanceEntity.class, "EDU_ATTENDANCE", "SEQ_EDU_ATTENDANCE"},
                new Object[]{GradeEntity.class, "EDU_GRADES", "SEQ_EDU_GRADES"},
                new Object[]{LeadEntity.class, "EDU_LEADS", "SEQ_EDU_LEADS"},
                new Object[]{TuitionFeeEntity.class, "FIN_TUITION_FEES", "SEQ_FIN_TUITION_FEES"},
                new Object[]{PaymentTransactionEntity.class, "FIN_PAYMENT_TRANSACTIONS", "SEQ_FIN_PAYMENT_TRANS"},
                new Object[]{BankAccountEntity.class, "FIN_BANK_ACCOUNTS", "SEQ_FIN_BANK_ACCOUNTS"},
                new Object[]{FileEntity.class, "SYS_ATTACHED_FILES", "SEQ_SYS_ATTACHED_FILES"},
                new Object[]{UserEntity.class, "SYS_USERS", "SEQ_SYS_USERS"},
                new Object[]{RefreshTokenEntity.class, "SYS_REFRESH_TOKENS", "SEQ_SYS_REFRESH_TOKENS"},
                new Object[]{RoleEntity.class, "SYS_ROLES", "SEQ_SYS_ROLES"},
                new Object[]{MenuEntity.class, "SYS_MENUS", "SEQ_SYS_MENUS"},
                new Object[]{FunctionEntity.class, "SYS_FUNCTIONS", "SEQ_SYS_FUNCTIONS"},
                new Object[]{RoleMenuPermissionEntity.class, "SYS_ROLE_MENU_PERMISSIONS", "SEQ_SYS_ROLE_MENU_PERM"},
                new Object[]{CodeRuleEntity.class, "SYS_CODE_RULES", "SEQ_SYS_CODE_RULES"},
                new Object[]{ClassScheduleEntity.class, "EDU_CLASS_SCHEDULES", "SEQ_EDU_CLASS_SCHEDULES"},
                new Object[]{ClassSessionEntity.class, "EDU_CLASS_SESSIONS", "SEQ_EDU_CLASS_SESSIONS"},
                new Object[]{UserStudentLinkEntity.class, "EDU_USER_STUDENT_LINKS", "SEQ_EDU_USER_STUDENT_LINKS"});
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sequencedEntities")
    void entity_mapsUppercaseTableAndOracleSequence(Class<?> type, String tableName, String sequenceName)
            throws Exception {

        assertThat(type.getAnnotation(Entity.class)).as("%s thiếu @Entity", type.getSimpleName()).isNotNull();

        Table table = type.getAnnotation(Table.class);
        assertThat(table).as("%s thiếu @Table", type.getSimpleName()).isNotNull();
        assertThat(table.name()).isEqualTo(tableName);
        assertThat(table.name()).isUpperCase();

        SequenceGenerator generator = type.getDeclaredField("id").getAnnotation(SequenceGenerator.class);
        assertThat(generator).as("%s thiếu @SequenceGenerator trên id", type.getSimpleName()).isNotNull();
        assertThat(generator.sequenceName()).isEqualTo(sequenceName);
        assertThat(generator.allocationSize())
                .as("allocationSize phải bằng 1 để không lệch ID so với sequence Oracle")
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sequencedEntities")
    void entity_mapsScalarFieldsToUppercaseColumn(Class<?> type, String tableName, String sequenceName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() || isAssociation(field)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            assertThat(column)
                    .as("%s.%s thiếu @Column", type.getSimpleName(), field.getName())
                    .isNotNull();
            assertThat(column.name())
                    .as("Cột của %s.%s phải viết HOA", type.getSimpleName(), field.getName())
                    .isUpperCase();
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sequencedEntities")
    void associations_areLazy(Class<?> type, String tableName, String sequenceName) {
        for (Field field : type.getDeclaredFields()) {
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            if (manyToOne != null) {
                assertThat(manyToOne.fetch())
                        .as("%s.%s phải FetchType.LAZY", type.getSimpleName(), field.getName())
                        .isEqualTo(FetchType.LAZY);
            }
            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            if (oneToMany != null) {
                assertThat(oneToMany.fetch())
                        .as("%s.%s phải FetchType.LAZY", type.getSimpleName(), field.getName())
                        .isEqualTo(FetchType.LAZY);
            }
        }
    }

    @Test
    void userRoleEntity_usesCompositeKeyWithoutSequence() {
        assertThat(UserRoleEntity.class.getAnnotation(Entity.class)).isNotNull();
        Table table = UserRoleEntity.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("SYS_USER_ROLES");
        assertThat(UserRoleEntity.class.getAnnotation(IdClass.class).value()).isEqualTo(UserRoleId.class);

        for (Field field : UserRoleEntity.class.getDeclaredFields()) {
            if (field.isSynthetic() || isAssociation(field)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            assertThat(column).as("UserRoleEntity.%s thiếu @Column", field.getName()).isNotNull();
            assertThat(column.name()).isUpperCase();
        }
    }

    @Test
    void studentEntity_mapsBusinessColumns() throws Exception {
        assertColumn(StudentEntity.class, "studentCode", "STUDENT_CODE");
        assertColumn(StudentEntity.class, "fullName", "FULL_NAME");
        assertColumn(StudentEntity.class, "isDeleted", "IS_DELETED");
        assertColumn(StudentEntity.class, "createdAt", "CREATED_AT");
        assertColumn(StudentEntity.class, "updatedBy", "UPDATED_BY");
    }

    @Test
    void studentCode_isDatabaseGeneratedOnInsert() throws Exception {
        Field field = StudentEntity.class.getDeclaredField("studentCode");
        Column column = field.getAnnotation(Column.class);
        assertThat(column.insertable()).isTrue();
        assertThat(column.updatable()).isFalse();

        org.hibernate.annotations.Generated generated =
                field.getAnnotation(org.hibernate.annotations.Generated.class);
        assertThat(generated).as("studentCode phải @Generated để Hibernate đọc lại sau INSERT").isNotNull();
        assertThat(generated.event()).contains(org.hibernate.generator.EventType.INSERT);
    }

    @Test
    void classCode_isDatabaseGeneratedOnInsert() throws Exception {
        Field field = ClassEntity.class.getDeclaredField("classCode");
        Column column = field.getAnnotation(Column.class);
        assertThat(column.insertable()).isFalse();
        assertThat(column.updatable()).isFalse();

        org.hibernate.annotations.Generated generated =
                field.getAnnotation(org.hibernate.annotations.Generated.class);
        assertThat(generated).as("classCode phải @Generated để Hibernate đọc lại sau INSERT").isNotNull();
        assertThat(generated.event()).contains(org.hibernate.generator.EventType.INSERT);
    }

    @Test
    void classEntity_mapsBusinessColumns() throws Exception {
        assertColumn(ClassEntity.class, "classCode", "CLASS_CODE");
        assertColumn(ClassEntity.class, "gradeLevel", "GRADE_LEVEL");
        assertColumn(ClassEntity.class, "teacherId", "TEACHER_ID");
        assertColumn(ClassEntity.class, "tuitionAmount", "TUITION_AMOUNT");
        assertColumn(ClassEntity.class, "startDate", "START_DATE");
    }

    @Test
    void classScheduleEntity_mapsBusinessColumns() throws Exception {
        assertColumn(ClassScheduleEntity.class, "classId", "CLASS_ID");
        assertColumn(ClassScheduleEntity.class, "dayOfWeek", "DAY_OF_WEEK");
        assertColumn(ClassScheduleEntity.class, "startTime", "START_TIME");
        assertColumn(ClassScheduleEntity.class, "endTime", "END_TIME");
        assertColumn(ClassScheduleEntity.class, "roomName", "ROOM_NAME");
        assertColumn(ClassScheduleEntity.class, "teacherId", "TEACHER_ID");
    }

    @Test
    void classSessionEntity_mapsBusinessColumns() throws Exception {
        assertColumn(ClassSessionEntity.class, "classId", "CLASS_ID");
        assertColumn(ClassSessionEntity.class, "scheduleId", "SCHEDULE_ID");
        assertColumn(ClassSessionEntity.class, "sessionDate", "SESSION_DATE");
        assertColumn(ClassSessionEntity.class, "startTime", "START_TIME");
        assertColumn(ClassSessionEntity.class, "status", "STATUS");
        assertColumn(ClassSessionEntity.class, "topic", "TOPIC");
    }

    @Test
    void fileEntity_mapsStorageColumns() throws Exception {
        assertColumn(FileEntity.class, "originalName", "ORIGINAL_NAME");
        assertColumn(FileEntity.class, "storedName", "STORED_NAME");
        assertColumn(FileEntity.class, "filePath", "FILE_PATH");
        assertColumn(FileEntity.class, "contentType", "CONTENT_TYPE");
        assertColumn(FileEntity.class, "moduleName", "MODULE_NAME");
        assertColumn(FileEntity.class, "referenceId", "REFERENCE_ID");
    }

    @Test
    void menuEntity_mapsHierarchyColumns() throws Exception {
        assertColumn(MenuEntity.class, "parentId", "PARENT_ID");
        assertColumn(MenuEntity.class, "menuCode", "MENU_CODE");
        assertColumn(MenuEntity.class, "menuType", "MENU_TYPE");
        assertColumn(MenuEntity.class, "sortOrder", "SORT_ORDER");
        assertColumn(MenuEntity.class, "isHidden", "IS_HIDDEN");
    }

    @Test
    void roleMenuPermissionEntity_mapsAllowedFunctions() throws Exception {
        assertColumn(RoleMenuPermissionEntity.class, "roleId", "ROLE_ID");
        assertColumn(RoleMenuPermissionEntity.class, "menuId", "MENU_ID");
        assertColumn(RoleMenuPermissionEntity.class, "allowedFunctions", "ALLOWED_FUNCTIONS");
    }

    @Test
    void leadAndFinanceEntities_mapForeignKeys() throws Exception {
        assertColumn(LeadEntity.class, "leadCode", "LEAD_CODE");
        assertColumn(LeadEntity.class, "assignedToId", "ASSIGNED_TO_ID");
        assertColumn(LeadEntity.class, "convertedStudentId", "CONVERTED_STUDENT_ID");
        assertColumn(TuitionFeeEntity.class, "feeCode", "FEE_CODE");
        assertColumn(TuitionFeeEntity.class, "studentId", "STUDENT_ID");
        assertColumn(TuitionFeeEntity.class, "feeMonth", "FEE_MONTH");
        assertColumn(TuitionFeeEntity.class, "feeYear", "FEE_YEAR");
        assertColumn(TuitionFeeEntity.class, "pricePerSession", "PRICE_PER_SESSION");
        assertColumn(TuitionFeeEntity.class, "totalSessions", "TOTAL_SESSIONS");
        assertColumn(TuitionFeeEntity.class, "teacherComment", "TEACHER_COMMENT");
        assertColumn(TuitionFeeEntity.class, "footerWish", "FOOTER_WISH");
        assertColumn(TuitionFeeEntity.class, "slipLabel", "SLIP_LABEL");
        assertColumn(PaymentTransactionEntity.class, "transactionCode", "TRANSACTION_CODE");
        assertColumn(PaymentTransactionEntity.class, "tuitionFeeId", "TUITION_FEE_ID");
        assertColumn(BankAccountEntity.class, "accountCode", "ACCOUNT_CODE");
        assertColumn(BankAccountEntity.class, "bankBin", "BANK_BIN");
        assertColumn(BankAccountEntity.class, "bankName", "BANK_NAME");
        assertColumn(BankAccountEntity.class, "accountNo", "ACCOUNT_NO");
        assertColumn(BankAccountEntity.class, "accountName", "ACCOUNT_NAME");
        assertColumn(BankAccountEntity.class, "isActive", "IS_ACTIVE");
        assertColumn(AttendanceEntity.class, "attendanceDate", "ATTENDANCE_DATE");
        assertColumn(AttendanceEntity.class, "recordedById", "RECORDED_BY_ID");
        assertColumn(GradeEntity.class, "gradeType", "GRADE_TYPE");
        assertColumn(ClassStudentEntity.class, "enrolledAt", "ENROLLED_AT");
    }

    @Test
    void codeRuleEntity_mapsBusinessColumns() throws Exception {
        assertColumn(CodeRuleEntity.class, "ruleCode", "RULE_CODE");
        assertColumn(CodeRuleEntity.class, "moduleName", "MODULE_NAME");
        assertColumn(CodeRuleEntity.class, "tableName", "TABLE_NAME");
        assertColumn(CodeRuleEntity.class, "prefix", "PREFIX");
        assertColumn(CodeRuleEntity.class, "pattern", "PATTERN");
        assertColumn(CodeRuleEntity.class, "seqLength", "SEQ_LENGTH");
        assertColumn(CodeRuleEntity.class, "resetCycle", "RESET_CYCLE");
        assertColumn(CodeRuleEntity.class, "lastResetKey", "LAST_RESET_KEY");
        assertColumn(CodeRuleEntity.class, "lastSeq", "LAST_SEQ");
        assertColumn(CodeRuleEntity.class, "isActive", "IS_ACTIVE");
    }

    @Test
    void onCreate_appliesDatabaseDefaults() {
        StudentEntity student = new StudentEntity();
        student.onCreate();
        assertThat(student.getIsDeleted()).isZero();
        assertThat(student.getStatus()).isEqualTo("ACTIVE");
        assertThat(student.getCreatedAt()).isNotNull();

        FileEntity file = new FileEntity();
        file.onCreate();
        assertThat(file.getIsDeleted()).isZero();
        assertThat(file.getCreatedAt()).isNotNull();

        MenuEntity menu = new MenuEntity();
        menu.onCreate();
        assertThat(menu.getIsDeleted()).isZero();
        assertThat(menu.getIsHidden()).isZero();
        assertThat(menu.getSortOrder()).isZero();
        assertThat(menu.getStatus()).isEqualTo("ACTIVE");

        UserEntity user = new UserEntity();
        user.onCreate();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.onCreate();
        assertThat(refreshToken.getCreatedAt()).isNotNull();

        RoleEntity role = new RoleEntity();
        role.onCreate();
        assertThat(role.getStatus()).isEqualTo("ACTIVE");

        FunctionEntity function = new FunctionEntity();
        function.onCreate();
        assertThat(function.getIsDeleted()).isZero();

        RoleMenuPermissionEntity permission = new RoleMenuPermissionEntity();
        permission.onCreate();
        assertThat(permission.getCreatedAt()).isNotNull();

        ClassEntity clazz = new ClassEntity();
        clazz.onCreate();
        assertThat(clazz.getIsDeleted()).isZero();
        assertThat(clazz.getCapacity()).isZero();
        assertThat(clazz.getTuitionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(clazz.getStatus()).isEqualTo("PLANNED");

        ClassStudentEntity enrollment = new ClassStudentEntity();
        enrollment.onCreate();
        assertThat(enrollment.getStatus()).isEqualTo("ENROLLED");
        assertThat(enrollment.getEnrolledAt()).isNotNull();

        GradeEntity grade = new GradeEntity();
        grade.onCreate();
        assertThat(grade.getWeight()).isEqualByComparingTo(BigDecimal.ONE);

        LeadEntity lead = new LeadEntity();
        lead.onCreate();
        assertThat(lead.getSource()).isEqualTo("OTHER");
        assertThat(lead.getStatus()).isEqualTo("NEW");

        TuitionFeeEntity fee = new TuitionFeeEntity();
        fee.onCreate();
        assertThat(fee.getStatus()).isEqualTo("UNPAID");
        assertThat(fee.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fee.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fee.getSlipLabel()).isEqualTo("Mặc Định");

        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.onCreate();
        assertThat(transaction.getStatus()).isEqualTo("PENDING");
        assertThat(transaction.getPaymentDate()).isNotNull();

        BankAccountEntity bankAccount = new BankAccountEntity();
        bankAccount.onCreate();
        assertThat(bankAccount.getIsDeleted()).isZero();
        assertThat(bankAccount.getIsActive()).isZero();
        assertThat(bankAccount.getCreatedAt()).isNotNull();

        CodeRuleEntity rule = new CodeRuleEntity();
        rule.onCreate();
        assertThat(rule.getIsDeleted()).isZero();
        assertThat(rule.getIsActive()).isEqualTo(1);
        assertThat(rule.getSeqLength()).isEqualTo(4);
        assertThat(rule.getLastSeq()).isZero();
        assertThat(rule.getResetCycle()).isEqualTo("YEAR");
        assertThat(rule.getPattern()).isEqualTo("{PREFIX}{YYYY}{SEQ}");
        assertThat(rule.getCreatedAt()).isNotNull();
    }

    @Test
    void onUpdate_setsUpdatedAt() {
        StudentEntity student = new StudentEntity();
        student.onUpdate();
        assertThat(student.getUpdatedAt()).isNotNull();
    }

    @Test
    void userEntity_onCreate_defaultsToStaffType() {
        UserEntity user = new UserEntity();
        user.onCreate();
        assertThat(user.getUserType()).isEqualTo("STAFF");

        UserEntity student = UserEntity.builder().userType("STUDENT").build();
        student.onCreate();
        assertThat(student.getUserType()).isEqualTo("STUDENT");
    }

    @Test
    void userStudentLink_onCreate_defaults() {
        UserStudentLinkEntity link = new UserStudentLinkEntity();
        link.onCreate();
        assertThat(link.getIsDeleted()).isZero();
        assertThat(link.getIsPrimary()).isZero();
        assertThat(link.getStatus()).isEqualTo("ACTIVE");
        assertThat(link.getCreatedAt()).isNotNull();
    }

    @Test
    void allEntitiesAreCovered() {
        List<String> covered = sequencedEntities()
                .map(row -> ((Class<?>) row[0]).getSimpleName())
                .toList();

        assertThat(covered).containsExactlyInAnyOrder(
                "StudentEntity", "ClassEntity", "ClassStudentEntity", "AttendanceEntity",
                "GradeEntity", "LeadEntity", "TuitionFeeEntity", "PaymentTransactionEntity",
                "BankAccountEntity",
                "FileEntity", "UserEntity", "RefreshTokenEntity", "RoleEntity",
                "MenuEntity", "FunctionEntity", "RoleMenuPermissionEntity", "CodeRuleEntity",
                "ClassScheduleEntity", "ClassSessionEntity", "UserStudentLinkEntity");
        assertThat(UserRoleEntity.class.getAnnotation(Entity.class)).isNotNull();
    }

    private static boolean isAssociation(Field field) {
        return field.getAnnotation(ManyToOne.class) != null
                || field.getAnnotation(OneToMany.class) != null;
    }

    private static void assertColumn(Class<?> type, String fieldName, String expectedColumn) throws Exception {
        Column column = type.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertThat(column).as("Thiếu @Column trên %s.%s", type.getSimpleName(), fieldName).isNotNull();
        assertThat(column.name()).isEqualTo(expectedColumn);
    }
}
