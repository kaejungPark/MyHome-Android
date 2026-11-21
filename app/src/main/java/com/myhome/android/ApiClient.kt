package com.myhome.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 서버가 HTTP 401을 반환했음을 나타낸다.
 * 로그인되지 않은 상태와 네트워크 연결 실패를 구분할 때 사용한다.
 */
class UnauthorizedException(message: String) : IOException(message)

/**
 * 서버가 반환한 HTTP 오류 코드를 보관한다.
 * 화면에서 409 수정 제한 등을 구분해 안내할 때 사용한다.
 */
class ApiHttpException( val statusCode: Int, message: String ) : IOException(message)

// 모든 API가 같은 서버 주소와 로그인 세션을 사용하도록 HTTP 통신을 담당한다.
object ApiClient {

    // 안드로이드 에뮬레이터에서 개발 PC의 Spring Boot 서버에 접속하는 주소다.
    private const val BASE_URL = "http://10.0.2.2:8080"

    // 로그인 시 발급된 세션 쿠키를 보관하고 이후 요청에 재사용한다.
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    // 보호된 API에서 인증 실패가 발생했는지 보관한다.
    // MainActivity가 이 값을 관찰해 로그인 화면으로 전환한다.
    private val _sessionExpired = MutableStateFlow(false)

    // 외부에서는 값을 읽기만 할 수 있도록 공개한다.
    val sessionExpired = _sessionExpired.asStateFlow()

    /**
     * 화면에서 세션 만료 처리를 끝냈거나 새 로그인에 성공하면
     * 이전 세션 만료 알림을 초기화한다.
     */
    fun clearSessionExpired() {
        _sessionExpired.value = false
    }

    /**
     * 앱 메모리에 보관한 세션 쿠키를 제거한다.
     * 서버 로그아웃에 성공한 뒤 호출해 다음 요청에 이전 쿠키가 전달되지 않게 한다.
     */
    fun clearSessionCookies() {
        cookies.cookieStore.removeAll()
    }

    /**
     * 서버에 요청을 보내고 성공 응답을 JSON으로 반환한다.
     * path는 /api/auth/login처럼 서버 주소 뒤에 붙는 경로다.
     * body는 폼이나 JSON을 전송할 때, csrf는 서버 데이터를 변경할 때 전달한다.
     * unauthorizedMessage는 로그인 실패와 세션 만료를 화면에서 구분하기 위한 문구다.
     */
    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String? = null,
        csrf: CsrfToken? = null,
        unauthorizedMessage: String = "다시 로그인해 주세요."
    ): JSONObject = withContext(Dispatchers.IO) {
        // 실제 네트워크 통신은 IO 스레드에서 실행한다.
        val url = URL("$BASE_URL$path")
        val uri = url.toURI()
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")

            // CSRF 토큰을 전달받은 요청에만 서버가 지정한 인증 헤더를 붙인다.
            if (csrf != null) {
                connection.setRequestProperty(csrf.headerName, csrf.token)
            }

            // 이 주소에 해당하는 쿠키를 요청 헤더에 넣어 로그인 세션을 이어간다.
            for ((name, values) in cookies.get(uri, emptyMap())) {
                for (value in values) {
                    connection.addRequestProperty(name, value)
                }
            }

            // 로그인은 폼 문자열, 장보기 등록·수정은 JSON 문자열을 보낸다.
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", requireNotNull(contentType))
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            // 로그인 직후 세션 쿠키가 바뀔 수 있으므로 매 응답에서 쿠키를 갱신한다.
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields)

            // 서버가 인증되지 않은 요청이라고 응답했을 때 처리한다.
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // 인증 API는 각 화면에서 결과를 처리한다.
                // 로그인 비밀번호 오류나 최초 로그인 확인을 세션 만료 알림에 포함하지 않는다.
                if (!path.startsWith("/api/auth/")) {
                    _sessionExpired.value = true
                }

                // 현재 요청도 실패로 종료해 이후 성공 처리가 실행되지 않게 한다.
                throw UnauthorizedException(unauthorizedMessage)
            }
            // 인증 실패는 위의 401 처리에서 다루고, 나머지 HTTP 오류를 전달한다.
            if (status !in 200..299) {
                throw ApiHttpException(
                    statusCode = status,
                    message = "서버 요청에 실패했습니다. (HTTP $status)"
                )
            }

            // 등록 성공(201)과 조회·수정 성공(200)을 모두 처리한다.
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }

            // HTTP 응답을 받았어도 백엔드가 처리 실패를 반환하면 화면에 이유를 전달한다.
            if (!response.getBoolean("success")) {
                val message = if (response.isNull("message")) {
                    "요청을 처리하지 못했습니다."
                } else {
                    response.getString("message")
                }
                throw IOException(message)
            }

            // withContext의 마지막 값이 request()의 반환값이 된다.
            response
        } finally {
            connection.disconnect()
        }
    }
}