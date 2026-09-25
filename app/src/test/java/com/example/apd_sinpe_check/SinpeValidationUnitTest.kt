package com.example.apd_sinpe_check

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.regex.Pattern

class SinpeValidationUnitTest {

    private fun esComprobanteBancarioValido(texto: String): Boolean {
        val t = texto.lowercase(Locale.getDefault())
        val palabrasClaveBancarias = listOf(
            "sinpe", "bac", "bcr", "banco", "transferencia", "comprobante",
            "notificación", "notificacion", "monedero", "davivienda", "popular",
            "scotiabank", "wink", "promerica", "lafise", "monto", "referencia", "transacción", "transaccion"
        )

        var coincidenciaCount = 0
        for (palabra in palabrasClaveBancarias) {
            if (t.contains(palabra)) {
                coincidenciaCount++
            }
        }
        return coincidenciaCount >= 2
    }

    private fun extraerTelefono(texto: String): String {
        val pTel = Pattern.compile("\\b([24678][0-9]{7})\\b")
        val mTel = pTel.matcher(texto)
        return if (mTel.find()) mTel.group(1) ?: "" else ""
    }

    private fun extraerReferencia(texto: String): String {
        val pRef = Pattern.compile("(?i)(?:referencia|ref|comprobante)?[:.\\s]*([0-9]{14,25})")
        val mRef = pRef.matcher(texto.replace("(?<=\\d)\\s+(?=\\d)".toRegex(), ""))
        return if (mRef.find()) mRef.group(1) ?: "" else ""
    }

    @Test
    fun testComprobanteBancarioReal_esValido() {
        val textoBCR = """
            Comprobante Transferencia SINPE Móvil
            Documento 10556677
            Referencia 2024121976890167889012345
            CTE CR24653797449087642311
            CASTRO VEGA ESTEBAN
            VASQUEZ REYES GLORIA DE LOS ANGELES
            7144-6881
            e110.000,00
        """.trimIndent()

        assertTrue(esComprobanteBancarioValido(textoBCR))
    }

    @Test
    fun testImagenNoBancaria_o_Corrupta_esRechazada() {
        val textoFotoMeme = "Foto de un gato jugando en el jardin 2025"
        assertFalse(esComprobanteBancarioValido(textoFotoMeme))

        val textoTextoRamdom = "Certificado de notas escolares 2024"
        assertFalse(esComprobanteBancarioValido(textoTextoRamdom))
    }

    @Test
    fun testComprobanteAlterado_esDetectadoIncompleto() {
        val textoAlterado = """
            BAC Notificación de transferencia SINPE Móvil
            Le informamos que JAYDEN JABAR CASTRO SAENZ realizó una transferencia al teléfono Nº 72  97041 a nombre de MARIA BELEN SEGURA VARGAS.
            Referencia 202609231028400053
            Monto ₡4,499.99
        """.trimIndent()

        val telefono = extraerTelefono(textoAlterado)
        val referencia = extraerReferencia(textoAlterado)

        // El teléfono no tiene los 8 dígitos continuos debido al borrado digital ("72  97041")
        // La referencia está incompleta/borrada al final
        val esAlteradoOIncompleto = telefono.length != 8 || referencia.length < 14
        assertTrue(esAlteradoOIncompleto)
    }

    @Test
    fun testTextoVacio_esRechazado() {
        assertFalse(esComprobanteBancarioValido(""))
    }
}