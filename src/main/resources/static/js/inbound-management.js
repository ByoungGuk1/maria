$(function () {
    var QTY_FORMATTER = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 4 });
    var DATETIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit"
    });

    var inbounds = [];
    var selectedInboundId = null;
    var PAGE_SIZE = 20;

    function formatDateTime(isoString) {
        if (!isoString) {
            return "-";
        }
        return DATETIME_FORMATTER.format(new Date(isoString)).replace(/\. /g, "-").replace(".", "");
    }

    function formatQty(qty) {
        return QTY_FORMATTER.format(qty || 0) + "주";
    }

    function formatPrice(price, currency) {
        if (price == null) {
            return "-";
        }
        return Number(price).toLocaleString("ko-KR", { maximumFractionDigits: 4 }) + " " + (currency || "");
    }

    function lotProgressCell(lot) {
        var qty = Number(lot.qty) || 0;
        var currentQty = Number(lot.currentQty) || 0;
        var ratio = qty > 0 ? (currentQty / qty) : 0;
        var soldQty = qty - currentQty;
        var isDepleted = currentQty <= 0 && qty > 0;
        var note = soldQty > 0
            ? formatQty(soldQty) + " 소진 (" + Math.round((1 - ratio) * 100) + "%)"
            : "소진 없음";
        return (
            '<div class="ib-lot-progress">' +
            '<div class="ib-lot-progress-label">' + formatQty(currentQty) + ' / ' + formatQty(qty) + '</div>' +
            '<div class="ib-lot-progress-track"><div class="ib-lot-progress-fill' + (isDepleted ? " depleted" : "") + '" style="width:' + Math.round(ratio * 100) + '%"></div></div>' +
            '<div class="ib-lot-progress-note">' + note + '</div>' +
            '</div>'
        );
    }

    var SELL_STATUS_LABEL = { RECEIVED: "접수", EXECUTED: "체결", REJECTED: "거부" };

    function sellHistoryCell(sellHistory) {
        if (!sellHistory || sellHistory.length === 0) {
            return '<span class="ib-sell-history-empty">매도 이력 없음</span>';
        }
        var items = sellHistory.map(function (order) {
            var label = SELL_STATUS_LABEL[order.status] || order.status;
            return (
                '<li>' + label + ' ' + formatQty(order.sellQty) +
                ' @ ' + formatPrice(order.basePrice, null) +
                ' · ' + formatDateTime(order.processedAt) +
                '</li>'
            );
        }).join("");
        return '<ul class="ib-sell-history">' + items + '</ul>';
    }

    function escapeHtml(value) {
        return $("<div>").text(value).html();
    }

    function renderList() {
        var $items = $("#ibListItems").empty();
        $("#ibListCount").text(inbounds.length + "건 수신");
        if (inbounds.length === 0) {
            $items.append('<div class="dash-empty">입고 이력이 없습니다.</div>');
            return;
        }
        inbounds.forEach(function (item) {
            var isSelected = item.inboundId === selectedInboundId;
            var $row = $(
                '<div class="ib-list-item' + (isSelected ? " selected" : "") + '">' +
                '<div class="ib-list-item-top">' +
                '<span class="ib-list-item-name">' + escapeHtml(item.customerName) + '</span>' +
                '<span class="ib-list-item-ticker">[' + escapeHtml(item.ticker || item.productName) + ']</span>' +
                '</div>' +
                '<div class="ib-list-item-bottom">' +
                '<span>계좌 ' + escapeHtml(item.accountNo || "-") + ' · ' + formatDateTime(item.processedAt) + '</span>' +
                (item.approvedQty === 0
                    ? '<span class="status-badge failed">한도초과 · 0주 승인</span>'
                    : '<span>승인수량: <strong>' + formatQty(item.approvedQty) + '</strong></span>') +
                '</div>' +
                '</div>'
            );
            $row.on("click", function () {
                selectedInboundId = item.inboundId;
                renderList();
                renderDetail(item);
            });
            $items.append($row);
        });
    }

    function renderPagination(page) {
        var $pagination = $("#ibPagination").empty();
        if (!page || page.totalPages <= 1) {
            return;
        }

        var current = page.page;
        var totalPages = page.totalPages;
        var BLOCK_SIZE = 10;
        var blockStart = Math.floor(current / BLOCK_SIZE) * BLOCK_SIZE;
        var blockEnd = Math.min(totalPages - 1, blockStart + BLOCK_SIZE - 1);

        function addButton(label, targetPage, isDisabled, isActive) {
            var classes = "page-btn" + (isActive ? " active" : "");
            var $btn = $('<button type="button" class="' + classes + '">' + label + "</button>");
            $btn.prop("disabled", isDisabled || isActive);
            if (!isDisabled && !isActive) {
                $btn.on("click", function () {
                    loadInbounds(targetPage);
                });
            }
            $pagination.append($btn);
        }

        addButton("이전", blockStart - 1, blockStart === 0, false);

        for (var i = blockStart; i <= blockEnd; i++) {
            addButton(String(i + 1), i, false, i === current);
        }

        addButton("다음", blockEnd + 1, blockEnd === totalPages - 1, false);
    }

    function minCard(label, value, isMatched, note) {
        var cls = "ib-min-card" + (isMatched ? " matched" : "");
        return (
            '<div class="' + cls + '">' +
            (isMatched ? '<span class="ib-min-badge">MIN 채택</span>' : "") +
            '<div class="ib-min-label">' + label + '</div>' +
            '<div class="ib-min-value">' + formatQty(value) + '</div>' +
            '<div class="ib-min-note">' + note + '</div>' +
            '</div>'
        );
    }

    function zeroApprovalReason(item) {
        if (item.currentHoldingAtRequest === 0) {
            return "요청 시점 기준 현재 보유수량이 0주라 입고할 자산이 없습니다.";
        }
        if (item.requestedQty === 0) {
            return "신청수량 자체가 0주로 접수됐습니다.";
        }
        return "이 계좌·종목으로 이미 승인된 누적수량이 12.23 기준수량을 다 채웠습니다.";
    }

    function renderDetail(item) {
        var $detail = $("#ibDetail").empty();

        var isZeroApproved = item.approvedQty === 0;
        var availableQty = item.remainingQty + item.approvedQty;
        $detail.append(
            '<div class="section-header">' +
            '<span>3-way MIN 계산 — ' + escapeHtml(item.accountNo || "-") + ' · ' + escapeHtml(item.customerName) + ' · ' + escapeHtml(item.ticker || item.productName) + '</span>' +
            '<span class="section-sub' + (isZeroApproved ? " ib-zero-text" : "") + '">최종 채택: ' + formatQty(item.approvedQty) + '</span>' +
            '</div>' +
            (isZeroApproved
                ? '<div class="ib-zero-warning">이번 요청은 반려됐습니다 — ' + zeroApprovalReason(item) + '</div>'
                : "")
        );

        $detail.append(
            '<div class="ib-min-grid">' +
            minCard("1. 신청수량", item.requestedQty, item.requestedQty === item.approvedQty, "고객 입고 신청 수량") +
            minCard("2. 가용수량(기준수량 - 기승인)", availableQty, availableQty === item.approvedQty, "12.23 기준수량 " + formatQty(item.snapshotQty) + " 중 이미 승인된 수량 차감") +
            minCard("3. 현재보유수량", item.currentHoldingAtRequest, item.currentHoldingAtRequest === item.approvedQty, "요청시점 실보유수량") +
            '</div>'
        );

        $detail.append(
            '<div class="ib-min-formula">' +
            'MIN(' + formatQty(item.requestedQty) + ', ' + formatQty(availableQty) + ', ' + formatQty(item.currentHoldingAtRequest) + ') = ' +
            '<strong>' + formatQty(item.approvedQty) + '</strong>' +
            '</div>'
        );

        $detail.append(
            '<div class="ib-min-info">' +
            '<span>계좌: ' + escapeHtml(item.accountNo || "-") + '</span>' +
            '<span>고객: ' + escapeHtml(item.customerName) + '</span>' +
            '<span>종목: ' + escapeHtml(item.ticker || "-") + ' (' + escapeHtml(item.productName || "-") +
            ')</span>' +
            (item.sourceBroker ? '<span>출처: ' + escapeHtml(item.sourceBroker) + '</span>' : "") +
            '<span>처리일시: ' + formatDateTime(item.processedAt) + '</span>' +
            '<span>잔여 가능 수량: <strong>' + formatQty(item.remainingQty) + '</strong></span>' +
            '</div>'
        );

        if (item.lots && item.lots.length > 0) {
            var lotRows = item.lots.map(function (lot) {
                return (
                    '<tr>' +
                    '<td>' + escapeHtml(lot.sourceBroker || "당사") + '</td>' +
                    '<td>' + formatDateTime(lot.purchaseDate) + '</td>' +
                    '<td>' + formatDateTime(lot.recordedAt) + '</td>' +

                    '<td>' + formatPrice(lot.purchasePrice, lot.purchaseCurrency) + '</td>' +
                    '<td>' + lotProgressCell(lot) + '</td>' +
                    '<td>' + sellHistoryCell(lot.sellHistory) + '</td>' +
                    '</tr>'
                );
            }).join("");

            $detail.append(
                '<div class="section-header">' +
                '<span>취득 정보</span>' +
                '<span class="section-sub">lot ' + item.lots.length + '건</span>' +
                '</div>' +
                '<table class="dash-table">' +
                '<thead><tr><th>출처</th><th>매수일</th><th>기록일</th><th>매수단가</th><th>보유 현황</th><th>매도 이력</th></tr></thead>' +
                '<tbody>' + lotRows + '</tbody>' +
                '</table>'
            );
        }
    }

    function loadInbounds(page) {
        var targetPage = page || 0;
        MARIA.auth.ajax({
            url: "/api/inbounds",
            method: "GET",
            data: { page: targetPage, size: PAGE_SIZE }
        })
            .done(function (res) {
                inbounds = res.data.content || [];
                selectedInboundId = null;
                renderList();
                renderPagination(res.data);
                if (inbounds.length > 0) {
                    selectedInboundId = inbounds[0].inboundId;
                    renderList();
                    renderDetail(inbounds[0]);
                } else {
                    $("#ibDetail").empty().append('<div class="dash-empty">왼쪽에서 입고 건을 선택하세요.</div>');
                }
                $("#ibLoading").hide();
                $("#ibBody").show();
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                $("#ibLoading").hide();
                var message = "목록을 불러오지 못했습니다.";
                if (xhr.responseJSON && xhr.responseJSON.message) {
                    message = xhr.responseJSON.message;
                }
                $("#ibError").text(message).show();
            });
    }

    loadInbounds(0);
});
