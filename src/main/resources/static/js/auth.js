/**
 * 인증 공용 유틸 (jQuery 기반).
 * 이 프로젝트는 세션이 아니라 JWT(Authorization 헤더) 인증이라,
 * 페이지 라우트 자체는 공개(SecurityConfig PUBLIC_URLS)로 열어두고
 * 여기서 클라이언트 쪽에서 토큰 유무를 검사한 뒤 API 호출 시 헤더로 붙여준다.
 */
var MARIA = window.MARIA || {};

MARIA.auth = (function ($) {
    var ACCESS_TOKEN_KEY = "maria.accessToken";
    var REFRESH_TOKEN_KEY = "maria.refreshToken";

    function getAccessToken() {
        return localStorage.getItem(ACCESS_TOKEN_KEY);
    }

    function getRefreshToken() {
        return localStorage.getItem(REFRESH_TOKEN_KEY);
    }

    function saveTokens(accessToken, refreshToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }

    function clearTokens() {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
    }

    // accessToken이 없으면 로그인 페이지로 보낸다. 만료 여부까지는 검사하지 않고,
    // 만료된 토큰으로 API를 호출해 401이 나면 ajax()가 처리한다.
    function requireAuth() {
        if (!getAccessToken()) {
            window.location.href = "/login";
            return false;
        }
        return true;
    }

    // JWT payload(가운데 구간)를 디코딩해 adminId/role 등 클레임을 읽는다.
    // 서명 검증은 서버(API 호출 시점)가 하므로, 여기서는 화면 표시용으로만 사용한다.
    function decodeToken(token) {
        try {
            var payload = token.split(".")[1];
            var json = decodeURIComponent(
                atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
                    .split("")
                    .map(function (c) {
                        return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
                    })
                    .join("")
            );
            return JSON.parse(json);
        } catch (e) {
            return null;
        }
    }

    function currentAdmin() {
        var token = getAccessToken();
        return token ? decodeToken(token) : null;
    }

    function logout() {
        clearTokens();
        window.location.href = "/login";
    }

    // Authorization 헤더를 자동으로 붙이는 $.ajax 래퍼. 401이 오면 토큰을 지우고
    // 로그인 페이지로 보낸다(리프레시 재시도는 하지 않음 - 필요해지면 여기에 추가).
    function ajax(options) {
        var token = getAccessToken();
        var mergedHeaders = $.extend({}, options.headers || {}, {
            Authorization: token ? "Bearer " + token : undefined
        });

        return $.ajax(
            $.extend({}, options, {
                headers: mergedHeaders
            })
        ).fail(function (xhr) {
            if (xhr.status === 401) {
                clearTokens();
                window.location.href = "/login";
            }
        });
    }

    return {
        getAccessToken: getAccessToken,
        getRefreshToken: getRefreshToken,
        saveTokens: saveTokens,
        clearTokens: clearTokens,
        requireAuth: requireAuth,
        currentAdmin: currentAdmin,
        logout: logout,
        ajax: ajax
    };
})(jQuery);
