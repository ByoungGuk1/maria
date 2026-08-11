/**
 * 공용 레이아웃(사이드바+헤더) 동작.
 * layout/main.html을 쓰는 모든 페이지에서 공통으로 로드된다.
 */
$(function () {
    if (!MARIA.auth.requireAuth()) {
        return;
    }

    var admin = MARIA.auth.currentAdmin();
    if (admin) {
        $("#adminBadge").text(admin.loginId + " · " + admin.role);
    }

    applyTheme(localStorage.getItem("maria.theme") || "light");

    $("#themeToggle").on("click", function () {
        var next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
        localStorage.setItem("maria.theme", next);
        applyTheme(next);
    });

    $("#logoutLink").on("click", function () {
        MARIA.auth.logout();
    });

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        $("#themeToggle").text(theme === "dark" ? "🌙" : "☀");
    }
});
