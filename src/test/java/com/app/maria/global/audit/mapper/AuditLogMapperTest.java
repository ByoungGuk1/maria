package com.app.maria.global.audit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.maria.global.audit.dto.AuditLogDTO;
import com.app.maria.global.audit.dto.AuditLogSearchDTO;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogMapperTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;
    private AuditLogMapper auditLogMapper;

    @BeforeAll
    static void configureMyBatis() throws IOException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-audit-test-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }
        dataSource =
                (PooledDataSource)
                        sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
    }

    @BeforeEach
    void setUpDatabase() throws SQLException {
        resetSchema();
        sqlSession = sqlSessionFactory.openSession(true);
        auditLogMapper = sqlSession.getMapper(AuditLogMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
    }

    private AuditLogDTO auditLog(
            Long adminId, String targetTable, String targetPk, String reasonCode) {
        return AuditLogDTO.builder()
                .adminId(adminId)
                .targetTable(targetTable)
                .targetPk(targetPk)
                .beforeValue("VIEWER")
                .afterValue("ADMIN")
                .reasonCode(reasonCode)
                .build();
    }

    private AuditLogSearchDTO.AuditLogSearchDTOBuilder searchDefaults() {
        return AuditLogSearchDTO.builder().size(20).offset(0);
    }

    @Test
    @DisplayName("insertLog로 저장하면 1건이 반영된다")
    void insertLogAffectsOneRow() {
        int affected =
                auditLogMapper.insertLog(auditLog(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE"));

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("검색조건이 전부 비어 있으면 전체 목록을 최신순으로 반환한다")
    void selectAuditLogsReturnsAllOrderedByProcessedAtDescWhenNoFilters() throws SQLException {
        insertLogAt(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE", "2026-08-01 09:00:00");
        insertLogAt(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED", "2026-08-05 09:00:00");

        List<AuditLogDTO> result = auditLogMapper.selectAuditLogs(searchDefaults().build());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTargetTable()).isEqualTo("SELL_ORDER");
        assertThat(result.get(1).getTargetTable()).isEqualTo("ADMIN_USER");
    }

    @Test
    @DisplayName("adminId로 필터링한다")
    void selectAuditLogsFiltersByAdminId() {
        auditLogMapper.insertLog(auditLog(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE"));
        auditLogMapper.insertLog(auditLog(2L, "ADMIN_USER", "3", "ADMIN_ROLE_UPDATE"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(searchDefaults().adminId(1L).build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAdminId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("targetTable로 필터링한다")
    void selectAuditLogsFiltersByTargetTable() {
        auditLogMapper.insertLog(auditLog(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE"));
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(searchDefaults().targetTable("SELL_ORDER").build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetTable()).isEqualTo("SELL_ORDER");
    }

    @Test
    @DisplayName("targetPk로 필터링한다")
    void selectAuditLogsFiltersByTargetPk() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(searchDefaults().targetPk("11").build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetPk()).isEqualTo("11");
    }

    @Test
    @DisplayName("reasonCode로 필터링한다")
    void selectAuditLogsFiltersByReasonCode() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "11", "SELL_ORDER_REJECTED"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(
                        searchDefaults().reasonCode("SELL_ORDER_REJECTED").build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReasonCode()).isEqualTo("SELL_ORDER_REJECTED");
    }

    @Test
    @DisplayName("기간(startDate~endDate)으로 필터링한다")
    void selectAuditLogsFiltersByDateRange() throws SQLException {
        insertLogAt(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE", "2026-01-01 09:00:00");
        insertLogAt(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED", "2026-08-05 09:00:00");
        insertLogAt(1L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED", "2026-12-31 09:00:00");

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(
                        searchDefaults()
                                .startDate(LocalDateTime.of(2026, 6, 1, 0, 0))
                                .endDate(LocalDateTime.of(2026, 9, 1, 0, 0))
                                .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetPk()).isEqualTo("10");
    }

    @Test
    @DisplayName("여러 조건을 동시에 걸면 AND로 결합되어 전부 만족하는 행만 반환한다")
    void selectAuditLogsCombinesMultipleFiltersWithAnd() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(2L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(1L, "ADMIN_USER", "3", "ADMIN_ROLE_UPDATE"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(
                        searchDefaults().adminId(1L).targetTable("SELL_ORDER").build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetPk()).isEqualTo("10");
    }

    @Test
    @DisplayName("조건에 맞는 행이 없으면 빈 리스트를 반환한다")
    void selectAuditLogsReturnsEmptyListWhenNoMatch() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));

        List<AuditLogDTO> result =
                auditLogMapper.selectAuditLogs(searchDefaults().adminId(999L).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("admin_user와 join되어 관리자 이름/역할이 함께 조회된다")
    void selectAuditLogsJoinsAdminNameAndRole() throws SQLException {
        insertAdmin(1L, "박지훈", "REVIEWER");
        auditLogMapper.insertLog(auditLog(1L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE"));

        List<AuditLogDTO> result = auditLogMapper.selectAuditLogs(searchDefaults().build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAdminName()).isEqualTo("박지훈");
        assertThat(result.get(0).getAdminRole()).isEqualTo("REVIEWER");
    }

    @Test
    @DisplayName("admin_user에 없는 관리자가 남긴 로그도 조회되고, 이름/역할은 null이다")
    void selectAuditLogsLeavesAdminNameNullWhenAdminUserMissing() {
        auditLogMapper.insertLog(auditLog(999L, "ADMIN_USER", "2", "ADMIN_ROLE_UPDATE"));

        List<AuditLogDTO> result = auditLogMapper.selectAuditLogs(searchDefaults().build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAdminName()).isNull();
        assertThat(result.get(0).getAdminRole()).isNull();
    }

    @Test
    @DisplayName("size/offset으로 페이지를 나눠 최신순으로 반환한다")
    void selectAuditLogsAppliesSizeAndOffset() throws SQLException {
        insertLogAt(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED", "2026-08-01 09:00:00");
        insertLogAt(1L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED", "2026-08-02 09:00:00");
        insertLogAt(1L, "SELL_ORDER", "12", "SELL_ORDER_EXECUTED", "2026-08-03 09:00:00");

        List<AuditLogDTO> firstPage =
                auditLogMapper.selectAuditLogs(
                        AuditLogSearchDTO.builder().size(1).offset(0).build());
        List<AuditLogDTO> secondPage =
                auditLogMapper.selectAuditLogs(
                        AuditLogSearchDTO.builder().size(1).offset(1).build());

        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.get(0).getTargetPk()).isEqualTo("12");
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getTargetPk()).isEqualTo("11");
    }

    @Test
    @DisplayName("countAuditLogs는 size/offset과 무관하게 조건에 맞는 전체 건수를 반환한다")
    void countAuditLogsReturnsTotalMatchingFilterRegardlessOfPaging() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(2L, "ADMIN_USER", "3", "ADMIN_ROLE_UPDATE"));

        long total =
                auditLogMapper.countAuditLogs(
                        AuditLogSearchDTO.builder().targetTable("SELL_ORDER").build());
        List<AuditLogDTO> onePage =
                auditLogMapper.selectAuditLogs(
                        AuditLogSearchDTO.builder()
                                .targetTable("SELL_ORDER")
                                .size(1)
                                .offset(0)
                                .build());

        assertThat(total).isEqualTo(2);
        assertThat(onePage).hasSize(1);
    }

    @Test
    @DisplayName("countAuditLogs도 select와 동일한 조건 필터를 적용한다")
    void countAuditLogsAppliesSameFiltersAsSelect() {
        auditLogMapper.insertLog(auditLog(1L, "SELL_ORDER", "10", "SELL_ORDER_EXECUTED"));
        auditLogMapper.insertLog(auditLog(2L, "SELL_ORDER", "11", "SELL_ORDER_EXECUTED"));

        long total = auditLogMapper.countAuditLogs(AuditLogSearchDTO.builder().adminId(1L).build());

        assertThat(total).isEqualTo(1);
    }

    private void insertAdmin(Long adminId, String name, String role) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO admin_user (admin_id, name, role) VALUES ("
                            + adminId
                            + ", '"
                            + name
                            + "', '"
                            + role
                            + "')");
        }
    }

    private void insertLogAt(
            Long adminId,
            String targetTable,
            String targetPk,
            String reasonCode,
            String processedAt)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO audit_log (admin_id, target_table, target_pk, before_value, after_value, reason_code, processed_at) "
                            + "VALUES ("
                            + adminId
                            + ", '"
                            + targetTable
                            + "', '"
                            + targetPk
                            + "', 'VIEWER', 'ADMIN', '"
                            + reasonCode
                            + "', '"
                            + processedAt
                            + "')");
        }
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(
                    """
                    CREATE TABLE admin_user (
                        admin_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name     VARCHAR(50) NOT NULL,
                        role     VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE audit_log (
                        audit_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
                        admin_id     BIGINT NOT NULL,
                        target_table VARCHAR(50) NOT NULL,
                        target_pk    VARCHAR(50) NOT NULL,
                        before_value TEXT NULL,
                        after_value  TEXT NULL,
                        reason_code  VARCHAR(30) NULL,
                        processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }
}
