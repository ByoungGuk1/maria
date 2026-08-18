$(function () {
    var PAGE_SIZE = 9;
    var STATUS_LABEL = {
        REQUESTED: "처리 요청",
        COMPLETED: "처리 완료",
        CANCELLED: "취소",
        FAILED: "실패"
    };
    var TYPE_LABEL = {
        EARNINGS_ONLY: "수익금",
        MATURED_PRINCIPAL_INCLUDED: "1년 경과 원금",
        IMMATURE_PRINCIPAL_INCLUDED: "1년 미경과 원금"
    };
    var DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
    var withdrawals = [];
    var completedWithdrawals = [];
    var selectedWithdrawalId = null;
    var currentPage = 1;
    var selectedWithdrawalType = "ALL";
    var detailAllocations = [];
    var currentAllocationIndex = 0;

    function escapeHtml(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }

    function formatAmount(value) {
        return "₩" + new Intl.NumberFormat("ko-KR").format(Number(value || 0));
    }

    function formatDateTime(value) {
        return value ? DATE_TIME_FORMATTER.format(new Date(value)) : "-";
    }

    function statusLabel(status) {
        return STATUS_LABEL[status] || status || "-";
    }

    function statusClass(status) {
        return (status || "").toLowerCase();
    }

    function renderPagination(totalPages) {
        var $pagination = $("#withdrawal-pagination").empty();
        if (totalPages <= 1) {
            $pagination.prop("hidden", true);
            return;
        }

        var blockSize = 10;
        var blockStart = Math.floor((currentPage - 1) / blockSize) * blockSize + 1;
        var blockEnd = Math.min(totalPages, blockStart + blockSize - 1);

        function appendPageButton(label, targetPage, disabled, active) {
            var $button = $("<button>", {
                type: "button",
                class: "page-btn" + (active ? " active" : ""),
                text: label
            }).prop("disabled", disabled || active);

            if (!disabled && !active) {
                $button.on("click", function () {
                    currentPage = targetPage;
                    selectFirstWithdrawalOnCurrentPage();
                });
            }
            $pagination.append($button);
        }

        appendPageButton("이전", blockStart - 1, blockStart === 1, false);
        for (var page = blockStart; page <= blockEnd; page += 1) {
            appendPageButton(String(page), page, false, page === currentPage);
        }
        appendPageButton("다음", blockEnd + 1, blockEnd === totalPages, false);
        $pagination.prop("hidden", false);
    }

    function renderList() {
        var $list = $("#withdrawal-list").empty();

        if (!withdrawals.length) {
            $list.append('<div class="withdrawal-empty">해당 조건의 인출 내역이 없습니다.</div>');
            $("#withdrawal-pagination").prop("hidden", true);
            clearDetail();
            return;
        }

        var totalPages = Math.max(1, Math.ceil(withdrawals.length / PAGE_SIZE));
        currentPage = Math.min(currentPage, totalPages);
        var startIndex = (currentPage - 1) * PAGE_SIZE;

        withdrawals.slice(startIndex, startIndex + PAGE_SIZE).forEach(function (withdrawal) {
            var selectedClass = Number(withdrawal.withdrawalId) === Number(selectedWithdrawalId)
                ? " is-selected"
                : "";
            $list.append(
                '<button type="button" class="withdrawal-list-item' + selectedClass + '"' +
                ' data-withdrawal-id="' + withdrawal.withdrawalId + '">' +
                '<span class="withdrawal-account-cell"><strong>' +
                escapeHtml(withdrawal.riaAccountNo || "-") + '</strong><small>' +
                escapeHtml(withdrawal.customerName || "-") + '</small></span>' +
                '<span class="withdrawal-status-badge ' + statusClass(withdrawal.status) + '">' +
                escapeHtml(statusLabel(withdrawal.status)) + '</span>' +
                '<strong class="withdrawal-request-amount">' +
                escapeHtml(formatAmount(withdrawal.requestedAmount)) + '</strong>' +
                '<span class="withdrawal-list-time">' +
                escapeHtml(formatDateTime(withdrawal.processedAt)) + '</span></button>'
            );
        });

        renderPagination(totalPages);
    }

    function clearDetail() {
        selectedWithdrawalId = null;
        $("#withdrawal-detail").addClass("is-empty");
        $("#withdrawal-detail-empty").show();
        $("#withdrawal-detail-content").prop("hidden", true);
    }

    function applyFilters() {
        var keyword = ($("#withdrawal-keyword").val() || "").trim().toLowerCase();

        var normalCount = completedWithdrawals.filter(function (withdrawal) {
            return Number(withdrawal.immaturePrincipalAmount || 0) <= 0;
        }).length;
        $("#withdrawal-all-count").text(completedWithdrawals.length);
        $("#withdrawal-normal-count").text(normalCount);
        $("#withdrawal-early-count").text(completedWithdrawals.length - normalCount);

        withdrawals = completedWithdrawals.filter(function (withdrawal) {
            var isEarlyWithdrawal = Number(withdrawal.immaturePrincipalAmount || 0) > 0;
            var matchesType = selectedWithdrawalType === "ALL" ||
                (selectedWithdrawalType === "EARLY" && isEarlyWithdrawal) ||
                (selectedWithdrawalType === "NORMAL" && !isEarlyWithdrawal);
            var customerName = String(withdrawal.customerName || "").toLowerCase();
            var accountNo = String(withdrawal.riaAccountNo || "").toLowerCase();
            var matchesKeyword = !keyword ||
                customerName.indexOf(keyword) >= 0 ||
                accountNo.indexOf(keyword) >= 0;

            return matchesType && matchesKeyword;
        });

        currentPage = 1;
        if (withdrawals.length) {
            selectedWithdrawalId = Number(withdrawals[0].withdrawalId);
            renderList();
            loadDetail(selectedWithdrawalId);
        } else {
            renderList();
        }
    }

    function calculateProgress(allocation) {
        if (!allocation.finalAt || !allocation.maturityAt) {
            return null;
        }
        var finalAt = new Date(allocation.finalAt).getTime();
        var maturityAt = new Date(allocation.maturityAt).getTime();
        var withdrawalAt = new Date(allocation.withdrawalAt).getTime();
        var total = maturityAt - finalAt;
        if (total <= 0 || Number.isNaN(withdrawalAt)) {
            return null;
        }
        return Math.round(Math.max(0, Math.min(1, (withdrawalAt - finalAt) / total)) * 100);
    }

    function renderAllocations() {
        var $container = $("#withdrawal-allocations").empty();
        var allocationCount = detailAllocations.length;
        $("#withdrawal-allocation-count").text(
            allocationCount ? (currentAllocationIndex + 1) + " / " + allocationCount : "0 / 0"
        );
        $("#previous-allocation").prop("disabled", currentAllocationIndex === 0);
        $("#next-allocation").prop(
            "disabled",
            !allocationCount || currentAllocationIndex === allocationCount - 1
        );

        if (!allocationCount) {
            $container.append('<div class="withdrawal-empty">저장된 배분 내역이 없습니다.</div>');
            return;
        }

        var allocation = detailAllocations[currentAllocationIndex];
        var progress = calculateProgress(allocation);
        var isEarnings = allocation.type === "EARNINGS_ONLY";
        var allocationTitle = isEarnings
            ? "수익금 배분"
            : (allocation.productName
                ? allocation.productName + (allocation.ticker ? " (" + allocation.ticker + ")" : "")
                : "종목 정보 없음");
        var progressMarkup = isEarnings || progress == null
            ? '<div class="retention-not-applicable">' +
                (isEarnings ? "수익금은 의무유지기간 비대상" : "의무유지기간 정보 없음") +
                '</div>'
            : '<div class="retention-dates"><span>' + escapeHtml(formatDateTime(allocation.finalAt)) +
                '</span><span>1년 경과일 ' + escapeHtml(formatDateTime(allocation.maturityAt)) + '</span></div>' +
                '<div class="retention-progress"><span style="width:' + progress + '%"></span></div>' +
                '<div class="retention-progress-label">인출 시점 기준 ' + progress + '% 경과</div>';

        $container.append(
            '<article class="withdrawal-allocation-item ' + statusClass(allocation.type) + '">' +
            '<div class="allocation-item-header"><div><span>' + escapeHtml(allocationTitle) + '</span>' +
            '<strong>' + escapeHtml(TYPE_LABEL[allocation.type] || allocation.type) + '</strong></div>' +
            '<strong>' + escapeHtml(formatAmount(allocation.allocatedAmount)) + '</strong></div>' +
            '<div class="allocation-meta"><span>환전건 ' +
            escapeHtml(allocation.exchangeId == null ? "해당 없음" : "#" + allocation.exchangeId) +
            '</span><span>인출 ' + escapeHtml(formatDateTime(allocation.withdrawalAt)) + '</span></div>' +
            progressMarkup + '</article>'
        );
    }

    function renderDetail(withdrawal) {
        selectedWithdrawalId = Number(withdrawal.withdrawalId);
        $("#withdrawal-detail").removeClass("is-empty");
        $("#withdrawal-detail-empty").hide();
        $("#withdrawal-detail-content").prop("hidden", false);
        $("#withdrawal-detail-title").text((withdrawal.customerName || "-") + " 고객 인출");
        $("#withdrawal-customer-name").text(withdrawal.customerName || "-");
        $("#withdrawal-account-no").text(withdrawal.riaAccountNo || "-");
        $("#withdrawal-requested-amount").text(formatAmount(withdrawal.requestedAmount));
        $("#withdrawal-destination-account").text(withdrawal.destinationAccountNo || "-");
        $("#withdrawal-processed-at").text(formatDateTime(withdrawal.processedAt));
        $("#withdrawal-early-result").text(withdrawal.earlyWithdrawal ? "발생" : "없음");
        $("#withdrawal-earnings-amount").text(formatAmount(withdrawal.earningsAmount));
        $("#withdrawal-matured-amount").text(formatAmount(withdrawal.maturedPrincipalAmount));
        $("#withdrawal-immature-amount").text(formatAmount(withdrawal.immaturePrincipalAmount));
        $("#withdrawal-detail-status")
            .attr("class", "withdrawal-status-badge " + statusClass(withdrawal.status))
            .text(statusLabel(withdrawal.status));
        $("#withdrawal-early-warning").prop("hidden", !withdrawal.earlyWithdrawal);
        detailAllocations = withdrawal.allocations || [];
        currentAllocationIndex = 0;
        renderAllocations();
        renderList();
    }

    function loadDetail(withdrawalId) {
        MARIA.auth.ajax({
            url: "/api/withdrawals/" + withdrawalId,
            method: "GET"
        })
            .done(function (response) {
                renderDetail(response.data);
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    MARIA.ui.showError(
                        (xhr.responseJSON && xhr.responseJSON.message) ||
                        "인출 상세 내역을 불러오지 못했습니다."
                    );
                }
            });
    }

    function loadWithdrawals() {
        $("#withdrawal-list").html('<div class="withdrawal-loading">불러오는 중...</div>');
        $("#withdrawal-search-button").prop("disabled", true);

        MARIA.auth.ajax({
            url: "/api/withdrawals",
            method: "GET",
            data: { status: "COMPLETED" }
        })
            .done(function (response) {
                completedWithdrawals = response.data || [];
                applyFilters();
            })
            .fail(function (xhr) {
                completedWithdrawals = [];
                withdrawals = [];
                renderList();
                if (xhr.status !== 401) {
                    MARIA.ui.showError(
                        (xhr.responseJSON && xhr.responseJSON.message) ||
                        "인출 내역을 불러오지 못했습니다."
                    );
                }
            })
            .always(function () {
                $("#withdrawal-search-button").prop("disabled", false);
            });
    }

    function selectFirstWithdrawalOnCurrentPage() {
        var withdrawal = withdrawals[(currentPage - 1) * PAGE_SIZE];
        if (!withdrawal) {
            renderList();
            clearDetail();
            return;
        }
        selectedWithdrawalId = Number(withdrawal.withdrawalId);
        renderList();
        loadDetail(selectedWithdrawalId);
    }

    $("#withdrawal-search-button").on("click", applyFilters);
    $("#withdrawal-keyword").on("keydown", function (event) {
        if (event.key === "Enter") {
            applyFilters();
        }
    });
    $(".withdrawal-type-tab").on("click", function () {
        selectedWithdrawalType = $(this).data("withdrawal-type");
        $(".withdrawal-type-tab").removeClass("active");
        $(this).addClass("active");
        applyFilters();
    });
    $(document).on("click", ".withdrawal-list-item", function () {
        loadDetail(Number($(this).data("withdrawal-id")));
    });
    $("#previous-allocation").on("click", function () {
        if (currentAllocationIndex > 0) {
            currentAllocationIndex -= 1;
            renderAllocations();
        }
    });
    $("#next-allocation").on("click", function () {
        if (currentAllocationIndex < detailAllocations.length - 1) {
            currentAllocationIndex += 1;
            renderAllocations();
        }
    });

    loadWithdrawals();
});
