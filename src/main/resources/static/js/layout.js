/**
 * 공용 레이아웃(사이드바+헤더) 동작.
 * layout/main.html을 쓰는 모든 페이지에서 공통으로 로드된다.
 */
MARIA.ui = MARIA.ui || {};

MARIA.ui.showError = function (message) {
    var $container = $("#toastContainer");
    var maxToastCount = 3;
    while ($container.children(".toast").length >= maxToastCount) {
        $container.children(".toast").first().remove();
    }
    var $toast = $("<div>", { "class": "toast", text: message || "요청 처리 중 오류가 발생했습니다." });
    $container.append($toast);
    requestAnimationFrame(function () { $toast.addClass("is-visible"); });
    setTimeout(function () {
        $toast.removeClass("is-visible");
        setTimeout(function () { $toast.remove(); }, 200);
    }, 3500);
};

$(function () {
    if (!MARIA.auth.requireAuth()) {
        return;
    }

    var admin = MARIA.auth.currentAdmin();
    if (admin) {
        $("#adminBadge").text(admin.name + " · " + admin.role);
    }

    applyTheme(localStorage.getItem("maria.theme") || "light");
    applyActiveMenu();

    $("#themeToggle").on("click", function () {
        var next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
        localStorage.setItem("maria.theme", next);
        applyTheme(next);
    });

    $("#logoutLink").on("click", function () {
        MARIA.auth.logout();
    });

    loadAccountReviewCount();

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        $("#themeToggle").text(theme === "dark" ? "🌙" : "☀");
    }

    function applyActiveMenu() {
        var path = window.location.pathname;
        $("#dashboardMenu").toggleClass("active", path === "/admin/dashboard");
        $("#accountManagementMenu").toggleClass("active", path === "/admin/account");
    }

    function loadAccountReviewCount() {
        MARIA.auth.ajax({
            url: "/api/account/list",
            method: "GET"
        }).done(function (res) {
            var count = (res.data || []).filter(function (account) {
                return account.status === "APPLIED" || account.status === "CLOSURE_REQUESTED";
            }).length;
            $("#accountReviewBadge").text(count).toggle(count > 0);
        });
    }
});
