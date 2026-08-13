$(function () {
    var TARGET_TABLE_LABEL = {
        ADMIN_USER: "관리자",
        SELL_ORDER: "매도주문",
        SYSTEM_CLOCK: "시스템 시각"
    };
    var REASON_CODE_LABEL = {
        ADMIN_ROLE_UPDATE: "관리자 권한 변경",
        SELL_ORDER_EXECUTED: "매도 체결",
        SELL_ORDER_REJECTED: "매도 반려"
    };
    var ROLE_LABEL = {
        VIEWER: "조회전용",
        REVIEWER: "심사담당",
        SETTLEMENT: "정산담당",
        ADMIN: "최고관리자"
    };
    var DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });
    var PAGE_SIZE = 20;
    var currentPage = 0;
    var totalCount = 0;

    function escapeHtml(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }

    function formatDateTime(value) {
        return value ? DATE_TIME_FORMATTER.format(new Date(value)) : "-";
    }

    var TARGET_TABLE_BADGE_CLASS = {
        ADMIN_USER: "type-admin-user",
        SELL_ORDER: "type-sell-order",
        SYSTEM_CLOCK: "type-system-clock"
    };

    function targetTableLabel(value) {
        return TARGET_TABLE_LABEL[value] || value || "-";
    }

    function targetTableBadgeClass(value) {
        return TARGET_TABLE_BADGE_CLASS[value] || "";
    }

    function reasonCodeLabel(value) {
        return REASON_CODE_LABEL[value] || value || "-";
    }

    function roleLabel(value) {
        return ROLE_LABEL[value] || value || "";
    }

    function showError(message) {
        MARIA.ui.showError(message);
    }

    function buildSearchParams() {
        var params = { page: currentPage, size: PAGE_SIZE };
        var targetTable = $("#auditTargetTableFilter").val();
        var adminKeyword = ($("#auditAdminKeywordFilter").val() || "").trim();
        var targetKeyword = ($("#auditTargetKeywordFilter").val() || "").trim();
        var reasonKeyword = ($("#auditReasonKeywordFilter").val() || "").trim();
        var startDate = $("#auditStartDateFilter").val();
        var endDate = $("#auditEndDateFilter").val();

        if (targetTable) {
            params.targetTable = targetTable;
        }
        if (adminKeyword) {
            params.adminKeyword = adminKeyword;
        }
        if (targetKeyword) {
            params.targetKeyword = targetKeyword;
        }
        if (reasonKeyword) {
            params.reasonKeyword = reasonKeyword;
        }
        if (startDate) {
            params.startDate = startDate;
        }
        if (endDate) {
            params.endDate = endDate;
        }
        return params;
    }

    function renderRows(logs) {
        var $body = $("#auditLogListBody").empty();

        if (!logs.length) {
            $body.append(
                '<tr><td colspan="7" class="audit-log-empty">조회된 감사로그가 없습니다.</td></tr>'
            );
            return;
        }

        logs.forEach(function (log) {
            var adminLabel = log.adminName ? log.adminName : "관리자 ID " + log.adminId;
            var targetLabel = log.targetName ? log.targetName : "#" + log.targetPk;
            $body.append(
                "<tr>" +
                "<td>" + formatDateTime(log.processedAt) + "</td>" +
                "<td><div class=\"audit-log-admin-name\">" + escapeHtml(adminLabel) + "</div>" +
                "<div class=\"audit-log-admin-role\">" + escapeHtml(roleLabel(log.adminRole)) + "</div></td>" +
                "<td><span class=\"audit-log-target-badge " + targetTableBadgeClass(log.targetTable) + "\">" +
                escapeHtml(targetTableLabel(log.targetTable)) + "</span></td>" +
                "<td class=\"audit-log-target-name\">" + escapeHtml(targetLabel) + "</td>" +
                "<td>" + escapeHtml(reasonCodeLabel(log.reasonCode)) + "</td>" +
                "<td class=\"audit-log-before\">" + escapeHtml(log.beforeValue || "-") + "</td>" +
                "<td class=\"audit-log-after\">" + escapeHtml(log.afterValue || "-") + "</td>" +
                "</tr>"
            );
        });
    }

    function renderPagination() {
        var totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
        $("#auditLogPageInfo").text((currentPage + 1) + " / " + totalPages);
        $("#previousAuditLogPage").prop("disabled", currentPage <= 0);
        $("#nextAuditLogPage").prop("disabled", currentPage >= totalPages - 1);
        $("#auditLogPagination").css("display", totalCount > 0 ? "flex" : "none");
    }

    function loadAuditLogs() {
        $("#auditLogListBody").html(
            '<tr><td colspan="7" class="audit-log-loading">불러오는 중...</td></tr>'
        );
        MARIA.auth.ajax({
            url: "/api/admin/audit-logs",
            method: "GET",
            data: buildSearchParams()
        })
            .done(function (res) {
                var page = res.data || { content: [], totalCount: 0 };
                totalCount = page.totalCount || 0;
                $("#auditLogCount").text(totalCount + "건");
                renderRows(page.content || []);
                renderPagination();
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    $("#auditLogListBody").html(
                        '<tr><td colspan="7" class="audit-log-error">감사로그를 불러오지 못했습니다.</td></tr>'
                    );
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "감사로그를 불러오지 못했습니다.");
                }
            });
    }

    $("#auditLogSearch").on("click", function () {
        currentPage = 0;
        loadAuditLogs();
    });

    $("#auditAdminKeywordFilter, #auditTargetKeywordFilter, #auditReasonKeywordFilter").on(
        "keydown",
        function (event) {
            if (event.key === "Enter") {
                currentPage = 0;
                loadAuditLogs();
            }
        }
    );

    $("#auditLogReset").on("click", function () {
        $("#auditTargetTableFilter").val("");
        $("#auditAdminKeywordFilter").val("");
        $("#auditTargetKeywordFilter").val("");
        $("#auditReasonKeywordFilter").val("");
        $("#auditStartDateFilter").val("");
        $("#auditEndDateFilter").val("");
        currentPage = 0;
        loadAuditLogs();
    });

    $("#previousAuditLogPage").on("click", function () {
        if (currentPage > 0) {
            currentPage -= 1;
            loadAuditLogs();
        }
    });

    $("#nextAuditLogPage").on("click", function () {
        var totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
        if (currentPage < totalPages - 1) {
            currentPage += 1;
            loadAuditLogs();
        }
    });

    loadAuditLogs();
});
