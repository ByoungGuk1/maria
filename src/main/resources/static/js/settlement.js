$(function () {
    var batches = [];
    var selectedBatchId = null;
    var currentPage = 1;
    var PAGE_SIZE = 5;
    var items = [];
    var allBatchItems = [];
    var selectedItemId = null;
    var currentItemPage = 1;
    var ITEM_PAGE_SIZE = 10;
    var itemFilter = "all";
    var LABELS = { RUNNING: "진행 중", COMPLETED: "완료", FAILED: "실패", SUCCESS: "성공" };
    var DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var RATE_FORMATTER = new Intl.NumberFormat("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
    var batchTrendChart = null;
    var ITEM_TREND_GROUPS = [
        { key: "total", label: "전체", color: "#64748b" },
        { key: "success", label: "성공", color: "#16a34a" },
        { key: "failed", label: "실패", color: "#dc2626" }
    ];

    function escapeHtml(value) { return $("<div>").text(value == null ? "-" : value).html(); }
    function toCount(value) { return Number(value || 0); }
    function formatDateTime(value) { return value ? DATE_TIME_FORMATTER.format(new Date(value)) : "-"; }
    function formatAmount(value) { return value == null ? "-" : "₩" + KRW_FORMATTER.format(value); }
    function formatRate(value) { return value == null ? "-" : RATE_FORMATTER.format(value); }
    function showError(message) { MARIA.ui.showError(message); }
    function statusBadge(status) { var key = (status || "").toLowerCase(); return '<span class="settlement-status ' + key + '">' + escapeHtml(LABELS[status] || status) + "</span>"; }
    function errorMessage(xhr, fallback) { return (xhr.responseJSON && xhr.responseJSON.message) || fallback; }
    function handleRequestFailure(xhr, fallback) { if (xhr.status !== 401) showError(errorMessage(xhr, fallback)); }

    function paginate(list, page, pageSize) {
        var totalPages = Math.max(1, Math.ceil(list.length / pageSize));
        var current = Math.min(page, totalPages);
        return { current: current, total: totalPages, items: list.slice((current - 1) * pageSize, current * pageSize) };
    }

    function renderPagination(containerSelector, infoSelector, previousSelector, nextSelector, page) {
        $(infoSelector).text(page.current + " / " + page.total);
        $(previousSelector).prop("disabled", page.current === 1);
        $(nextSelector).prop("disabled", page.current === page.total);
        $(containerSelector).css("display", "flex");
    }

    function startOfDay(value) {
        var date = new Date(value);
        date.setHours(0, 0, 0, 0);
        return date;
    }

    function itemCounts(batchList) {
        return batchList.reduce(function (counts, batch) {
            var total = toCount(batch.totalCount);
            var processed = toCount(batch.processedCount);
            counts.total += total;
            counts.success += toCount(batch.successCount);
            counts.failed += toCount(batch.failedCount);
            counts.processed += processed;
            return counts;
        }, { total: 0, success: 0, failed: 0, processed: 0 });
    }

    function batchesExecutedOn(date) {
        return batches.filter(function (batch) {
            if (!batch.executedAt) return false;
            return startOfDay(batch.executedAt).getTime() === date.getTime();
        });
    }

    function formatDate(date) {
        return date.getFullYear() + "." + String(date.getMonth() + 1).padStart(2, "0") + "." + String(date.getDate()).padStart(2, "0");
    }

    function renderBatchCount() {
        $("#settlementBatchCount").text(batches.length + "건");
    }

    function renderBatchTrend() {
        var latestExecutedAt = batches.reduce(function (latest, batch) {
            if (!batch.executedAt) return latest;
            var executedAt = new Date(batch.executedAt);
            return !latest || executedAt > latest ? executedAt : latest;
        }, null);
        var latestDate = startOfDay(latestExecutedAt || new Date());
        var dates = Array.from({ length: 14 }, function (_, index) {
            var date = new Date(latestDate);
            date.setDate(latestDate.getDate() - 13 + index);
            return date;
        });
        var datasets = ITEM_TREND_GROUPS.map(function (group) {
            return {
                label: group.label,
                data: dates.map(function (date) {
                    return itemCounts(batchesExecutedOn(date))[group.key];
                }),
                borderColor: group.color,
                backgroundColor: group.color,
                tension: 0.3,
                borderWidth: 2,
                pointRadius: 3,
                pointHoverRadius: 5,
                pointHitRadius: 12
            };
        });
        var labels = dates.map(function (date) { return (date.getMonth() + 1) + "/" + date.getDate(); });
        $("#settlementTrendTotal").text(itemCounts(batches).total + "건");
        if (batchTrendChart) batchTrendChart.destroy();
        batchTrendChart = new Chart($("#settlementBatchTrend")[0], {
            type: "line",
            data: { labels: labels, datasets: datasets },
            options: {
                animation: false,
                maintainAspectRatio: false,
                plugins: { legend: { display: false }, tooltip: { displayColors: false, callbacks: { label: function (context) { return context.dataset.label + ": " + context.parsed.y + "건"; } } } },
                scales: { x: { grid: { display: false }, ticks: { color: "#64748b", font: { size: 10 } } }, y: { beginAtZero: true, ticks: { precision: 0, color: "#64748b", font: { size: 10 } }, grid: { color: "#e2e8f0" } } },
                onClick: function (_, elements) { if (elements.length) renderTrendDetail(dates[elements[0].index]); }
            }
        });
        renderTrendDetail(latestDate);
    }

    function renderTrendDetail(date) {
        var counts = itemCounts(batchesExecutedOn(date));
        $("#settlementTrendDetailDate").text(formatDate(date) + " 정산 항목 상세");
        $("#settlementTrendDetailTotal").text(counts.total + "건");
        $("#settlementTrendDetailSuccess").text(counts.success + "건");
        $("#settlementTrendDetailFailed").text(counts.failed + "건");
        $("#settlementTrendDetailProcessed").text(counts.processed + "건");
        $("#settlementTrendDetail").css("display", "block");
    }

    function renderBatches() {
        var $body = $("#settlementBatchBody").empty();
        var page = paginate(batches, currentPage, PAGE_SIZE);
        currentPage = page.current;
        if (!batches.length) { $body.append('<tr><td colspan="8" class="settlement-empty">실행 이력이 없습니다.</td></tr>'); $("#settlementPagination").hide(); return; }
        page.items.forEach(function (batch) {
            $body.append('<tr class="settlement-batch-row' + (batch.batchId === selectedBatchId ? " is-selected" : "") + '" data-batch-id="' + batch.batchId + '">' +
                "<td>#" + batch.batchId + "</td><td>" + formatDateTime(batch.executedAt) + "</td><td>" + statusBadge(batch.status) + "</td>" +
                "<td>" + batch.totalCount + "</td><td>" + batch.successCount + "</td><td>" + batch.failedCount + "</td><td>" + batch.processedCount + "</td><td>" + escapeHtml(batch.runId) + "</td></tr>");
        });
        renderPagination("#settlementPagination", "#settlementPageInfo", "#previousSettlementPage", "#nextSettlementPage", page);
    }

    function renderItems(itemList) {
        var $body = $("#settlementItemBody").empty();
        var page = paginate(itemList, currentItemPage, ITEM_PAGE_SIZE);
        currentItemPage = page.current;
        if (!itemList.length) { $body.append('<tr><td colspan="8" class="settlement-empty">정산 항목이 없습니다.</td></tr>'); $("#settlementItemPagination").hide(); return; }
        page.items.forEach(function (item) {
            $body.append('<tr class="settlement-item-row' + (item.itemId === selectedItemId ? " is-selected" : "") + '" data-item-id="' + item.itemId + '"><td>#' + item.itemId + "</td><td>" + escapeHtml(item.accountNo) + "</td><td>" + escapeHtml(item.ticker) + "</td><td>" + formatAmount(item.provisionalAmount) + "</td><td>" + formatAmount(item.finalAmount) + "</td><td>" + statusBadge(item.result) + "</td><td>" + escapeHtml(item.failureCode || item.failureMessage) + "</td><td>" + formatDateTime(item.processedAt) + "</td></tr>");
        });
        renderPagination("#settlementItemPagination", "#settlementItemPageInfo", "#previousSettlementItemPage", "#nextSettlementItemPage", page);
    }

    function selectBatch(batchId, preserveItemPage) {
        selectedBatchId = Number(batchId);
        if (!preserveItemPage) {
            itemFilter = "all";
            currentItemPage = 1;
        }
        MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId, method: "GET" })
            .done(function (res) {
                var latestBatch = res.data;
                var batchIndex = batches.findIndex(function (item) { return item.batchId === selectedBatchId; });
                if (batchIndex !== -1) {
                    batches[batchIndex] = latestBatch;
                }
                renderBatchCount();
                renderBatches();
                renderDetail(latestBatch);
                $("#settlementItemFilter").val(itemFilter);
                loadBatchItems(preserveItemPage);
            })
            .fail(function (xhr) { handleRequestFailure(xhr, "배치 상태를 불러오지 못했습니다."); });
    }

    function renderDetail(batch) {
        $("#settlementDetail").show();
        $("#detailBatchTitle").text("배치 #" + selectedBatchId + " 상세");
        $("#detailBatchFailure").text(batch.failureMessage || "");
        $("#detailBatchExecutedAt").text(formatDateTime(batch.executedAt));
        $("#detailBatchRunId").text(batch.runId || "-");
        $("#detailBatchTotalCount").text(batch.totalCount + "건");
        $("#detailBatchSuccessCount").text(batch.successCount + "건");
        $("#detailBatchFailedCount").text(batch.failedCount + "건");
        $("#detailBatchProcessedCount").text(batch.processedCount + "건");
        $("#retrySettlementBatch").toggle(batch.status === "FAILED");
        $("#settlementItemFilter").toggle(batch.status === "FAILED");
    }

    function loadBatchItems(preserveItemPage) {
        $("#settlementItemBody").html('<tr><td colspan="8" class="settlement-empty">불러오는 중...</td></tr>');
        var url = itemFilter === "failed"
            ? "/api/settlement/batches/detail/fail/" + selectedBatchId
            : "/api/settlement/batches/detail/" + selectedBatchId;
        MARIA.auth.ajax({ url: url, method: "GET" })
            .done(function (res) {
                allBatchItems = res.data || [];
                items = itemFilter === "success"
                    ? allBatchItems.filter(function (item) { return item.result === "SUCCESS"; })
                    : allBatchItems;
                if (!preserveItemPage) {
                    currentItemPage = 1;
                    selectedItemId = null;
                    $("#settlementItemDetail").hide();
                }
                renderItems(items);
            })
            .fail(function (xhr) { if (xhr.status !== 401) $("#settlementItemBody").empty(); handleRequestFailure(xhr, "정산 항목을 불러오지 못했습니다."); });
    }

    function selectItem(itemId) {
        selectedItemId = Number(itemId);
        MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId + "/items/" + selectedItemId, method: "GET" })
            .done(function (res) {
                var item = res.data;
                renderItems(items);
                $("#settlementItemDetail").show();
                $("#detailItemTitle").text("정산 항목 #" + item.itemId + " 상세");
                $("#detailItemFailure").text(item.failureMessage || "");
                $("#detailItemExchangeId").text(item.exchangeId || "-");
                $("#detailItemAccountId").text(item.accountId || "-");
                $("#detailItemAccountNo").text(item.accountNo || "-");
                $("#detailItemOrderId").text(item.orderId || "-");
                $("#detailItemProduct").text([item.ticker, item.productName].filter(Boolean).join(" · ") || "-");
                $("#detailItemExchangeStatus").text(item.settlementStatus || "-");
                $("#detailItemFailureCode").text(item.failureCode || "-");
                $("#detailItemProvisionalAmount").text(formatAmount(item.provisionalAmount));
                $("#detailItemProvisionalAt").text(formatDateTime(item.provisionalAt));
                $("#detailItemProvisionalRate").text(formatRate(item.settlementFxRate));
                $("#detailItemFinalAmount").text(formatAmount(item.finalAmount));
                $("#detailItemFinalAt").text(formatDateTime(item.finalAt));
                $("#detailItemFinalRate").text(formatRate(item.finalRate));
                var difference = item.finalAmount == null || item.provisionalAmount == null ? null : Number(item.finalAmount) - Number(item.provisionalAmount);
                $("#detailItemDifference").text(difference == null ? "-" : (difference > 0 ? "+" : "") + formatAmount(difference));
                renderRetryHistory(item.exchangeId);
                $("#retrySettlementItem").toggle(item.result === "FAILED");
            })
            .fail(function (xhr) { handleRequestFailure(xhr, "정산 항목 상세를 불러오지 못했습니다."); });
    }

    function renderRetryHistory(exchangeId) {
        var $history = $("#settlementRetryHistory").empty();
        var history = allBatchItems.filter(function (item) { return item.exchangeId === exchangeId; })
            .sort(function (left, right) { return Number(right.itemId) - Number(left.itemId); });
        if (!history.length) {
            $history.append("<li>처리 이력이 없습니다.</li>");
            return;
        }
        history.forEach(function (historyItem) {
            $history.append("<li><strong>Item #" + historyItem.itemId + " " + escapeHtml(LABELS[historyItem.result] || historyItem.result || "대기") + "</strong><span>" + formatDateTime(historyItem.processedAt) + "</span><small>" + escapeHtml(historyItem.failureCode || historyItem.failureMessage || "처리 완료") + "</small></li>");
        });
    }

    function loadBatches() {
        MARIA.auth.ajax({ url: "/api/settlement/batches", method: "GET" }).done(function (res) {
            batches = res.data || []; renderBatchCount(); renderBatchTrend(); renderBatches();
        }).fail(function (xhr) { if (xhr.status !== 401) $("#settlementBatchBody").empty(); handleRequestFailure(xhr, "배치 목록을 불러오지 못했습니다."); });
    }

    $(document).on("click", ".settlement-batch-row", function () { selectBatch($(this).data("batch-id")); });
    $(document).on("click", ".settlement-item-row", function () { selectItem($(this).data("item-id")); });
    $("#previousSettlementPage").on("click", function () { if (currentPage > 1) { currentPage -= 1; renderBatches(); } });
    $("#nextSettlementPage").on("click", function () { if (currentPage < Math.ceil(batches.length / PAGE_SIZE)) { currentPage += 1; renderBatches(); } });
    $("#previousSettlementItemPage").on("click", function () { if (currentItemPage > 1) { currentItemPage -= 1; renderItems(items); } });
    $("#nextSettlementItemPage").on("click", function () { if (currentItemPage < Math.ceil(items.length / ITEM_PAGE_SIZE)) { currentItemPage += 1; renderItems(items); } });
    $("#settlementItemFilter").on("change", function () {
        itemFilter = $(this).val();
        loadBatchItems();
    });
    $("#executeSettlement").on("click", function () { MARIA.auth.ajax({ url: "/api/settlement/jobs", method: "POST" }).done(function (res) { selectedBatchId = res.data.batchId; currentPage = 1; loadBatches(); }).fail(function (xhr) { handleRequestFailure(xhr, "정산 배치 실행에 실패했습니다."); }); });
    $("#retrySettlementBatch").on("click", function () { if (!selectedBatchId) return; MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId + "/retry", method: "POST" }).done(function () { loadBatches(); }).fail(function (xhr) { handleRequestFailure(xhr, "정산 배치 재처리에 실패했습니다."); }); });
    $("#retrySettlementItem").on("click", function () { if (!selectedBatchId || !selectedItemId) return; MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId + "/items/" + selectedItemId + "/retry", method: "POST" }).done(function () { selectBatch(selectedBatchId, true); }).fail(function (xhr) { handleRequestFailure(xhr, "정산 항목 재처리에 실패했습니다."); }); });
    loadBatches();
});
