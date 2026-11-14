package com.myhome.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

    /**
     * MyHome 계정으로 로그인하고, 이후 API 요청에 사용할 세션 쿠키를 저장한다.
     * 반환값은 비밀번호 변경 필요 여부다. 임시 비밀번호로 로그인하면 true다.
     */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {

        // 백엔드는 로그인 요청에도 CSRF 토큰과 해당 토큰을 발급받은 세션 쿠키를 요구한다.
        val csrf = fetchCsrfToken()
        val url = URL("$BASE_URL/api/auth/login")
        val uri = url.toURI()
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(csrf.headerName, csrf.token)

            // 토큰을 받을 때 저장한 쿠키를 함께 보내 같은 세션의 요청임을 알린다.
            for ((name, values) in cookies.get(uri, emptyMap())) {
                for (value in values) {
                    connection.addRequestProperty(name, value)
                }
            }

            // 이메일과 비밀번호를 각각 인코딩해 +, &, = 같은 문자도 원래 값으로 전달한다.
            val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("email=$encodedEmail&password=$encodedPassword")
            }

            // 로그인 과정에서 세션 쿠키가 바뀔 수 있으므로 새 응답의 쿠키도 저장한다.
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields)

            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IOException("이메일 또는 비밀번호를 확인해 주세요.")
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("로그인 요청에 실패했습니다. (HTTP $status)")
            }

            // 정상 로그인과 임시 비밀번호 로그인을 구분해 화면에 알려준다.
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = JSONObject(body)
            if (!response.getBoolean("success")) {
                throw IOException("로그인에 실패했습니다.")
            }
            response.getJSONObject("data").getBoolean("passwordChangeRequired")

        } finally {
            connection.disconnect()
        }
    }

    /**
     * 로그인한 사용자의 장보기 목록에서 첫 페이지를 조회한다.
     *
     * @return 서버에서 받은 장보기 항목을 ShoppingItem 목록으로 반환한다.
     *         등록한 항목이 없으면 빈 목록을 반환한다.
     */
    suspend fun fetchShoppingItems(): List<ShoppingItem> = withContext(Dispatchers.IO) {

        // 백엔드의 페이지 번호는 1부터 시작한다. 첫 화면에는 최대 10개를 가져온다
        val url = URL("$BASE_URL/api/shopping-items?page=1&size=10")
        val uri = url.toURI()
        val connection = url.openConnection() as HttpURLConnection

        try {

            // 목록 조회는 GET 요청이다. 검색 조건은 URL에 넣고 요청 본문은 보내지 않는다.
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")

            // 로그인할 때 받은 세션 쿠키를 보낸다.
            // 백엔드는 이 쿠키로 사용자를 식별하므로 이메일이나 사용자 ID를 따로 보내지 않는다.
            for ((name, values) in cookies.get(uri, emptyMap())) {
                for (value in values) {
                    connection.addRequestProperty(name, value)
                }
            }

            // 응답 상태를 읽고, 서버가 쿠키를 갱신했다면 다음 요청을 위해 저장한다.
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields)

            // 로그인하지 않았거나 세션이 만료된 경우에는 화면에서 다시 로그인 하도록 안내한다.
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IOException("장보기 목록을 보려면 다시 로그인해 주세요.")
            }

            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("장보기 목록 조회에 실패했습니다. (HTTP $status)")
            }

            // 응답 본문을 문자열로 읽는다. use가 끝나면 읽기 스트림도 닫힌다.
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                reader -> reader.readText()
            }

            val response = JSONObject(body)
            if (!response.getBoolean("success")) {
                throw IOException("장보기 목록을 가져오지 못했습니다.")
            }

            // 백엔드는 data에 페이지 정보를 넣고, 실제 장보기 항목은 data.items 배열에 담아 보낸다.
            val items = response.getJSONObject("data").getJSONArray("items")

            // 서버의 각 항목을 기존 ShoppingItem으로 변환한다.
            // List(0)이면 변환할 항목이 없으므로 빈 목록이 그대로 반환된다.
            List(items.length()) { index ->
                val item = items.getJSONObject(index)

                // 구매일, 예정일, 메모는 비어 있을 수 있다. 서버의 null을 Kotlin의 null로 유지한다.
                fun optionalText(key: String): String? {
                    return if (item.isNull(key)) null else item.getString(key)
                }

                ShoppingItem(
                    id = item.getLong("id"),
                    name = item.getString("name"),

                    // 1.5kg 같은 소수 수량을 담을 수 있도록 기존 모델의 BigDecimal로 변환한다.
                    quantity = item.get("quantity").toString().toBigDecimal(),
                    unit = item.getString("unit"),
                    isCompleted = item.getBoolean("isCompleted"),
                    completedDate = optionalText("completedDate"),
                    purchaseDueDate = optionalText("purchaseDueDate"),
                    memo = optionalText("memo")
                )
            }

        } finally {
            // 조회 성공 여부에 관계없이 이번 HTTP 연결을 정리한다.
            connection.disconnect()
        }
    }
}