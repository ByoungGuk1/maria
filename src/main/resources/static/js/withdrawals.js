$(function () {
    function calculatePageSize() {
        return window.innerHeight >= 850 ? 10 : 9;
    }

    var accountPageSize = calculatePageSize();
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
    var allAccounts = [];
    var accounts = [];
    var selectedAccountId = null;
    var selectedWithdrawalId = null;
    var currentAccountPage = 1;
    var accountWithdrawals = [];
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

    function withdrawalTypeLabel(withdrawal) {
        if (withdrawal.status !== "COMPLETED") {
            return "-";
        }
        return Number(withdrawal.immaturePrincipalAmount || 0) > 0 ? "조기 인출" : "정상 인출";
    }

    function renderAccountPagination(totalPages) {
        var $pagination = $("#withdrawal-account-pagination").empty();
        if (totalPages <= 1) {
            $pagination.prop("hidden", true);
            return;
        }

        var blockSize = 10;
        var blockStart = Math.floor((currentAccountPage - 1) / blockSize) * blockSize + 1;
        var blockEnd = Math.min(totalPages, blockStart + blockSize - 1);

        function addButton(label, targetPage, isDisabled, isActive) {
            var $button = $("<button>", {
                type: "button",
                class: "page-btn" + (isActive ? " active" : ""),
                text: label
            });
            $button.prop("disabled", isDisabled || isActive);
            if (!isDisabled && !isActive) {
                $button.on("click", function () {
                    currentAccountPage = targetPage;
                    selectFirstAccountOnCurrentPage();
                });
            }
            $pagination.append($button);
        }

        addButton("이전", blockStart - 1, blockStart === 1, false);
        for (var page = blockStart; page <= blockEnd; page += 1) {
            addButton(String(page), page, false, page === currentAccountPage);
        }
        addButton("다음", blockEnd + 1, blockEnd === totalPages, false);
        $pagination.prop("hidden", false);
    }

    function renderAccounts() {
        var $list = $("#withdrawal-account-list").empty();
        $("#withdrawal-account-count").text(accounts.length + "건");

        if (!accounts.length) {
            $list.append('<div class="withdrawal-empty">조회된 계좌가 없습니다.</div>');
            $("#withdrawal-account-pagination").prop("hidden", true);
            clearHistory();
            return;
        }

        var totalPages = Math.max(1, Math.ceil(accounts.length / accountPageSize));
        currentAccountPage = Math.min(currentAccountPage, totalPages);
        var startIndex = (currentAccountPage - 1) * accountPageSize;

        accounts.slice(startIndex, startIndex + accountPageSize).forEach(function (account) {
            var selectedClass = Number(account.accountId) === Number(selectedAccountId)
                ? " is-selected"
                : "";
            $list.append(
                '<button type="button" class="withdrawal-account-item' + selectedClass + '"' +
                ' data-account-id="' + account.accountId + '">' +
                '<strong>' + escapeHtml(account.accountNo || "-") + '</strong>' +
                '<span>' + escapeHtml(account.customerName || "-") + '</span></button>'
            );
        });

        renderAccountPagination(totalPages);
    }

    function clearHistory() {
        selectedAccountId = null;
        accountWithdrawals = [];
        $("#withdrawal-history-title").text("계좌를 선택해 주세요");
        $("#withdrawal-history-count").text("0건");
        $("#withdrawal-history-list").html(
            '<div class="withdrawal-detail-empty">왼쪽 목록에서 계좌를 선택해 주세요.</div>'
        );
        closeDrawer();
    }

    function renderHistory() {
        var $list = $("#withdrawal-history-list").empty();
        $("#withdrawal-history-count").text(accountWithdrawals.length + "건");

        if (!accountWithdrawals.length) {
            $list.append('<div class="withdrawal-empty">이 계좌에는 인출 이력이 없습니다.</div>');
            return;
        }

        accountWithdrawals.forEach(function (withdrawal) {
            $list.append(
                '<button type="button" class="withdrawal-history-item" data-withdrawal-id="' +
                withdrawal.withdrawalId + '">' +
                '<span><span class="withdrawal-status-badge ' + statusClass(withdrawal.status) + '">' +
                escapeHtml(statusLabel(withdrawal.status)) + '</span></span>' +
                '<span class="withdrawal-type-label">' + escapeHtml(withdrawalTypeLabel(withdrawal)) + '</span>' +
                '<strong>' + escapeHtml(formatAmount(withdrawal.requestedAmount)) + '</strong>' +
                '<span>' + escapeHtml(formatDateTime(withdrawal.processedAt)) + '</span></button>'
            );
        });
    }

    function applyAccountFilter() {
        var keyword = ($("#withdrawal-keyword").val() || "").trim().toLowerCase();
        accounts = allAccounts.filter(function (account) {
            var customerName = String(account.customerName || "").toLowerCase();
            var accountNo = String(account.accountNo || "").toLowerCase();
            return !keyword || customerName.indexOf(keyword) >= 0 || accountNo.indexOf(keyword) >= 0;
        });
        currentAccountPage = 1;
        selectFirstAccountOnCurrentPage();
    }

    function selectAccount(accountId) {
        selectedAccountId = Number(accountId);
        selectedWithdrawalId = null;
        closeDrawer();
        renderAccounts();

        var account = allAccounts.find(function (item) {
            return Number(item.accountId) === selectedAccountId;
        });
        $("#withdrawal-history-title").text(
            account ? (account.accountNo || "-") + " · " + (account.customerName || "-") : "인출 이력"
        );
        $("#withdrawal-history-list").html('<div class="withdrawal-loading">인출 이력을 불러오는 중...</div>');

        var requestedAccountId = selectedAccountId;
        MARIA.auth.ajax({
            url: "/api/withdrawals/accounts/" + requestedAccountId,
            method: "GET"
        })
            .done(function (response) {
                if (selectedAccountId !== requestedAccountId) {
                    return;
                }
                accountWithdrawals = response.data || [];
                renderHistory();
            })
            .fail(function (xhr) {
                if (selectedAccountId !== requestedAccountId) {
                    return;
                }
                accountWithdrawals = [];
                renderHistory();
                if (xhr.status !== 401) {
                    MARIA.ui.showError(
                        (xhr.responseJSON && xhr.responseJSON.message) || "계좌 인출 이력을 불러오지 못했습니다."
                    );
                }
            });
    }

    function selectFirstAccountOnCurrentPage() {
        var account = accounts[(currentAccountPage - 1) * accountPageSize];
        if (!account) {
            renderAccounts();
            clearHistory();
            return;
        }
        selectAccount(account.accountId);
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
        var allocationType = TYPE_LABEL[allocation.type] || allocation.type;
        var allocationSecondary = isEarnings ? allocationTitle : allocationType;
        var allocationPrimary = isEarnings ? allocationType : allocationTitle;
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
            '<div class="allocation-item-header"><div><span>' + escapeHtml(allocationSecondary) + '</span>' +
            '<strong>' + escapeHtml(allocationPrimary) + '</strong></div>' +
            '<strong>' + escapeHtml(formatAmount(allocation.allocatedAmount)) + '</strong></div>' +
            '<div class="allocation-meta"><span>환전건 ' +
            escapeHtml(allocation.exchangeId == null ? "해당 없음" : "#" + allocation.exchangeId) +
            '</span><span>인출 ' + escapeHtml(formatDateTime(allocation.withdrawalAt)) + '</span></div>' +
            progressMarkup + '</article>'
        );
    }

    function openDrawer(withdrawal) {
        selectedWithdrawalId = Number(withdrawal.withdrawalId);
        $("#withdrawal-detail").removeClass("is-empty").addClass("is-open").attr("aria-hidden", "false");
        $("#withdrawal-drawer-backdrop").prop("hidden", false);
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
    }

    function closeDrawer() {
        selectedWithdrawalId = null;
        $("#withdrawal-detail").removeClass("is-open").attr("aria-hidden", "true");
        $("#withdrawal-drawer-backdrop").prop("hidden", true);
    }

    function loadDetail(withdrawalId) {
        MARIA.auth.ajax({
            url: "/api/withdrawals/" + withdrawalId,
            method: "GET"
        })
            .done(function (response) {
                openDrawer(response.data);
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    MARIA.ui.showError(
                        (xhr.responseJSON && xhr.responseJSON.message) || "인출 상세 내역을 불러오지 못했습니다."
                    );
                }
            });
    }

    function loadAccounts() {
        $("#withdrawal-account-list").html('<div class="withdrawal-loading">계좌 목록을 불러오는 중...</div>');
        $("#withdrawal-search-button").prop("disabled", true);
        MARIA.auth.ajax({ url: "/api/account/list", method: "GET" })
            .done(function (response) {
                allAccounts = response.data || [];
                applyAccountFilter();
            })
            .fail(function (xhr) {
                allAccounts = [];
                accounts = [];
                renderAccounts();
                if (xhr.status !== 401) {
                    MARIA.ui.showError(
                        (xhr.responseJSON && xhr.responseJSON.message) || "계좌 목록을 불러오지 못했습니다."
                    );
                }
            })
            .always(function () {
                $("#withdrawal-search-button").prop("disabled", false);
            });
    }

    $("#withdrawal-search-button").on("click", applyAccountFilter);
    $("#withdrawal-keyword").on("keydown", function (event) {
        if (event.key === "Enter") {
            applyAccountFilter();
        }
    });
    $(document).on("click", ".withdrawal-account-item", function () {
        selectAccount(Number($(this).data("account-id")));
    });
    $(document).on("click", ".withdrawal-history-item", function () {
        loadDetail(Number($(this).data("withdrawal-id")));
    });
    $("#withdrawal-drawer-close, #withdrawal-drawer-backdrop").on("click", closeDrawer);
    $(document).on("keydown", function (event) {
        if (event.key === "Escape") {
            closeDrawer();
        }
    });
    $(window).on("resize", function () {
        var nextPageSize = calculatePageSize();
        if (nextPageSize !== accountPageSize) {
            accountPageSize = nextPageSize;
            currentAccountPage = 1;
            selectFirstAccountOnCurrentPage();
        }
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

    loadAccounts();
});
