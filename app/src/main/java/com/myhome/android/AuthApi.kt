package com.myhome.android

import java.net.URLEncoder
import org.json.JSONObject

// 서버가 지정한 CSRF 헤더 이름과 그 헤더에 넣을 토큰을 함께 보관한다.
data class CsrfToken(
    val headerName: String,
    val token: String
)

/**
 * 임시 비밀번호로 로그인한 세션임을 나타낸다.
 * 일반 오류 화면 대신 비밀번호 변경 화면을 표시할 때 사용한다.
 */
class PasswordChangeRequiredException : Exception()

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
     * 가입 이메일로 임시 비밀번호 발송을 요청한다.
     * 가입 여부가 노출되지 않도록 서버가 반환한 공통 안내를 표시한다.
     * 성공 응답은 실제 메일 도착을 보장하는 의미는 아니다.
     */
    suspend fun requestPasswordReset(email: String): String {
        // 비밀번호 찾기 API가 받는 이메일 정보를 구성한다.
        val body = JSONObject().apply {
            put("email", email.trim())
        }

        // 로그인 전 요청이지만 서버의 CSRF 검사는 그대로 적용한다.
        val response = ApiClient.request(
            method = "POST",
            path = "/api/auth/password-reset",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = fetchCsrfToken()
        )

        // 서버는 data에 사용자에게 보여줄 안내 문구를 반환한다.
        return response.optString("data").ifBlank {
            "발송 가능한 계정이면 임시 비밀번호가 전송됩니다. 메일함을 확인해 주세요."
        }
    }

    /**
     * 임시 비밀번호를 확인하고 새 비밀번호를 저장한다.
     * 서버가 변경 완료 후 세션을 종료하므로 앱의 쿠키도 제거한다.
     * 변경 후에는 새 비밀번호로 다시 로그인해야 한다.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ) {
        // 비밀번호의 공백도 실제 값이므로 trim하지 않는다.
        val body = JSONObject().apply {
            put("currentPassword", currentPassword)
            put("newPassword", newPassword)
        }

        ApiClient.request(
            method = "PUT",
            path = "/api/auth/password",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = fetchCsrfToken(),
            unauthorizedMessage = "로그인 시간이 만료되었습니다. 다시 로그인해 주세요."
        )

        // 저장 성공을 확인한 뒤 이전 로그인 정보를 제거한다.
        ApiClient.clearSessionCookies()
        ApiClient.clearSessionExpired()
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

            // 임시 로그인 세션이면 앱의 비밀번호 변경 화면으로 이동시킨다.
            if (data.getBoolean("passwordChangeRequired")) {
                throw PasswordChangeRequiredException()
            }

            true
        } catch (e: UnauthorizedException) {
            // 서버가 비로그인으로 판단한 경우에만 false를 반환한다.
            false
        }
    }
}

