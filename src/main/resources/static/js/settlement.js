$(function () {
    var batches = [];
    var selectedBatchId = null;
    var currentPage = 1;
    var PAGE_SIZE = 20;
    var items = [];
    var allBatchItems = [];
    var selectedItemId = null;
    var currentItemPage = 1;
    var ITEM_PAGE_SIZE = 10;
    var itemFilter = "all";
    var LABELS = { RUNNING: "진행 중", COMPLETED: "완료", FAILED: "실패", SUCCESS: "성공" };

    function escapeHtml(value) { return $("<div>").text(value == null ? "-" : value).html(); }
    function formatDateTime(value) { return value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-"; }
    function formatAmount(value) { return value == null ? "-" : "₩" + new Intl.NumberFormat("ko-KR").format(value); }
    function formatRate(value) { return value == null ? "-" : new Intl.NumberFormat("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 6 }).format(value); }
    function showError(message) { MARIA.ui.showError(message); }
    function statusBadge(status) { var key = (status || "").toLowerCase(); return '<span class="settlement-status ' + key + '">' + escapeHtml(LABELS[status] || status) + "</span>"; }

    function renderSummary() {
        var latest = batches[0];
        $("#totalBatchCount").text(batches.length);
        $("#runningBatchCount").text(batches.filter(function (batch) { return batch.status === "RUNNING"; }).length);
        $("#failedBatchCount").text(batches.filter(function (batch) { return batch.status === "FAILED"; }).length);
        $("#latestProcessedCount").text(latest ? latest.processedCount + "건" : "-");
        $("#settlementBatchCount").text(batches.length + "건");
    }

    function renderBatches() {
        var $body = $("#settlementBatchBody").empty();
        var totalPages = Math.max(1, Math.ceil(batches.length / PAGE_SIZE));
        currentPage = Math.min(currentPage, totalPages);
        var pageBatches = batches.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
        if (!batches.length) { $body.append('<tr><td colspan="8" class="settlement-empty">실행 이력이 없습니다.</td></tr>'); $("#settlementPagination").hide(); return; }
        pageBatches.forEach(function (batch) {
            $body.append('<tr class="settlement-batch-row' + (batch.batchId === selectedBatchId ? " is-selected" : "") + '" data-batch-id="' + batch.batchId + '">' +
                "<td>#" + batch.batchId + "</td><td>" + formatDateTime(batch.executedAt) + "</td><td>" + statusBadge(batch.status) + "</td>" +
                "<td>" + batch.totalCount + "</td><td>" + batch.successCount + "</td><td>" + batch.failedCount + "</td><td>" + batch.processedCount + "</td><td>" + escapeHtml(batch.runId) + "</td></tr>");
        });
        $("#settlementPageInfo").text(currentPage + " / " + totalPages);
        $("#previousSettlementPage").prop("disabled", currentPage === 1);
        $("#nextSettlementPage").prop("disabled", currentPage === totalPages);
        $("#settlementPagination").css("display", "flex");
    }

    function renderItems(items) {
        var $body = $("#settlementItemBody").empty();
        var totalPages = Math.max(1, Math.ceil(items.length / ITEM_PAGE_SIZE));
        currentItemPage = Math.min(currentItemPage, totalPages);
        var pageItems = items.slice((currentItemPage - 1) * ITEM_PAGE_SIZE, currentItemPage * ITEM_PAGE_SIZE);
        if (!items.length) { $body.append('<tr><td colspan="8" class="settlement-empty">정산 항목이 없습니다.</td></tr>'); $("#settlementItemPagination").hide(); return; }
        pageItems.forEach(function (item) {
            $body.append('<tr class="settlement-item-row' + (item.itemId === selectedItemId ? " is-selected" : "") + '" data-item-id="' + item.itemId + '"><td>#' + item.itemId + "</td><td>" + escapeHtml(item.accountNo) + "</td><td>" + escapeHtml(item.ticker) + "</td><td>" + formatAmount(item.provisionalAmount) + "</td><td>" + formatAmount(item.finalAmount) + "</td><td>" + statusBadge(item.result) + "</td><td>" + escapeHtml(item.failureCode || item.failureMessage) + "</td><td>" + formatDateTime(item.processedAt) + "</td></tr>");
        });
        $("#settlementItemPageInfo").text(currentItemPage + " / " + totalPages);
        $("#previousSettlementItemPage").prop("disabled", currentItemPage === 1);
        $("#nextSettlementItemPage").prop("disabled", currentItemPage === totalPages);
        $("#settlementItemPagination").css("display", "flex");
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
                renderSummary();
                renderBatches();
                renderDetail(latestBatch);
                updateItemFilter();
                loadBatchItems(preserveItemPage);
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "배치 상태를 불러오지 못했습니다.");
                }
            });
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
        var url = "/api/settlement/batches/detail/" + selectedBatchId;
        MARIA.auth.ajax({ url: url, method: "GET" })
            .done(function (res) {
                allBatchItems = res.data || [];
                items = allBatchItems.filter(function (item) {
                    return itemFilter === "all" || (itemFilter === "success" && item.result === "SUCCESS") || (itemFilter === "failed" && item.result === "FAILED");
                });
                if (!preserveItemPage) {
                    currentItemPage = 1;
                    selectedItemId = null;
                    $("#settlementItemDetail").hide();
                }
                renderItems(items);
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    $("#settlementItemBody").empty();
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "정산 항목을 불러오지 못했습니다.");
                }
            });
    }

    function updateItemFilter() {
        $("#settlementItemFilter").val(itemFilter);
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
            .fail(function (xhr) { if (xhr.status !== 401) { showError((xhr.responseJSON && xhr.responseJSON.message) || "정산 항목 상세를 불러오지 못했습니다."); } });
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
            batches = res.data || []; renderSummary(); renderBatches();
        }).fail(function (xhr) { if (xhr.status !== 401) { $("#settlementBatchBody").empty(); showError((xhr.responseJSON && xhr.responseJSON.message) || "배치 목록을 불러오지 못했습니다."); } });
    }

    $(document).on("click", ".settlement-batch-row", function () { selectBatch($(this).data("batch-id")); });
    $(document).on("click", ".settlement-item-row", function () { selectItem($(this).data("item-id")); });
    $("#previousSettlementPage").on("click", function () { if (currentPage > 1) { currentPage -= 1; renderBatches(); } });
    $("#nextSettlementPage").on("click", function () { if (currentPage < Math.ceil(batches.length / PAGE_SIZE)) { currentPage += 1; renderBatches(); } });
    $("#previousSettlementItemPage").on("click", function () { if (currentItemPage > 1) { currentItemPage -= 1; renderItems(items); } });
    $("#nextSettlementItemPage").on("click", function () { if (currentItemPage < Math.ceil(items.length / ITEM_PAGE_SIZE)) { currentItemPage += 1; renderItems(items); } });
    $("#settlementItemFilter").on("change", function () {
        itemFilter = $(this).val();
        updateItemFilter();
        loadBatchItems();
    });
    $("#executeSettlement").on("click", function () { MARIA.auth.ajax({ url: "/api/settlement/jobs", method: "POST" }).done(function (res) { selectedBatchId = res.data.batchId; currentPage = 1; loadBatches(); }).fail(function (xhr) { if (xhr.status !== 401) { showError((xhr.responseJSON && xhr.responseJSON.message) || "정산 배치 실행에 실패했습니다."); } }); });
    $("#retrySettlementBatch").on("click", function () { if (!selectedBatchId) return; MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId + "/retry", method: "POST" }).done(function () { loadBatches(); }).fail(function (xhr) { if (xhr.status !== 401) { showError((xhr.responseJSON && xhr.responseJSON.message) || "정산 배치 재처리에 실패했습니다."); } }); });
    $("#retrySettlementItem").on("click", function () { if (!selectedBatchId || !selectedItemId) return; MARIA.auth.ajax({ url: "/api/settlement/batches/" + selectedBatchId + "/items/" + selectedItemId + "/retry", method: "POST" }).done(function () { selectBatch(selectedBatchId, true); }).fail(function (xhr) { if (xhr.status !== 401) { showError((xhr.responseJSON && xhr.responseJSON.message) || "정산 항목 재처리에 실패했습니다."); } }); });
    loadBatches();
});
