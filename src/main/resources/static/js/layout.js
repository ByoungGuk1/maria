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
    loadReferenceTime();

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

    function loadAccountReviewCount() {
        MARIA.auth.ajax({
            url: "/api/account/requiring-action-count",
            method: "GET"
        }).done(function (res) {
            var count = res.data || 0;
            $("#accountReviewBadge").text(count).toggle(count > 0);
        });
    }

    function loadReferenceTime() {
        MARIA.auth.ajax({
            url: "/api/admin/dashboard",
            method: "GET"
        }).done(function (res) {
            if (res.data && res.data.referenceDateTime) {
                $("#clockValue").text(formatDateTime(res.data.referenceDateTime));
            }
        });
    }

    function formatDateTime(value) {
        return new Intl.DateTimeFormat("ko-KR", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(new Date(value));
    }
});
