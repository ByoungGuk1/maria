$(function () {
    var STATUS_LABEL = {
        APPLIED: "심사대기",
        OPENED: "개설",
        CLOSURE_REQUESTED: "해지신청",
        CLOSED: "해지",
        REJECTED: "반려"
    };
    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });
    var accounts = [];
    var selectedAccountId = null;
    var currentPage = 1;
    var PAGE_SIZE = 10;
    var availableLimitRequestIds = {};

    function escapeHtml(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }

    function formatAmount(amount) {
        return "₩" + KRW_FORMATTER.format(amount || 0);
    }

    function formatDateTime(value) {
        return value ? DATE_TIME_FORMATTER.format(new Date(value)) : "-";
    }

    function statusLabel(status) {
        return STATUS_LABEL[status] || status || "-";
    }

    function showError(message) {
        MARIA.ui.showError(message);
    }

    function filteredAccounts() {
        var status = $("#accountStatusFilter").val();
        var keyword = ($("#accountSearch").val() || "").trim().toLowerCase();

        return accounts.filter(function (account) {
            var matchesStatus = !status || account.status === status;
            var searchable = [account.accountNo, account.customerId, statusLabel(account.status)]
                .join(" ").toLowerCase();
            return matchesStatus && (!keyword || searchable.indexOf(keyword) !== -1);
        });
    }

    function renderAccounts() {
        var $body = $("#accountListBody").empty();
        var list = filteredAccounts();
        var totalPages = Math.max(1, Math.ceil(list.length / PAGE_SIZE));
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
                '<div class="account-customer-id">고객 ID ' + escapeHtml(account.customerId) + '</div></td>' +
                '<td><span class="account-status-badge ' + statusClass + '">' + escapeHtml(statusLabel(account.status)) + '</span></td>' +
                '<td class="account-amount">' + formatAmount(account.limitAmount) + '</td>' +
                '<td class="account-amount">' + formatAmount(account.amount) + '</td>' +
                '<td>' + escapeHtml(account.benefit || "-") + '</td>' +
                '<td>' + formatDateTime(account.createdAt) + '</td>' +
                '<td>' + formatDateTime(account.openedAt) + '</td>' +
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
        $("#accountHistory").show();
        $("#detailAccountNo").text(account.accountNo || "-");
        $("#detailCustomerId").text(account.customerId || "-");
        $("#detailStatus").text(statusLabel(account.status));
        $("#detailLimitAmount").text(formatAmount(account.limitAmount));
        $("#detailAmount").text(formatAmount(account.amount));
        $("#detailOpenedAt").text(formatDateTime(account.openedAt));
        $("#approveAccount, #rejectAccount").toggle(account.status === "APPLIED");
        $("#rejectionActions").toggle(account.status === "APPLIED");
        $("#reapplyActions, #overrideActions").toggle(account.status === "REJECTED");
        $("#limitCustomerId").val(account.customerId || "");
        $("#expectedCurrentLimit").val(account.limitAmount || "");
        loadAvailableLimit("#limitCustomerId", "#limitAvailableLimit");
    }

    function loadStatusLogs(accountId) {
        var $list = $("#accountHistoryList").empty().append('<li class="account-loading">불러오는 중...</li>');
        MARIA.auth.ajax({ url: "/api/account/" + accountId + "/status-logs", method: "GET" })
            .done(function (res) {
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
                        '<span>' + escapeHtml(statusLabel(log.prevStatus)) + ' → ' + escapeHtml(statusLabel(log.newStatus)) + '</span>' +
                        '<span class="account-history-reason">' + escapeHtml(log.reason || "-") + '</span>' +
                        '</li>'
                    );
                });
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    $list.empty();
                    showError("상태 이력을 불러오지 못했습니다.");
                }
            });
    }

    function selectAccount(accountId) {
        selectedAccountId = Number(accountId);
        var account = accounts.find(function (item) { return item.accountId === selectedAccountId; });
        renderAccounts();
        renderDetail(account);
        if (account) {
            loadStatusLogs(account.accountId);
        }
    }

    function loadAccounts() {
        $("#accountListBody").html('<tr><td colspan="7" class="account-loading">불러오는 중...</td></tr>');
        MARIA.auth.ajax({ url: "/api/account/list", method: "GET" })
            .done(function (res) {
                accounts = res.data || [];
                renderAccounts();
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    $("#accountListBody").empty();
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "계좌 목록을 불러오지 못했습니다.");
                }
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
                loadAccounts();
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "계좌 상태 변경에 실패했습니다.");
                }
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
                $("#reapplyLimitAmount, #overrideReason").val("");
                loadAccounts();
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "요청 처리에 실패했습니다.");
                }
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
                if (xhr.status !== 401) {
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "요청 처리에 실패했습니다.");
                }
            });
    }

    function loadAvailableLimit(customerIdSelector, displaySelector) {
        var customerId = $(customerIdSelector).val();
        var requestId = (availableLimitRequestIds[displaySelector] || 0) + 1;
        availableLimitRequestIds[displaySelector] = requestId;
        if (!customerId || Number(customerId) <= 0) {
            $(displaySelector).text("고객 ID를 입력하세요.");
            return;
        }

        $(displaySelector).text("조회 중...");
        MARIA.auth.ajax({
            url: "/api/account/available-limit",
            method: "GET",
            data: { customerId: customerId }
        })
            .done(function (res) {
                if (requestId === availableLimitRequestIds[displaySelector]) {
                    $(displaySelector).text(formatAmount(res.data));
                }
            })
            .fail(function (xhr) {
                if (requestId === availableLimitRequestIds[displaySelector] && xhr.status !== 401) {
                    $(displaySelector).text("고객 ID를 입력하세요.");
                    showError((xhr.responseJSON && xhr.responseJSON.message) || "사용 가능한 한도를 조회할 수 없습니다.");
                }
            });
    }

    $(document).on("click", ".account-row", function () { selectAccount($(this).data("account-id")); });
    $("#accountSearch, #accountStatusFilter").on("input change", function () {
        currentPage = 1;
        renderAccounts();
    });
    $("#previousAccountPage").on("click", function () {
        if (currentPage > 1) {
            currentPage -= 1;
            renderAccounts();
        }
    });
    $("#nextAccountPage").on("click", function () {
        var totalPages = Math.ceil(filteredAccounts().length / PAGE_SIZE);
        if (currentPage < totalPages) {
            currentPage += 1;
            renderAccounts();
        }
    });
    $("#createCustomerId").on("change blur", function () {
        loadAvailableLimit("#createCustomerId", "#availableLimit");
    });
    $("#limitCustomerId").on("change blur", function () {
        loadAvailableLimit("#limitCustomerId", "#limitAvailableLimit");
    });
    $("#approveAccount").on("click", function () { submitReview("approve"); });
    $("#rejectAccount").on("click", function () { submitReview("reject"); });
    $("#reapplyAccount").on("click", function () {
        var limitAmount = $("#reapplyLimitAmount").val();
        if (limitAmount && (!Number.isInteger(Number(limitAmount)) || Number(limitAmount) < 1 || Number(limitAmount) > 50000000)) {
            showError("재신청 한도는 1원 이상 5천만원 이하의 정수여야 합니다.");
            return;
        }
        submitRecovery("reapply", limitAmount ? { limitAmount: Number(limitAmount) } : {});
    });
    $("#overrideAccount").on("click", function () {
        var reason = $("#overrideReason").val().trim();
        if (!reason) {
            showError("오버라이드 사유를 입력해 주세요.");
            return;
        }
        submitRecovery("override", { reason: reason });
    });
    $("#accountCreateForm").on("submit", function (event) {
        event.preventDefault();
        var $form = $(this);
        submitForm($form, {
            url: "/api/account/applications",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify({
                customerId: Number($("#createCustomerId").val()),
                limitAmount: Number($("#createLimitAmount").val())
            })
        });
    });
    $("#accountLimitForm").on("submit", function (event) {
        event.preventDefault();
        var $form = $(this);
        submitForm($form, {
            url: "/api/account/update/limit",
            method: "PUT",
            contentType: "application/json",
            data: JSON.stringify({
                customerId: Number($("#limitCustomerId").val()),
                expectedCurrentLimit: Number($("#expectedCurrentLimit").val()),
                limitAmount: Number($("#newLimitAmount").val())
            })
        });
    });

    loadAccounts();
});
