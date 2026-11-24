package ru.plasmadynamics.visits.mobile.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/**
 * REST-клиент бекенда. 10.0.2.2 — это localhost хост-машины, как его видит
 * Android-эмулятор. Для реального устройства поменять на IP машины с беком
 * (или домен в проде).
 *
 * TODO(аутентификация): JWT в заголовок Authorization + обновление токена.
 */
const val BASE_URL = "http://10.0.2.2:8080"

class VisitsApi(private val baseUrl: String = BASE_URL) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    /** DEV: список пользователей для выбора инспектора (аутентификации пока нет). */
    suspend fun users(): List<UserOut> =
        client.get("$baseUrl/api/admin/users").body()

    suspend fun presign(request: PresignRequest): PresignResponse =
        client.post("$baseUrl/api/mobile/media/presign") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun sync(request: SyncRequest): SyncResponse =
        client.post("$baseUrl/api/mobile/sync") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Прямая загрузка файла в MinIO по presigned URL (мимо бекенда). */
    suspend fun upload(uploadUrl: String, file: File, contentType: String): Boolean {
        val originalHost = io.ktor.http.Url(uploadUrl).hostWithPort
        val response: HttpResponse = client.put(devRewrite(uploadUrl)) {
            header(HttpHeaders.Host, originalHost)
            header(HttpHeaders.ContentType, contentType)
            setBody(file.readBytes())
        }
        return response.status.isSuccess()
    }

    /**
     * DEV-костыль для эмулятора: presigned URL содержит хост MinIO, как его
     * видит бек (localhost:9000), а для эмулятора localhost — он сам.
     * TODO(прод): убрать; задать беку S3_ENDPOINT, доступный с телефона.
     */
    private fun devRewrite(url: String): String = url
        .replace("://localhost:", "://10.0.2.2:")
        .replace("://127.0.0.1:", "://10.0.2.2:")
}
