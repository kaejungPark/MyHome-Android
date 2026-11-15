package com.myhome.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL

// 모든 API가 같은 서버 주소와 로그인 세션을 사용하도록 HTTP 통신을 담당한다.
object ApiClient {

    // 안드로이드 에뮬레이터에서 개발 PC의 Spring Boot 서버에 접속하는 주소다.
    private const val BASE_URL = "http://10.0.2.2:8080"

    // 로그인 시 발급된 세션 쿠키를 보관하고 이후 요청에 재사용한다.
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

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

            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IOException(unauthorizedMessage)
            }
            if (status !in 200..299) {
                throw IOException("서버 요청에 실패했습니다. (HTTP $status)")
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