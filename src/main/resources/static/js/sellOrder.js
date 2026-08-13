$(function () {
    var ACCOUNT_STATUS_LABEL = {
        APPLIED: "심사대기",
        OPENED: "개설",
        CLOSURE_REQUESTED: "해지신청",
        CLOSED: "해지",
        REJECTED: "반려"
    };

    var SELL_ORDER_STATUS_LABEL = {
        RECEIVED: "접수",
        EXECUTED: "체결",
        REJECTED: "거부"
    };

    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var DATETIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });

    var lastSearchResults = [];
    var selectedAccount = null;
    var productMap = {}; // foreignProductId -> holding(ticker/name/currentQty), 이 계좌의 현재 보유종목 기준

    function escapeHtml(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }

    function formatAmount(amount) {
        return "₩" + KRW_FORMATTER.format(amount || 0);
    }

    function formatDateTime(value) {
        return value ? DATETIME_FORMATTER.format(new Date(value)) : "-";
    }

    function accountStatusLabel(status) {
        return ACCOUNT_STATUS_LABEL[status] || status || "-";
    }

    function sellOrderStatusLabel(status) {
        return SELL_ORDER_STATUS_LABEL[status] || status || "-";
    }

    function statusClassOf(status) {
        return (status || "").toLowerCase().replace(/_/g, "-");
    }

    function usageRatioPercent(account) {
        if (!account.limitAmount || Number(account.limitAmount) <= 0) {
            return null;
        }
        return Math.round((Number(account.usedAmount) / Number(account.limitAmount)) * 100);
    }

    function showError($el, message) {
        $el.text(message).show();
    }

    function hideError($el) {
        $el.hide();
    }

    function canPlaceSellOrder() {
        var admin = MARIA.auth.currentAdmin();
        return !!admin && (admin.role === "ADMIN" || admin.role === "SETTLEMENT");
    }

    // ---- 계좌 검색 ----

    function searchAccounts() {
        var accountNo = $("#searchAccountNo").val().trim();
        var customerName = $("#searchCustomerName").val().trim();
        hideError($("#searchError"));

        if (!accountNo && !customerName) {
            showError($("#searchError"), "계좌번호 또는 고객명 중 하나는 입력해 주세요.");
            return;
        }

        var request = {};
        if (accountNo) {
            request.accountNo = accountNo;
        }
        if (customerName) {
            request.customerName = customerName;
        }

        $("#searchResultsBody").html('<tr><td colspan="3" class="sellorder-loading">검색 중...</td></tr>');

        MARIA.auth.ajax({
            url: "/api/account/search",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify(request)
        })
            .done(function (res) {
                renderSearchResults(res.data || []);
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                $("#searchResultsBody").empty();
                showError(
                    $("#searchError"),
                    (xhr.responseJSON && xhr.responseJSON.message) || "계좌 검색에 실패했습니다."
                );
            });
    }

    function renderSearchResults(accounts) {
        lastSearchResults = accounts;
        var $body = $("#searchResultsBody").empty();

        if (!accounts.length) {
            $body.append('<tr><td colspan="3" class="sellorder-empty">검색 결과가 없습니다.</td></tr>');
            return;
        }

        accounts.forEach(function (account) {
            var ratio = usageRatioPercent(account);
            var ratioText = ratio === null ? "-" : ratio + "%";
            var row =
                '<tr class="sellorder-row" data-account-id="' + account.accountId + '">' +
                '<td><div class="sellorder-account-no">' + escapeHtml(account.accountNo || "-") + "</div>" +
                '<div class="sellorder-account-name">' + escapeHtml(account.customerName || "") + "</div></td>" +
                "<td><span class=\"sellorder-status-badge " + statusClassOf(account.status) + '">' +
                escapeHtml(accountStatusLabel(account.status)) + "</span></td>" +
                '<td class="sellorder-amount">' + ratioText + "</td>" +
                "</tr>";
            $body.append(row);
        });
    }

    function findAccountById(accountId) {
        return lastSearchResults.find(function (account) {
            return account.accountId === accountId;
        });
    }

    // ---- 계좌 선택 ----

    function selectAccount(account) {
        selectedAccount = account;
        renderSelectedAccount(account);
        $("#selectedAccountCard, #sellOrderCard, #sellHistoryCard").show();
        hideError($("#sellOrderError"));
        $("#sellOrderResult").hide();

        // 매도 이력의 종목명을 보유종목 기준으로 붙여주므로, 보유종목을 먼저 받고 나서 이력을 그린다.
        loadHoldings(account.accountId).always(function () {
            loadSellHistory(account.accountId);
        });
    }

    function renderSelectedAccount(account) {
        $("#selectedAccountNo").text(account.accountNo || "-");
        $("#selectedCustomerName").text(account.customerName || "-");
        $("#selectedAccountStatus")
            .attr("class", "sellorder-status-badge " + statusClassOf(account.status))
            .text(accountStatusLabel(account.status));
        $("#selectedLimitAmount").text(formatAmount(account.limitAmount));
        $("#selectedUsedAmount").text(formatAmount(account.usedAmount));
    }

    // ---- 보유종목 ----

    function loadHoldings(accountId) {
        var $select = $("#sellForeignProductId").empty();
        $("#holdingsEmpty").hide();
        $("#sellOrderReadonlyNotice").hide();
        $("#sellForeignProductId, #sellQty, #sellOrderForm button[type=submit]").prop("disabled", true);
        productMap = {};

        return MARIA.auth.ajax({
            url: "/api/inbounds/holdings",
            method: "GET",
            data: { accountId: accountId }
        })
            .done(function (res) {
                var holdings = res.data || [];
                if (!holdings.length) {
                    $("#holdingsEmpty").show();
                    return;
                }
                holdings.forEach(function (holding) {
                    productMap[holding.foreignProductId] = holding;
                    var label = holding.ticker + " · " + holding.name + " (보유 " + holding.currentQty + ")";
                    $select.append($("<option>", { value: holding.foreignProductId, text: label }));
                });

                if (canPlaceSellOrder()) {
                    $("#sellForeignProductId, #sellQty, #sellOrderForm button[type=submit]").prop("disabled", false);
                } else {
                    $("#sellOrderReadonlyNotice").show();
                }
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                showError(
                    $("#sellOrderError"),
                    (xhr.responseJSON && xhr.responseJSON.message) || "보유종목을 불러오지 못했습니다."
                );
            });
    }

    // ---- 매도 주문 ----

    function submitSellOrder() {
        if (!selectedAccount || !canPlaceSellOrder()) {
            return;
        }

        var foreignProductId = Number($("#sellForeignProductId").val());
        var sellQty = $("#sellQty").val();
        hideError($("#sellOrderError"));
        $("#sellOrderResult").hide();

        if (!foreignProductId || !sellQty || Number(sellQty) <= 0) {
            showError($("#sellOrderError"), "종목과 매도 수량을 확인해 주세요.");
            return;
        }

        MARIA.auth.ajax({
            url: "/api/sell-orders",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify({
                accountId: selectedAccount.accountId,
                foreignProductId: foreignProductId,
                sellQty: Number(sellQty)
            })
        })
            .done(function (res) {
                renderSellOrderResult(res.data || []);
                $("#sellQty").val("");
                loadHoldings(selectedAccount.accountId).always(function () {
                    loadSellHistory(selectedAccount.accountId);
                });
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                if (xhr.status === 502) {
                    showError($("#sellOrderError"), "시세/환율 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
                    return;
                }
                showError(
                    $("#sellOrderError"),
                    (xhr.responseJSON && xhr.responseJSON.message) || "매도 주문 접수에 실패했습니다."
                );
            });
    }

    function renderSellOrderResult(orders) {
        // FIFO로 여러 lot에 걸쳐 분할 체결되면 orders가 여러 건으로 올 수 있다.
        if (!orders.length) {
            return;
        }
        var rejected = orders[0].status === "REJECTED";
        var message = rejected
            ? "매도 한도 초과로 거부되었습니다."
            : orders.length + "건 lot으로 분할되어 체결되었습니다.";
        $("#sellOrderResult")
            .attr("class", "sellorder-form-result" + (rejected ? " is-rejected" : ""))
            .text(message)
            .show();
    }

    // ---- 매도 이력 ----

    function loadSellHistory(accountId) {
        $("#sellHistoryBody").html('<tr><td colspan="5" class="sellorder-loading">불러오는 중...</td></tr>');

        MARIA.auth.ajax({
            url: "/api/sell-orders",
            method: "GET",
            data: { accountId: accountId }
        })
            .done(function (res) {
                renderSellHistory(res.data || []);
            })
            .fail(function (xhr) {
                if (xhr.status === 401) {
                    return;
                }
                $("#sellHistoryBody").empty();
                showError(
                    $("#sellOrderError"),
                    (xhr.responseJSON && xhr.responseJSON.message) || "매도 이력을 불러오지 못했습니다."
                );
            });
    }

    function renderSellHistory(orders) {
        var $body = $("#sellHistoryBody").empty();

        if (!orders.length) {
            $body.append('<tr><td colspan="5" class="sellorder-empty">매도 이력이 없습니다.</td></tr>');
            return;
        }

        var sorted = orders.slice().sort(function (a, b) {
            return new Date(b.processedAt || 0) - new Date(a.processedAt || 0);
        });

        sorted.forEach(function (order) {
            var product = productMap[order.foreignProductId];
            var productLabel = product
                ? product.ticker + " · " + product.name
                : "종목 #" + order.foreignProductId;
            var row =
                "<tr>" +
                "<td>" + escapeHtml(productLabel) + "</td>" +
                '<td class="sellorder-amount">' + escapeHtml(order.sellQty) + "</td>" +
                '<td class="sellorder-amount">' + formatAmount(order.basePrice) + "</td>" +
                "<td><span class=\"sellorder-status-badge " + statusClassOf(order.status) + '">' +
                escapeHtml(sellOrderStatusLabel(order.status)) + "</span></td>" +
                "<td>" + formatDateTime(order.processedAt) + "</td>" +
                "</tr>";
            $body.append(row);
        });
    }

    // ---- 이벤트 바인딩 ----

    $("#accountSearchForm").on("submit", function (event) {
        event.preventDefault();
        searchAccounts();
    });

    $(document).on("click", ".sellorder-row", function () {
        var accountId = Number($(this).data("account-id"));
        var account = findAccountById(accountId);
        if (account) {
            selectAccount(account);
        }
    });

    $("#sellOrderForm").on("submit", function (event) {
        event.preventDefault();
        submitSellOrder();
    });
});
