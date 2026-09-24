package com.example.apd_sinpe_check

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("api/comprobantes/validar")
    suspend fun subirComprobante(
        @Part imagen: MultipartBody.Part,
        @Part("monto_esperado") montoEsperado: RequestBody
    ): Response<Void>
}