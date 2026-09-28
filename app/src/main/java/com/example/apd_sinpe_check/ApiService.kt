package com.example.apd_sinpe_check

import com.google.gson.JsonParser
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class SinpeApiResponse(
    val estatus_pago: String? = null,
    val numero_referencia: String? = null,
    val porcentaje_confianza_ocr: Double? = null,
    val detalles: SinpeDetallesResponse? = null
)

data class SinpeDetallesResponse(
    val monto_esperado: Double? = null,
    val monto_coincide: Boolean? = null,
    val es_duplicado: Boolean? = null,
    val nombre_archivo: String? = null,
    val datos_comprobante: SinpeDatosComprobanteResponse? = null,
    val texto_ocr_raw: String? = null
)

data class SinpeDatosComprobanteResponse(
    val numero_comprobante: String? = null,
    val fecha_pago: String? = null,
    val hora_pago: String? = null,
    val telefono_origen: String? = null,
    val monto: String? = null,
    val moneda: String? = null,
    val monto_numerico: String? = null
)

data class ApiErrorResponse(
    val detail: String? = null,
    val error: String? = null,
    val message: String? = null
)

interface ApiService {
    @Multipart
    @POST("api/comprobantes/validar")
    suspend fun subirComprobante(
        @Part imagen: MultipartBody.Part,
        @Part("monto_esperado") montoEsperado: RequestBody
    ): Response<SinpeApiResponse>

    companion object {
        fun parsearErrorHttp400(errorBodyString: String?): String {
            if (errorBodyString.isNullOrBlank()) {
                return "Solicitud no válida (HTTP 400 Bad Request)."
            }
            return try {
                val jsonObject = JsonParser.parseString(errorBodyString).asJsonObject
                val detail = jsonObject.get("detail")?.asString ?: ""
                val error = jsonObject.get("error")?.asString ?: ""
                val message = jsonObject.get("message")?.asString ?: ""

                when {
                    detail.isNotBlank() -> detail
                    error.isNotBlank() -> error
                    message.isNotBlank() -> message
                    else -> "Error en parámetros de la solicitud (HTTP 400 Bad Request)."
                }
            } catch (e: Exception) {
                "Error 400 Bad Request: $errorBodyString"
            }
        }
    }
}