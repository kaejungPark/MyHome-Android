package com.myhome.android

import java.net.URLEncoder

// 서버가 지정한 CSRF 헤더 이름과 그 헤더에 넣을 토큰을 함께 보관한다.
data class CsrfToken(
    val headerName: String,
    val token: String
)

// 로그인과 CSRF 토큰 발급 등 인증 기능을 담당한다.
object AuthApi {

    // 백엔드에서 현재 세션에 사용할 CSRF 토큰을 받아온다.
    suspend fun fetchCsrfToken(): CsrfToken {
        val response = ApiClient.request(
            method = "GET",
            path = "/api/auth/csrf"
        )

        val data = response.getJSONObject("data")
        return CsrfToken(
            headerName = data.getString("headerName"),
            token = data.getString("token")
        )
    }

    /**
     * 이메일과 비밀번호로 로그인한다.
     * 성공하면 비밀번호 변경 필요 여부를 반환한다. 일반 로그인은 false다.
     */
    suspend fun login(email: String, password: String): Boolean {
        // 백엔드가 폼 데이터를 받으므로 두 값을 각각 인코딩한다.
        // 비밀번호에 포함된 공백이나 + 기호도 원래대로 전달한다.
        val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
        val encodedPassword = URLEncoder.encode(password, "UTF-8")

        val response = ApiClient.request(
            method = "POST",
            path = "/api/auth/login",
            body = "email=$encodedEmail&password=$encodedPassword",
            contentType = "application/x-www-form-urlencoded; charset=UTF-8",
            csrf = fetchCsrfToken(),
            unauthorizedMessage = "이메일 또는 비밀번호를 확인해 주세요."
        )

        return response.getJSONObject("data").getBoolean("passwordChangeRequired")
    }

    /**
     * 현재 로그인 세션을 서버에서 종료한다.
     * 로그아웃은 서버 상태를 변경하므로 CSRF 토큰을 함께 보낸다.
     * 서버 처리가 성공하면 앱에 보관한 세션 쿠키도 제거한다.
     */
    suspend fun logout() {
        // 현재 세션에서 사용할 CSRF 토큰을 먼저 받아온다.
        val csrf = fetchCsrfToken()

        // 서버가 세션 무효화와 인증 정보 제거를 처리한다.
        ApiClient.request(
            method = "POST",
            path = "/api/auth/logout",
            csrf = csrf
        )

        // 성공 응답을 확인한 뒤 앱의 세션 쿠키를 비운다.
        ApiClient.clearSessionCookies()
    }

    /**
     * 현재 앱의 세션 쿠키로 서버 로그인 상태를 확인한다.
     * 일반 로그인 상태면 true, 비로그인 또는 세션 만료면 false를 반환한다.
     * 네트워크 오류는 호출한 화면으로 전달해 재시도할 수 있게 한다.
     */
    suspend fun checkLoginStatus(): Boolean {
        return try {
            // ApiClient가 보관 중인 세션 쿠키를 요청에 함께 전달한다.
            val response = ApiClient.request(
                method = "GET",
                path = "/api/auth/me"
            )

            val data = response.getJSONObject("data")

            // 임시 비밀번호로 로그인한 세션은 일반 서비스 화면에 진입시키지 않는다.
            if (data.getBoolean("passwordChangeRequired")) {
                throw java.io.IOException(
                    "웹에서 비밀번호를 변경한 뒤 다시 로그인해 주세요."
                )
            }

            true
        } catch (e: UnauthorizedException) {
            // 서버가 비로그인으로 판단한 경우에만 false를 반환한다.
            false
        }
    }
}

