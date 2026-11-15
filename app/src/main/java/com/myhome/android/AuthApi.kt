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
}