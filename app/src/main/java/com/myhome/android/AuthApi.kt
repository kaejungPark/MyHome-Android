package com.myhome.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL

// 로그인 요청에 사용할 CSRF 헤더 이름과 토큰 값을 함께 담는다.
data class CsrfToken(
    val headerName: String,
    val token: String
)

// 인증 API를 호출하고, 요청 간에 세션 쿠키를 공유한다.
object AuthApi {

    // 에뮬레이터에서 10.0.2.2를 사용하면 개발 PC에서 실행 중인 백엔드에 접속할 수 있다.
    private const val BASE_URL = "http://10.0.2.2:8080"

    // 서버가 발급한 쿠키를 메모리에 보관한다.
    // AuthApi 안에서 같은 CookieManager를 재사용하므로 다음 요청에도 세션 쿠키를 보낼 수 있다.
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    /**
     * 로그인 요청에 필요한 CSRF 헤더 이름과 토큰을 백엔드에서 받아온다.
     * 화면이 응답을 기다리는 동안에도 동작할 수 있도록 네트워크 작업은 Dispatchers.IO에서 실행한다.
     */
    suspend fun fetchCsrfToken(): CsrfToken = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/api/auth/csrf")

        // CookieManager는 URI를 기준으로 이 주소에 보낼 쿠키를 찾는다.
        val uri = url.toURI()
        val connection = url.openConnection() as HttpURLConnection

        try {
            // GET으로 토큰을 조회한다. 연결 대기와 응답 읽기에는 각각 5초 제한을 둔다.
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")

            // 이 주소에 보낼 쿠키가 있으면 요청 헤더에 추가해 기존 세션을 이어간다.
            for ((name, values) in cookies.get(uri, emptyMap())) {
                for (value in values) {
                    connection.addRequestProperty(name, value)
                }
            }

            // 응답을 받은 뒤, 서버가 새로 발급하거나 갱신한 쿠키를 다음 요청에 쓰도록 저장한다.
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields)

            // 먼저 HTTP 상태를 확인한다. 이 API는 정상 응답으로 200을 반환한다.
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("서버 응답 오류 (HTTP $status)")
            }

            // 응답 본문을 UTF-8 문자열로 읽는다. use 블록이 끝나면 읽기 스트림이 닫힌다.
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val response = JSONObject(body)

            // HTTP 응답을 정상적으로 받았어도 토큰 발급 자체는 실패할 수 있어 success도 확인한다.
            if (!response.getBoolean("success")) {
                throw IOException("서버에서 인증 정보를 발급하지 못했습니다.")
            }

            // 다음 로그인 요청에서 headerName을 헤더 이름으로, token을 그 값으로 사용한다.
            val data = response.getJSONObject("data")
            CsrfToken(
                headerName = data.getString("headerName"),
                token = data.getString("token")
            )
        } finally {
            // 응답을 읽거나 JSON을 해석하다 오류가 발생해도 연결은 정리한다.
            connection.disconnect()
        }
    }
}