$(function () {
    var STOCK_TYPE_LABEL = {
        FOREIGN_STOCK: "해외주식",
        ETF: "EFT",
        ETN: "ETN",
        FUND: "펀드"
        };
    var TRADE_TYPE_LABEL = {
        BUY: "매수",
        SELL: "매도",
        INHERITANCE: "상속",
        GIFT: "증여"
    };
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
        var $body = $(#tpTableBody).empty();
        if (!items || items.length === 0) {
            $body.append('<tr><td colsapn="8" class="dash-empty">탐지된 이벤트가 없습니다.</td></tr>');
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

    function loadTargetProducts() {
        MARIA.auth.ajax({
            url: "/api/target-products",
            method: "GET"
        })
            .done(function (xhr) {
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

    loadTargetProducts();
});
