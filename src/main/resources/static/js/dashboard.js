$(function () {
    var KRW_FORMATTER = new Intl.NumberFormat("ko-KR");
    var CHART_COLORS = ["#2563eb", "#16a34a", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#94a3b8"];
    var BENEFIT_LABEL = {
        POSSIBLE: "가능",
        REDUCED: "축소",
        IMPOSSIBLE: "불가능",
        UNCLASSIFIED: "미분류"
    };
    var PRODUCT_PIE_TOP_N = 5;

    var charts = {};

    function formatAmount(amount) {
        return "₩" + KRW_FORMATTER.format(amount || 0);
    }

    function showError(message) {
        $("#dashStatError").text(message).show();
    }

    function hideError() {
        $("#dashStatError").hide();
    }

    function buildFilterParams(extra) {
        var params = {};
        var accountNo = $("#statFilterAccountNo").val().trim();
        var customerName = $("#statFilterCustomerName").val().trim();
        var productName = $("#statFilterProductName").val().trim();
        var startDate = $("#statFilterStartDate").val();
        var endDate = $("#statFilterEndDate").val();
        if (accountNo) params.accountNo = accountNo;
        if (customerName) params.customerName = customerName;
        if (productName) params.productName = productName;
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
        return $.extend(params, extra);
    }

    function destroyChart(key) {
        if (charts[key]) {
            charts[key].destroy();
            charts[key] = null;
        }
    }

    function renderPieChart(key, canvasId, emptyId, labels, data, tooltipFormatter) {
        destroyChart(key);
        $("#" + emptyId).toggle(!labels.length);
        if (!labels.length) {
            return;
        }
        charts[key] = new Chart($("#" + canvasId)[0], {
            type: "pie",
            data: {
                labels: labels,
                datasets: [{
                    data: data,
                    backgroundColor: labels.map(function (_, i) { return CHART_COLORS[i % CHART_COLORS.length]; })
                }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: "bottom", labels: { boxWidth: 10, font: { size: 11 } } },
                    tooltip: { callbacks: { label: tooltipFormatter } }
                }
            }
        });
    }

    function renderBarChart(key, canvasId, emptyId, labels, data, label, tooltipFormatter) {
        destroyChart(key);
        $("#" + emptyId).toggle(!labels.length);
        if (!labels.length) {
            return;
        }
        charts[key] = new Chart($("#" + canvasId)[0], {
            type: "bar",
            data: {
                labels: labels,
                datasets: [{
                    label: label,
                    data: data,
                    backgroundColor: CHART_COLORS[0]
                }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { label: tooltipFormatter } }
                },
                scales: {
                    x: { grid: { display: false } },
                    y: { beginAtZero: true, ticks: { callback: function (v) { return formatAmount(v); } } }
                }
            }
        });
    }

    function renderLineChart(key, canvasId, emptyId, labels, series) {
        destroyChart(key);
        $("#" + emptyId).toggle(!labels.length);
        if (!labels.length) {
            return;
        }
        charts[key] = new Chart($("#" + canvasId)[0], {
            type: "line",
            data: {
                labels: labels,
                datasets: series.map(function (s, i) {
                    return {
                        label: s.label,
                        data: s.data,
                        borderColor: CHART_COLORS[i % CHART_COLORS.length],
                        backgroundColor: CHART_COLORS[i % CHART_COLORS.length],
                        tension: 0.3,
                        borderWidth: 2,
                        pointRadius: 3
                    };
                })
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    tooltip: { callbacks: { label: function (ctx) { return ctx.dataset.label + ": " + formatAmount(ctx.parsed.y); } } }
                },
                scales: {
                    x: { grid: { display: false } },
                    y: { beginAtZero: true, ticks: { callback: function (v) { return formatAmount(v); } } }
                }
            }
        });
    }

    function loadAccountBenefit() {
        return MARIA.auth.ajax({
            url: "/api/statistics/account-benefit",
            method: "GET",
            data: buildFilterParams()
        }).done(function (res) {
            var rows = (res.data || []).filter(function (r) { return r.accountCount > 0; });
            renderPieChart(
                "accountBenefit", "accountBenefitChart", "accountBenefitEmpty",
                rows.map(function (r) { return BENEFIT_LABEL[r.benefit] || r.benefit; }),
                rows.map(function (r) { return r.accountCount; }),
                function (ctx) { return ctx.label + ": " + ctx.parsed + "건"; }
            );
        });
    }

    function loadAgeInvestment() {
        return MARIA.auth.ajax({
            url: "/api/statistics/age-investment",
            method: "GET",
            data: buildFilterParams()
        }).done(function (res) {
            var rows = (res.data || []).filter(function (r) { return r.purchaseAmount > 0; });
            renderPieChart(
                "ageInvestment", "ageInvestmentChart", "ageInvestmentEmpty",
                rows.map(function (r) { return r.ageGroup; }),
                rows.map(function (r) { return r.purchaseAmount; }),
                function (ctx) { return ctx.label + ": " + formatAmount(ctx.parsed); }
            );
        });
    }

    function loadProductPurchase() {
        return MARIA.auth.ajax({
            url: "/api/statistics/product-purchase",
            method: "GET",
            data: buildFilterParams()
        }).done(function (res) {
            var rows = (res.data || []).filter(function (r) { return r.purchaseAmount > 0; });
            var top = rows.slice(0, PRODUCT_PIE_TOP_N);
            var rest = rows.slice(PRODUCT_PIE_TOP_N);
            var restSum = rest.reduce(function (sum, r) { return sum + r.purchaseAmount; }, 0);
            var labels = top.map(function (r) { return r.name; });
            var data = top.map(function (r) { return r.purchaseAmount; });
            if (restSum > 0) {
                labels.push("기타");
                data.push(restSum);
            }
            renderPieChart(
                "productPurchase", "productPurchaseChart", "productPurchaseEmpty",
                labels, data,
                function (ctx) { return ctx.label + ": " + formatAmount(ctx.parsed); }
            );
        });
    }

    function loadReliefRate() {
        return MARIA.auth.ajax({
            url: "/api/statistics/relief-rate",
            method: "GET",
            data: buildFilterParams()
        }).done(function (res) {
            var rows = res.data || [];
            renderBarChart(
                "reliefRate", "reliefRateChart", "reliefRateEmpty",
                rows.map(function (r) { return r.periodLabel; }),
                rows.map(function (r) { return r.sellAmount; }),
                "매도금액",
                function (ctx) { return "매도금액: " + formatAmount(ctx.parsed.y); }
            );
        });
    }

    function loadFxExchange() {
        return MARIA.auth.ajax({
            url: "/api/statistics/fx-exchange",
            method: "GET",
            data: buildFilterParams()
        }).done(function (res) {
            var rows = res.data || [];
            var labels = rows.map(function (r) {
                var d = new Date(r.statDate);
                return (d.getMonth() + 1) + "/" + d.getDate();
            });
            renderLineChart("fxExchange", "fxExchangeChart", "fxExchangeEmpty", labels, [
                { label: "가환전액", data: rows.map(function (r) { return r.provisionalAmount || 0; }) },
                { label: "확정환전액", data: rows.map(function (r) { return r.finalAmount || 0; }) }
            ]);
        });
    }

    function loadAll() {
        hideError();
        $.when(
            loadAccountBenefit(),
            loadAgeInvestment(),
            loadProductPurchase(),
            loadReliefRate(),
            loadFxExchange()
        ).fail(function (xhr) {
            if (xhr && xhr.status === 401) {
                return;
            }
            showError((xhr && xhr.responseJSON && xhr.responseJSON.message) || "통계 데이터를 불러오지 못했습니다.");
        });
    }

    $("#statFilterSearch").on("click", loadAll);

    $("#statFilterReset").on("click", function () {
        $("#statFilterAccountNo").val("");
        $("#statFilterCustomerName").val("");
        $("#statFilterProductName").val("");
        $("#statFilterStartDate").val("");
        $("#statFilterEndDate").val("");
        loadAll();
    });

    loadAll();
});
