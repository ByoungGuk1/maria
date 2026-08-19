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

    // Authorization 헤더를 자동으로 붙이는 $.ajax 래퍼. 401이 오면 refresh token으로
    // 한 번 재발급을 시도한 뒤 원래 요청을 재시도한다. refresh마저 실패하면 로그아웃.
    function ajax(options) {
        var token = getAccessToken();
        var authHeader = token ? { Authorization: "Bearer " + token } : {};
        var mergedHeaders = $.extend({}, options.headers || {}, authHeader);

        var deferred = $.Deferred();

        $.ajax(
            $.extend({}, options, {
                headers: mergedHeaders
            })
        )
            .done(function (data, textStatus, jqXHR) {
                deferred.resolve(data, textStatus, jqXHR);
            })
            .fail(function (xhr) {
                if (xhr.status !== 401) {
                    deferred.reject(xhr);
                    return;
                }
                refreshAccessToken()
                    .done(function () {
                        var retryHeaders = $.extend({}, options.headers || {}, {
                            Authorization: "Bearer " + getAccessToken()
                        });
                        $.ajax($.extend({}, options, { headers: retryHeaders }))
                            .done(function (data, textStatus, jqXHR) {
                                deferred.resolve(data, textStatus, jqXHR);
                            })
                            .fail(function (retryXhr) {
                                if (retryXhr.status === 401) {
                                    logout();
                                }
                                deferred.reject(retryXhr);
                            });
                    })
                    .fail(function () {
                        logout();
                        deferred.reject(xhr);
                    });
            });

        return deferred.promise();
    }

    // refresh token으로 access token을 재발급받아 저장한다. 동시에 여러 요청이 401을
    // 맞아도 진행 중인 재발급 호출 하나만 공유한다.
    var refreshInFlight = null;
    function refreshAccessToken() {
        var refreshToken = getRefreshToken();
        if (!refreshToken) {
            return $.Deferred().reject().promise();
        }
        if (refreshInFlight) {
            return refreshInFlight;
        }
        refreshInFlight = $.ajax({
            url: "/api/auth/admin/refresh",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify({ refreshToken: refreshToken })
        })
            .done(function (res) {
                saveTokens(res.data.accessToken, res.data.refreshToken);
            })
            .always(function () {
                refreshInFlight = null;
            });
        return refreshInFlight;
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
