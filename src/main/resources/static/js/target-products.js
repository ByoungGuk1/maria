$(function () {
    var STOCK_TYPE_LABEL = {
        FOREIGN_STOCK: "해외주식",
        ETF: "ETF",
        ETN: "ETN",
        FUND: "펀드"
        };
    var TRADE_TYPE_LABEL = {
        BUY: "매수",
        SELL: "매도",
        INHERITANCE: "상속",
        GIFT: "증여"
    };
    var PAGE_SIZE = 20;

    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var DATETIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit"
    });

    function formatDateTime(isoString) {
        if (!isoString) {
            return "-";
        }
        return DATETIME_FORMATTER.format(new Date(isoString)).replace(/\. /g, "-").replace(".", "");
    }
    function formatAmount(amount) {
        return "₩" + KRW_FORMATTER.format(amount || 0);
    }
    function escapeHtml(value) {
        return $("<div>").text(value).html();
    }
    function assetLabel(item) {
        if (item.stockType === "FUND") {
            return escapeHtml(item.fundName || "-");
        }
        return escapeHtml(item.ticker || "-");
    }

    function renderTable(items) {
        var $body = $("#tpTableBody").empty();
        if (!items || items.length === 0) {
            $body.append('<tr><td colspan="8" class="dash-empty">탐지된 이벤트가 없습니다.</td></tr>');
            return;
        }
        items.forEach(function (item) {
            var netBuyClass = Number(item.netBuyAmount) < 0 ? ' style="color:var(--danger)"' : "";
            var targetBadge = item.isTarget
                ? '<span class="status-badge completed">대상</span>'
                : '<span class="status-badge failed">비대상</span>';
            var row =
                "<tr>" +
                "<td>" + formatDateTime(item.judgedAt) + "</td>" +
                "<td>" + escapeHtml(item.customerName || "-") + "</td>" +
                "<td>" + (STOCK_TYPE_LABEL[item.stockType] || item.stockType) + "</td>" +
                "<td>" + assetLabel(item) + "</td>" +
                "<td>" + (TRADE_TYPE_LABEL[item.tradeType] || item.tradeType) + "</td>" +
                "<td>" + formatAmount(item.amount) + "</td>" +
                "<td" + netBuyClass + ">" + formatAmount(item.netBuyAmount) + "</td>" +
                "<td>" + targetBadge + "</td>" +
                "</tr>";
            $body.append(row);
        });
    }

    function renderPagination(page) {
        var $pagination = $("#tpPagination").empty();
        if (!page || page.totalPages <= 1) {
            return;
        }

        var current = page.page;
        var totalPages = page.totalPages;

        function addButton(label, targetPage, isDisabled, isActive) {
            var classes = "page-btn" + (isActive ? " active" : "");
            var $btn = $('<button type="button" class="' + classes + '">' + label + "</button>");
            $btn.prop("disabled", isDisabled || isActive);
            if (!isDisabled && !isActive) {
                $btn.on("click", function () {
                    loadTargetProducts(targetPage);
                });
            }
            $pagination.append($btn);
        }

        addButton("이전", current - 1, current === 0, false);

        var windowSize = 2;
        var start = Math.max(0, current - windowSize);
        var end = Math.min(totalPages - 1, current + windowSize);

        if (start > 0) {
            addButton("1", 0, false, false);
            if (start > 1) {
                $pagination.append('<span class="page-ellipsis">...</span>');
            }
        }
        for (var i = start; i <= end; i++) {
            addButton(String(i + 1), i, false, i === current);
        }
        if (end < totalPages - 1) {
            if (end < totalPages - 2) {
                $pagination.append('<span class="page-ellipsis">...</span>');
            }
            addButton(String(totalPages), totalPages - 1, false, false);
        }

        addButton("다음", current + 1, current === totalPages - 1, false);
    }

    function renderSummary(summary) {
        $("#kpiTodayJudgement").text(summary.todayJudgementCount + " 건");
        $("#kpiTodayTarget").text(summary.todayTargetCount + " 건");
        $("#kpiTodayTargetAmount").text(formatAmount(summary.todayTargetNetBuyAmount));
        $("#kpiTotalJudgement").text(summary.totalJudgementCount + " 건");
    }

    function loadSummary() {
        MARIA.auth.ajax({
            url: "/api/target-products/summary",
            method: "GET"
        })
            .done(function (res) {
                renderSummary(res.data);
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
            });
    }

    function getFilterParams() {
        var params = {};
        var customerName = $("#tpFilterCustomerName").val();
        if (customerName) {
            params.customerName = customerName;
        }
        var stockType = $("#tpFilterStockType").val();
        if (stockType) {
            params.stockType = stockType;
        }
        var isTarget = $("#tpFilterIsTarget").val();
        if (isTarget) {
            params.isTarget = isTarget;
        }
        return params;
    }

    function loadTargetProducts(page) {
        var targetPage = page || 0;
        var requestData = $.extend({ page: targetPage, size: PAGE_SIZE }, getFilterParams());
        MARIA.auth.ajax({
            url: "/api/target-products",
            method: "GET",
            data: requestData
        })
            .done(function (res) {
                renderTable(res.data.content);
                renderPagination(res.data);
                $("#tpLoading").hide();
                $("#tpBody").show();
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                $("#tpLoading").hide();
                var message = "목록을 불러오지 못했습니다.";
                if (xhr.responseJSON && xhr.responseJSON.message) {
                    message = xhr.responseJSON.message;
                }
                $("#tpError").text(message).show();
            });
    }

    $("#tpFilterSubmit").on("click", function () {
        loadTargetProducts(0);
    });
    $("#tpFilterReset").on("click", function () {
        $("#tpFilterCustomerName").val("");
        $("#tpFilterStockType").val("");
        $("#tpFilterIsTarget").val("");
        loadTargetProducts(0);
    });
    $("#tpFilterCustomerName").on("keypress", function (e) {
        if (e.which === 13) {
            loadTargetProducts(0);
        }
    });

    loadSummary();
    loadTargetProducts(0);
});
