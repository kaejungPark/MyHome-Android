package com.myhome.android

import org.json.JSONObject

/**
 * 회원가입 전 이메일 인증과 회원 등록을 담당한다.
 * 각 요청은 기존 ApiClient의 세션 쿠키와 CSRF 처리를 사용한다.
 */
object SignupApi {

    /**
     * 인증번호를 발송하고 해당 발송 요청의 ID를 반환한다.
     * 재발송 간격과 인증번호 유효기간은 서버에서 검사한다.
     */
    suspend fun sendCode(email: String): String {
        val body = JSONObject().apply {
            put("email", email.trim())
        }

        val response = ApiClient.request(
            method = "POST",
            path = "/api/auth/signup/email-verifications/send",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = AuthApi.fetchCsrfToken()
        )

        return response.getJSONObject("data").getString("requestId")
    }

    /**
     * 이메일, 발송 요청 ID, 인증번호를 확인한다.
     * 성공하면 회원가입에 사용할 일회용 인증 확인값을 반환한다.
     */
    suspend fun confirmCode(
        email: String,
        requestId: String,
        code: String
    ): String {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("requestId", requestId)
            put("code", code.trim())
        }

        val response = ApiClient.request(
            method = "POST",
            path = "/api/auth/signup/email-verifications/confirm",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = AuthApi.fetchCsrfToken()
        )

        return response.getJSONObject("data").getString("verificationToken")
    }

    /**
     * 이메일 인증을 마친 사용자의 회원가입 정보를 서버에 전달한다.
     * 이름, 나이, 성별은 선택 항목이므로 입력하지 않았다면 null로 보낸다.
     * 가입이 완료되어도 자동 로그인되지는 않는다.
     */
    suspend fun signup(
        email: String,
        name: String,
        nickname: String,
        password: String,
        age: Int?,
        gender: String?,
        verificationToken: String,
        emailNotificationEnabled: Boolean
    ) {
        // 백엔드의 회원가입 요청 형식에 맞춰 JSON을 구성한다.
        val body = JSONObject().apply {
            put("email", email.trim())
            put("nickname", nickname.trim())

            // 비밀번호는 사용자가 입력한 그대로 전달한다.
            put("password", password)

            // 이메일 인증 완료 후 발급받은 토큰을 전달한다.
            put("emailVerificationToken", verificationToken)
            put("emailNotificationEnabled", emailNotificationEnabled)

            // 선택 항목의 빈값은 JSON의 null로 명시한다.
            put("name", name.trim().ifBlank { null } ?: JSONObject.NULL)
            put("age", age ?: JSONObject.NULL)
            put("gender", gender ?: JSONObject.NULL)
        }

        // 회원가입도 서버 상태를 변경하는 요청이므로 CSRF 토큰을 함께 보낸다.
        ApiClient.request(
            method = "POST",
            path = "/api/auth/signup",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = AuthApi.fetchCsrfToken()
        )
    }
}