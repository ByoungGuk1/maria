$(function () {
    var STATUS_CONFIG = [
        { value: "APPLIED", label: "심사대기", trendLabel: "신청", color: "#2563eb", summarySelector: "#appliedAccountCount" },
        { value: "OPENED", label: "개설", trendLabel: "승인", color: "#16a34a", summarySelector: "#openedAccountCount" },
        { value: "REJECTED", label: "반려", trendLabel: "반려", color: "#d69e2e", summarySelector: "#rejectedAccountCount" },
        { value: "CLOSURE_REQUESTED", label: "해지신청", trendLabel: "해지신청", color: "#d53f8c", summarySelector: "#closureRequestedAccountCount" },
        { value: "CLOSED", label: "해지", trendLabel: "해지", color: "#dc2626", summarySelector: "#closedAccountCount" }
    ];
    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });
    var TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: true });
    var accounts = [];
    var selectedAccountId = null;
    var currentPage = 1;
    var PAGE_SIZE = 10;
    var availableLimitRequestIds = {};
    var accountApplicationChart = null;

    function escapeHtml(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }

    function formatAmount(amount) {
        return "₩" + KRW_FORMATTER.format(amount || 0);
    }

    function parseLimitAmount(selector) {
        var value = $(selector).val().replace(/,/g, "");
        return /^\d+$/.test(value) ? Number(value) : null;
    }

    function isValidLimitAmount(value) {
        return Number.isInteger(value) && value >= 1 && value <= 50000000;
    }

    function setLimitAmount(selector, amount) {
        $(selector).val(amount == null || amount === "" ? "" : KRW_FORMATTER.format(Number(amount)));
    }

    function formatLimitInput(input) {
        var value = input.value.replace(/,/g, "");
        if (/^\d*$/.test(value)) {
            input.value = value ? KRW_FORMATTER.format(Number(value)) : "";
        }
    }

    function formatDateTime(value) {
        return value ? DATE_TIME_FORMATTER.format(new Date(value)) : "-";
    }

    function formatTableDateTime(value) {
        if (!value) {
            return "-";
        }
        var date = new Date(value);
        var dateText = date.getFullYear() + "." + String(date.getMonth() + 1).padStart(2, "0") + "." + String(date.getDate()).padStart(2, "0");
        return '<span class="account-table-date">' + dateText + "<br>" + TIME_FORMATTER.format(date) + "</span>";
    }

    function statusLabel(status) {
        var config = STATUS_CONFIG.find(function (item) { return item.value === status; });
        return config ? config.label : status || "-";
    }

    function usedAmountOf(account) {
        return Number(account.usedAmount || 0);
    }

    function remainingLimitOf(account) {
        return Number(account.limitAmount || 0) - usedAmountOf(account);
    }

    function showError(message) {
        MARIA.ui.showError(message);
    }

    function errorMessage(xhr, fallback) {
        return (xhr.responseJSON && xhr.responseJSON.message) || fallback;
    }

    function handleRequestFailure(xhr, fallback, onFailure) {
        if (xhr.status === 401) {
            return;
        }
        if (onFailure) {
            onFailure();
        }
        showError(errorMessage(xhr, fallback));
    }

    function totalPagesOf(list) {
        return Math.max(1, Math.ceil(list.length / PAGE_SIZE));
    }

    function updateAccountCache(account) {
        var accountIndex = accounts.findIndex(function (item) { return item.accountId === account.accountId; });
        if (accountIndex !== -1) {
            accounts[accountIndex] = $.extend({}, accounts[accountIndex], account);
        }
        return accounts[accountIndex] || account;
    }

    function getSelectedAccount() {
        return accounts.find(function (account) { return account.accountId === selectedAccountId; });
    }

    function reloadSelectedAccount() {
        loadAccounts(function () { selectAccount(selectedAccountId); });
    }

    function updateAvailableLimit(customerId, displaySelector, messages) {
        var requestId = (availableLimitRequestIds[displaySelector] || 0) + 1;
        availableLimitRequestIds[displaySelector] = requestId;
        if (!customerId || Number(customerId) <= 0) {
            $(displaySelector).text(messages.initial);
            return;
        }

        $(displaySelector).text("조회 중...");
        MARIA.auth.ajax({ url: "/api/account/available-limit", method: "GET", data: { customerId: customerId } })
            .done(function (res) {
                if (requestId === availableLimitRequestIds[displaySelector]) {
                    $(displaySelector).text(formatAmount(res.data));
                }
            })
            .fail(function (xhr) {
                if (requestId === availableLimitRequestIds[displaySelector] && xhr.status !== 401) {
                    $(displaySelector).text(messages.initial);
                }
                handleRequestFailure(xhr, messages.error);
            });
    }

    function renderSummary() {
        $("#totalAccountCount").text(accounts.length);
        STATUS_CONFIG.forEach(function (status) {
            $(status.summarySelector).text(accounts.filter(function (account) { return account.status === status.value; }).length);
        });
    }

    function renderApplicationTrend() {
        var selectedTrendStatus = $("#accountTrendStatusFilter").val();
        var latestCreatedAt = accounts.reduce(function (latest, account) {
            if (!account.createdAt) return latest;
            var createdAt = new Date(account.createdAt);
            return !latest || createdAt > latest ? createdAt : latest;
        }, null);
        var today = latestCreatedAt || new Date();
        today.setHours(0, 0, 0, 0);
        var dates = Array.from({ length: 7 }, function (_, index) {
            var date = new Date(today);
            date.setDate(today.getDate() - 6 + index);
            return date;
        });
        var trendGroups = selectedTrendStatus
            ? STATUS_CONFIG.filter(function (status) { return status.value === selectedTrendStatus; }).map(function (status) {
                return { label: status.trendLabel, statuses: [status.value], color: status.color };
            })
            : [
                { label: "신청·개설", statuses: ["APPLIED", "OPENED"], color: "#2563eb" },
                { label: "반려", statuses: ["REJECTED"], color: "#d69e2e" },
                { label: "해지신청·해지", statuses: ["CLOSURE_REQUESTED", "CLOSED"], color: "#d53f8c" }
            ];
        var datasets = trendGroups.map(function (group) {
            return {
                label: group.label,
                data: dates.map(function (date) {
                    return accounts.filter(function (account) {
                        if (!account.createdAt || group.statuses.indexOf(account.status) === -1) return false;
                        var createdAt = new Date(account.createdAt);
                        createdAt.setHours(0, 0, 0, 0);
                        return createdAt.getTime() === date.getTime();
                    }).length;
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
        $("#accountTrendTotal").text(datasets.reduce(function (total, dataset) { return total + dataset.data.reduce(function (sum, count) { return sum + count; }, 0); }, 0) + "건");
        if (accountApplicationChart) accountApplicationChart.destroy();
        accountApplicationChart = new Chart($("#accountApplicationTrend")[0], {
            type: "line",
            data: { labels: labels, datasets: datasets },
            options: {
                animation: false,
                maintainAspectRatio: false,
                plugins: { legend: { display: false }, tooltip: { displayColors: false, callbacks: { label: function (context) { return context.dataset.label + ": " + context.parsed.y + "건"; } } } },
                scales: { x: { grid: { display: false }, ticks: { color: "#64748b", font: { size: 10 } } }, y: { beginAtZero: true, ticks: { precision: 0, color: "#64748b", font: { size: 10 } }, grid: { color: "#e2e8f0" } } }
            }
        });
    }

    function filteredAccounts() {
        var keyword = ($("#accountSearch").val() || "").trim().toLowerCase();

        var status = $("#accountStatusFilter").val();
        return accounts.filter(function (account) {
            var matchesStatus = !status || account.status === status;
            var searchable = [account.accountNo, account.customerId, account.customerName, statusLabel(account.status)]
                .join(" ").toLowerCase();
            return matchesStatus && (!keyword || searchable.indexOf(keyword) !== -1);
        });
    }

    function renderAccounts() {
        var $body = $("#accountListBody").empty();
        var list = filteredAccounts();
        var totalPages = totalPagesOf(list);
        currentPage = Math.min(currentPage, totalPages);
        var startIndex = (currentPage - 1) * PAGE_SIZE;
        var pageAccounts = list.slice(startIndex, startIndex + PAGE_SIZE);
        $("#accountCount").text(list.length + "건");

        if (!list.length) {
            $body.append('<tr><td colspan="7" class="account-empty">조회된 계좌가 없습니다.</td></tr>');
            $("#accountPagination").hide();
            return;
        }

        pageAccounts.forEach(function (account) {
            var statusClass = (account.status || "").toLowerCase().replace(/_/g, "-");
            var selectedClass = account.accountId === selectedAccountId ? " is-selected" : "";
            $body.append(
                '<tr class="account-row' + selectedClass + '" data-account-id="' + account.accountId + '">' +
                '<td><div class="account-number">' + escapeHtml(account.accountNo || "-") + '</div>' +
                '<div class="account-customer-id">' + escapeHtml(account.customerName || "고객 ID " + account.customerId) + ' · 고객 ID ' + escapeHtml(account.customerId) + '</div></td>' +
                '<td><span class="account-status-badge ' + statusClass + '">' + escapeHtml(statusLabel(account.status)) + '</span></td>' +
                '<td class="account-amount">' + formatAmount(account.limitAmount) + '</td>' +
                '<td class="account-amount">' + formatAmount(account.amount) + '</td>' +
                '<td>' + escapeHtml(account.benefit || "-") + '</td>' +
                '<td>' + formatTableDateTime(account.createdAt) + '</td>' +
                '<td>' + formatTableDateTime(account.openedAt) + '</td>' +
                '</tr>'
            );
        });
        $("#accountPageInfo").text(currentPage + " / " + totalPages);
        $("#previousAccountPage").prop("disabled", currentPage === 1);
        $("#nextAccountPage").prop("disabled", currentPage === totalPages);
        $("#accountPagination").css("display", "flex");
    }

    function renderDetail(account) {
        if (!account) {
            $("#accountDetail").hide();
            return;
        }
        $("#accountDetail").show();
        $("#detailAccountNo").text(account.accountNo || "-");
        $("#detailCustomerId").text(account.customerId || "-");
        $("#detailStatus").text(statusLabel(account.status));
        $("#detailLimitAmount").text(formatAmount(account.limitAmount));
        var usedAmount = usedAmountOf(account);
        $("#detailUsedAmount").text(formatAmount(usedAmount));
        $("#detailRemainingLimit").text(formatAmount(remainingLimitOf(account)));
        $("#detailAmount").text(formatAmount(account.amount));
        $("#detailBenefit").text(account.benefit || "-");
        $("#detailOpenedAt").text(formatDateTime(account.openedAt));
        $("#approveAccount, #rejectAccount").toggle(account.status === "APPLIED");
        $("#openLimitModal").toggle(account.status === "APPLIED" || account.status === "OPENED");
        $("#rejectionActions").toggle(account.status === "APPLIED");
        $("#openReapplyModal").toggle(account.status === "REJECTED");
        $("#overrideActions").toggle(account.status === "REJECTED");
    }

    function openLimitModal() {
        var account = getSelectedAccount();
        if (!account || (account.status !== "APPLIED" && account.status !== "OPENED")) {
            return;
        }
        var usedAmount = usedAmountOf(account);
        $("#modalCurrentLimit").text(formatAmount(account.limitAmount));
        $("#modalUsedAmount").text(formatAmount(usedAmount));
        $("#modalRemainingLimit").text(formatAmount(remainingLimitOf(account)));
        setLimitAmount("#modalNewLimitAmount", account.limitAmount);
        $("#accountLimitModal").css("display", "flex");
        updateAvailableLimit(account.customerId, "#modalAvailableLimit", { initial: "조회 실패", error: "설정 가능 한도를 조회할 수 없습니다." });
    }

    function closeModal(modalSelector) {
        $(modalSelector).hide();
    }

    function openReapplyModal() {
        var account = getSelectedAccount();
        if (!account || account.status !== "REJECTED") {
            return;
        }
        $("#reapplyCurrentLimit").text(formatAmount(account.limitAmount));
        setLimitAmount("#modalReapplyLimitAmount", account.limitAmount);
        $("#accountReapplyModal").css("display", "flex");
        updateAvailableLimit(account.customerId, "#reapplyAvailableLimit", { initial: "조회 실패", error: "설정 가능 한도를 조회할 수 없습니다." });
    }

    function loadStatusLogs(accountId) {
        var $list = $("#accountHistoryList").empty().append('<li class="account-loading">불러오는 중...</li>');
        MARIA.auth.ajax({ url: "/api/account/" + accountId + "/status-logs", method: "GET" })
            .done(function (res) {
                if (selectedAccountId !== accountId) {
                    return;
                }
                $list.empty();
                var logs = res.data || [];
                if (!logs.length) {
                    $list.append('<li class="account-empty">상태 이력이 없습니다.</li>');
                    return;
                }
                logs.forEach(function (log) {
                    $list.append(
                        '<li class="account-history-item">' +
                        '<span class="account-history-time">' + formatDateTime(log.changedAt) + '</span>' +
                        '<span class="account-history-reason">' + escapeHtml(log.reason || "-") + '</span>' +
                        '<span class="account-history-status">' + escapeHtml(statusLabel(log.prevStatus)) + ' → ' + escapeHtml(statusLabel(log.newStatus)) + '</span>' +
                        '</li>'
                    );
                });
            })
            .fail(function (xhr) {
                handleRequestFailure(xhr, "상태 이력을 불러오지 못했습니다.", function () { $list.empty(); });
            });
    }

    function selectAccount(accountId) {
        var requestedAccountId = Number(accountId);
        selectedAccountId = requestedAccountId;
        renderAccounts();
        MARIA.auth.ajax({ url: "/api/account/" + requestedAccountId, method: "GET" })
            .done(function (res) {
                if (selectedAccountId !== requestedAccountId) {
                    return;
                }
                var account = updateAccountCache(res.data);
                renderAccounts();
                renderDetail(account);
                loadStatusLogs(account.accountId);
            })
            .fail(function (xhr) {
                if (selectedAccountId === requestedAccountId) {
                    handleRequestFailure(xhr, "계좌 정보를 불러오지 못했습니다.");
                }
            });
    }

    function loadAccounts(afterLoad) {
        $("#accountListBody").html('<tr><td colspan="7" class="account-loading">불러오는 중...</td></tr>');
        MARIA.auth.ajax({ url: "/api/account/list", method: "GET" })
            .done(function (res) {
                accounts = res.data || [];
                renderSummary();
                renderApplicationTrend();
                renderAccounts();
                if (afterLoad) {
                    afterLoad();
                }
            })
            .fail(function (xhr) {
                handleRequestFailure(xhr, "계좌 목록을 불러오지 못했습니다.", function () { $("#accountListBody").empty(); });
            });
    }

    function submitReview(action) {
        if (!selectedAccountId) {
            return;
        }
        var reason = $("#accountReason").val().trim();
        if (action === "reject" && !reason) {
            showError("사유를 입력해 주세요.");
            return;
        }

        var options = { url: "/api/account/" + selectedAccountId + "/" + action, method: "POST" };
        if (action === "reject") {
            options.contentType = "application/json";
            options.data = JSON.stringify({ reason: reason });
        }
        MARIA.auth.ajax(options)
            .done(function () {
                $("#accountReason").val("");
                reloadSelectedAccount();
            })
            .fail(function (xhr) {
                handleRequestFailure(xhr, "계좌 상태 변경에 실패했습니다.");
            });
    }

    function submitRecovery(action, payload) {
        if (!selectedAccountId) {
            return;
        }
        MARIA.auth.ajax({
            url: "/api/account/" + selectedAccountId + "/" + action,
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify(payload)
        })
            .done(function () {
                $("#overrideReason").val("");
                reloadSelectedAccount();
            })
            .fail(function (xhr) {
                handleRequestFailure(xhr, "요청 처리에 실패했습니다.");
            });
    }

    function submitForm($form, options) {
        if (!$form[0].checkValidity()) {
            showError("입력값을 확인해 주세요.");
            return;
        }
        MARIA.auth.ajax(options)
            .done(function () {
                $form[0].reset();
                loadAccounts();
            })
            .fail(function (xhr) {
                handleRequestFailure(xhr, "요청 처리에 실패했습니다.");
            });
    }

    function loadAvailableLimit(customerIdSelector, displaySelector) {
        updateAvailableLimit($(customerIdSelector).val(), displaySelector, {
            initial: "고객 ID를 입력하세요.",
            error: "사용 가능한 한도를 조회할 수 없습니다."
        });
    }

    $(document).on("click", ".account-row", function () { selectAccount($(this).data("account-id")); });
    $(document).on("input", ".account-currency-input", function () { formatLimitInput(this); });
    $("#accountSearch, #accountStatusFilter").on("input change", function () {
        currentPage = 1;
        renderAccounts();
    });
    $("#accountTrendStatusFilter").on("change", function () {
        renderApplicationTrend();
    });
    $("#previousAccountPage").on("click", function () {
        if (currentPage > 1) {
            currentPage -= 1;
            renderAccounts();
        }
    });
    $("#nextAccountPage").on("click", function () {
        var totalPages = totalPagesOf(filteredAccounts());
        if (currentPage < totalPages) {
            currentPage += 1;
            renderAccounts();
        }
    });
    $("#createCustomerId").on("change blur", function () {
        loadAvailableLimit("#createCustomerId", "#availableLimit");
    });
    $("#approveAccount").on("click", function () { submitReview("approve"); });
    $("#rejectAccount").on("click", function () { submitReview("reject"); });
    $("#openReapplyModal").on("click", openReapplyModal);
    $("#closeReapplyModal, #cancelReapplyModal, #accountReapplyModal .account-modal-backdrop").on("click", function () { closeModal("#accountReapplyModal"); });
    $("#accountReapplyModalForm").on("submit", function (event) {
        event.preventDefault();
        var limitAmount = parseLimitAmount("#modalReapplyLimitAmount");
        if (!this.checkValidity() || !isValidLimitAmount(limitAmount)) {
            showError("재신청 한도는 1원 이상 5천만원 이하의 정수여야 합니다.");
            return;
        }
        closeModal("#accountReapplyModal");
        submitRecovery("reapply", { limitAmount: limitAmount });
    });
    $("#overrideAccount").on("click", function () {
        var reason = $("#overrideReason").val().trim();
        if (!reason) {
            showError("오버라이드 사유를 입력해 주세요.");
            return;
        }
        submitRecovery("override", { reason: reason });
    });
    $("#openLimitModal").on("click", openLimitModal);
    $("#closeLimitModal, #cancelLimitModal, #accountLimitModal .account-modal-backdrop").on("click", function () { closeModal("#accountLimitModal"); });
    $("#accountLimitModalForm").on("submit", function (event) {
        event.preventDefault();
        var account = getSelectedAccount();
        var limitAmount = parseLimitAmount("#modalNewLimitAmount");
        if (!account || !this.checkValidity() || !isValidLimitAmount(limitAmount)) {
            showError("입력값을 확인해 주세요.");
            return;
        }
        MARIA.auth.ajax({
            url: "/api/account/update/limit",
            method: "PUT",
            contentType: "application/json",
            data: JSON.stringify({ customerId: account.customerId, expectedCurrentLimit: Number(account.limitAmount), limitAmount: limitAmount })
        }).done(function () {
            closeModal("#accountLimitModal");
            reloadSelectedAccount();
        }).fail(function (xhr) {
            handleRequestFailure(xhr, "계좌 한도가 변경되었습니다. 다시 조회 후 시도해주세요.");
        });
    });
    $("#accountCreateForm").on("submit", function (event) {
        event.preventDefault();
        var $form = $(this);
        var limitAmount = parseLimitAmount("#createLimitAmount");
        if (!isValidLimitAmount(limitAmount)) {
            showError("계좌의 한도는 1원 이상 5천만원 이하의 정수여야 합니다.");
            return;
        }
        submitForm($form, {
            url: "/api/account/applications",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify({
                customerId: Number($("#createCustomerId").val()),
                limitAmount: limitAmount
            })
        });
    });

    loadAccounts();
});
