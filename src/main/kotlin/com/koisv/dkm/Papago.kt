package com.koisv.dkm

import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder


object Papago {
    enum class Lang(
        val strName: String
    ) {
        KO("한국어"), EN("English"), JA("日本語"),
        ZH_CN("中文 [CN]"), ZH_TW("中文 [TW]"), VI("Tiếng Việt"),
        ID("بهاس إندونيسيا"), TH("ภาษาไทย"), DE("Deutsch"),
        RU("Русский"), ES("Español"), IT("Lingua italiana"),
        FR("Français");
    }

    data class TransResult(
        val original: String,
        val result: String,
        val oLang: Int,
        val rLang: Int
    )

    @JvmStatic
    fun translate(data: String, id: String, secret: String, apiReplace: String = "https://openapi.naver.com/v1/papago/n2mt") {
        val text: String = try {
            URLEncoder.encode(data, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException("인코딩 실패", e)
        }
        val requestHeaders: MutableMap<String, String> = HashMap()
        requestHeaders["X-Naver-Client-Id"] = id
        requestHeaders["X-Naver-Client-Secret"] = secret
        val responseBody = post(apiReplace, requestHeaders, text)
        println(responseBody)
    }

    fun getLang(api: String, head: Map<String, String>, text: String): Lang? {
        val data = post(api, head, text, true)
        val findLang = data?.let {
            try {
                Lang.valueOf(
                    Json.parseToJsonElement(it).jsonObject["langCode"].toString()
                        .replace("-","_").uppercase().removeSurrounding("\"")
                )
            } catch (e: Exception) {
                e.printStack()
                null
            }
        }
        return findLang
    }

    private fun post(apiUrl: String, requestHeaders: Map<String, String>, text: String, detect: Boolean = false): String? {
        val con = connect(apiUrl)
        val postParams =
            if (!detect) "source=${requestHeaders["source"] ?: getLang(apiUrl, requestHeaders, text)}&target=${requestHeaders["target"]}&text=$text" //원본언어: 한국어 (ko) -> 목적언어: 영어 (en)
            else "query=$text"
        return if (con != null) try {
            con.requestMethod = "POST"
            for (header in requestHeaders.entries) {
                con.setRequestProperty(header.key, header.value)
            }
            con.doOutput = true
            DataOutputStream(con.outputStream).use { wr ->
                wr.write(postParams.toByteArray())
                wr.flush()
            }
            val responseCode = con.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) { // 정상 응답
                readBody(con.inputStream)
            } else {  // 에러 응답
                readBody(con.errorStream)
            }
        } catch (e: IOException) {
            throw RuntimeException("API 요청과 응답 실패", e)
        } finally {
            con.disconnect()
        } else null
    }

    private fun connect(apiUrl: String): HttpURLConnection? {
        return try {
            val url = URL(apiUrl)
            url.openConnection() as HttpURLConnection
        } catch (e: MalformedURLException) {
            throw RuntimeException("API URL이 잘못되었습니다. : $apiUrl", e)
        } catch (e: IOException) {
            throw RuntimeException("연결이 실패했습니다. : $apiUrl", e)
        }
    }

    private fun readBody(body: InputStream): String {
        val streamReader = InputStreamReader(body)
        try {
            BufferedReader(streamReader).use { lineReader ->
                val responseBody = StringBuilder()
                var line: String?
                while (lineReader.readLine().also { line = it } != null) {
                    responseBody.append(line)
                }
                return responseBody.toString()
            }
        } catch (e: IOException) {
            throw RuntimeException("API 응답을 읽는데 실패했습니다.", e)
        }
    }
}