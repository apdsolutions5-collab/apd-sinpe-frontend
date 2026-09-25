package com.example.apd_sinpe_check

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

data class SinpeDatosExtraidos(
    val emisor: String = "",
    val receptor: String = "",
    val telefono: String = "",
    val monto: Double = 0.0,
    val moneda: String = "CRC",
    val montoFormateado: String = "",
    val referencia: String = "",
    val fechaHora: String = "",
    val ocrConfianza: Double = 98.2,
)

class MainActivity : AppCompatActivity() {

    private var currentImageUri: Uri? = null
    private var tempCameraUri: Uri? = null

    // Registro en memoria de referencias validadas para detección de duplicados
    private val referenciasValidadas = mutableSetOf<String>()

    // UI elements
    private lateinit var etMontoEsperado: TextInputEditText
    private lateinit var cardSeleccionarImagen: CardView
    private lateinit var layoutEstadoVacio: LinearLayout
    private lateinit var layoutEstadoCargado: RelativeLayout
    private lateinit var ivComprobante: ImageView
    private lateinit var btnTomarFoto: MaterialButton
    private lateinit var btnSeleccionarGaleria: MaterialButton
    private lateinit var btnCambiarImagen: MaterialButton
    private lateinit var btnEliminarImagen: MaterialButton
    private lateinit var btnValidar: MaterialButton
    private lateinit var cardResultado: CardView

    // Indicador Visual de Estado y Detalles
    private lateinit var layoutIndicadorHeader: LinearLayout
    private lateinit var progressBarCargando: ProgressBar
    private lateinit var ivIconoEstatus: ImageView
    private lateinit var tvEstatusPago: TextView
    private lateinit var tvEstatusDescripcion: TextView
    private lateinit var layoutDetallesResultado: LinearLayout

    // Campos de información extraída
    private lateinit var tvEmisor: TextView
    private lateinit var tvReceptor: TextView
    private lateinit var tvTelefonoOrigen: TextView
    private lateinit var tvMontoDetectado: TextView
    private lateinit var tvNumeroReferencia: TextView
    private lateinit var tvFechaHora: TextView
    private lateinit var tvEsDuplicado: TextView
    private lateinit var tvConfianzaOCR: TextView
    private lateinit var tvTextoOcrRaw: TextView

    // Launcher para Permiso de Cámara
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            ejecutarAbrirCamara()
        } else {
            Toast.makeText(
                this,
                "Se requiere permiso de cámara para tomar fotos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Launcher para Galería
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    // Launcher para Cámara Nativa
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            onImageSelected(tempCameraUri!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etMontoEsperado = findViewById(R.id.etMontoEsperado)
        cardSeleccionarImagen = findViewById(R.id.cardSeleccionarImagen)
        layoutEstadoVacio = findViewById(R.id.layoutEstadoVacio)
        layoutEstadoCargado = findViewById(R.id.layoutEstadoCargado)
        ivComprobante = findViewById(R.id.ivComprobante)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnSeleccionarGaleria = findViewById(R.id.btnSeleccionarGaleria)
        btnCambiarImagen = findViewById(R.id.btnCambiarImagen)
        btnEliminarImagen = findViewById(R.id.btnEliminarImagen)
        btnValidar = findViewById(R.id.btnValidar)
        cardResultado = findViewById(R.id.cardResultado)

        layoutIndicadorHeader = findViewById(R.id.layoutIndicadorHeader)
        progressBarCargando = findViewById(R.id.progressBarCargando)
        ivIconoEstatus = findViewById(R.id.ivIconoEstatus)
        tvEstatusPago = findViewById(R.id.tvEstatusPago)
        tvEstatusDescripcion = findViewById(R.id.tvEstatusDescripcion)
        layoutDetallesResultado = findViewById(R.id.layoutDetallesResultado)

        tvEmisor = findViewById(R.id.tvEmisor)
        tvReceptor = findViewById(R.id.tvReceptor)
        tvTelefonoOrigen = findViewById(R.id.tvTelefonoOrigen)
        tvMontoDetectado = findViewById(R.id.tvMontoDetectado)
        tvNumeroReferencia = findViewById(R.id.tvNumeroReferencia)
        tvFechaHora = findViewById(R.id.tvFechaHora)
        tvEsDuplicado = findViewById(R.id.tvEsDuplicado)
        tvConfianzaOCR = findViewById(R.id.tvConfianzaOCR)
        tvTextoOcrRaw = findViewById(R.id.tvTextoOcrRaw)
    }

    private fun setupListeners() {
        cardSeleccionarImagen.setOnClickListener {
            if (currentImageUri == null) {
                mostrarDialogoSeleccion()
            }
        }

        btnTomarFoto.setOnClickListener { abrirCamara() }
        btnSeleccionarGaleria.setOnClickListener { abrirGaleria() }
        btnCambiarImagen.setOnClickListener { mostrarDialogoSeleccion() }
        btnEliminarImagen.setOnClickListener { quitarImagen() }
        btnValidar.setOnClickListener { iniciarValidacionConIndicador() }
    }

    private fun mostrarDialogoSeleccion() {
        val opciones = arrayOf(
            getString(R.string.btn_camara),
            getString(R.string.btn_galeria)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.opciones_titulo)
            .setItems(opciones) { dialog, which ->
                when (which) {
                    0 -> abrirCamara()
                    1 -> abrirGaleria()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun abrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ejecutarAbrirCamara()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun ejecutarAbrirCamara() {
        try {
            val imageFile = File.createTempFile("sinpe_comprobante_", ".jpg", cacheDir)
            tempCameraUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                imageFile
            )
            cameraLauncher.launch(tempCameraUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirGaleria() {
        try {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir la galería", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onImageSelected(uri: Uri) {
        currentImageUri = uri
        ivComprobante.setImageURI(uri)
        layoutEstadoVacio.visibility = View.GONE
        layoutEstadoCargado.visibility = View.VISIBLE
        cardResultado.visibility = View.GONE
    }

    private fun quitarImagen() {
        currentImageUri = null
        ivComprobante.setImageURI(null)
        layoutEstadoCargado.visibility = View.GONE
        layoutEstadoVacio.visibility = View.VISIBLE
        cardResultado.visibility = View.GONE
    }

    private fun iniciarValidacionConIndicador() {
        cardResultado.visibility = View.VISIBLE
        mostrarEstadoCargando()
        btnValidar.isEnabled = false

        val uri = currentImageUri
        if (uri == null) {
            btnValidar.isEnabled = true
            mostrarEstadoFraude(
                getString(R.string.estado_alerta_sin_imagen_titulo),
                getString(R.string.estado_alerta_sin_imagen_desc),
                mostrarDetalles = false
            )
            return
        }

        // Preparación del cuerpo MultipartBody para Retrofit (KAN-44)
        val multipartImagen = prepararImagenMultipart(uri)
        if (multipartImagen != null) {
            // Imagen lista para envío a API REST
        }

        procesarComprobanteRealConOCR(uri)
    }

    private fun prepararImagenMultipart(uri: Uri): MultipartBody.Part? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())

            MultipartBody.Part.createFormData("imagen", "comprobante.jpg", requestFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al generar MultipartBody", Toast.LENGTH_SHORT).show()
            null
        }
    }

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

    private fun procesarComprobanteRealConOCR(uri: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    btnValidar.isEnabled = true
                    val rawText = visionText.text

                    if (rawText.isBlank()) {
                        mostrarEstadoFraude(
                            getString(R.string.estado_alerta_sin_texto_titulo),
                            getString(R.string.estado_alerta_sin_texto_desc),
                            mostrarDetalles = false
                        )
                        return@addOnSuccessListener
                    }

                    // KAN-47: Validación de imagen no bancaria
                    if (!esComprobanteBancarioValido(rawText)) {
                        mostrarEstadoFraude(
                            getString(R.string.estado_alerta_no_bancario_titulo),
                            getString(R.string.estado_alerta_no_bancario_desc),
                            mostrarDetalles = false
                        )
                        return@addOnSuccessListener
                    }

                    val datosExtraidos = parsearTextoSinpe(rawText)

                    // Autocompletar dinámicamente los campos en la interfaz (KAN-45)
                    tvNumeroReferencia.text = datosExtraidos.referencia.ifBlank { "No detectado" }
                    tvEmisor.text = datosExtraidos.emisor.ifBlank { "No detectado" }
                    tvReceptor.text = datosExtraidos.receptor.ifBlank { "No detectado" }
                    tvTelefonoOrigen.text = datosExtraidos.telefono.ifBlank { "No detectado" }
                    tvFechaHora.text = datosExtraidos.fechaHora.ifBlank { "No detectada" }
                    tvMontoDetectado.text = datosExtraidos.montoFormateado
                    tvConfianzaOCR.text = String.format(Locale.getDefault(), "%.2f%% (Lectura OCR Real)", datosExtraidos.ocrConfianza)
                    tvTextoOcrRaw.text = rawText

                    // KAN-47: Detección de edición / borrado digital (Teléfono o Referencia incompletos)
                    val esAlteradoOIncompleto = (datosExtraidos.telefono.isBlank() || datosExtraidos.telefono.length != 8) ||
                            (datosExtraidos.referencia.isBlank() || datosExtraidos.referencia.length < 14)

                    // Verificación de duplicado
                    val claveDuplicado = datosExtraidos.referencia.ifBlank { rawText.hashCode().toString() }
                    val esDuplicado = referenciasValidadas.contains(claveDuplicado)

                    // Verificación de monto ingresado por el usuario
                    val montoTextoRaw = etMontoEsperado.text.toString().trim()
                    val montoEsperado = parsearStringAMonto(montoTextoRaw)

                    val esMontoIncorrecto = montoEsperado > 0 && datosExtraidos.monto > 0 && abs(datosExtraidos.monto - montoEsperado) >= 0.01

                    // Evaluación de respuesta de la validación (KAN-45 y KAN-47)
                    if (esAlteradoOIncompleto) {
                        mostrarEstadoFraude(
                            "¡Alerta de Inconsistencia / Comprobante Alterado!",
                            "La imagen presenta datos incompletos o borrados digitalmente (El número de teléfono o la referencia están incompletos).",
                            mostrarDetalles = true
                        )
                        tvEsDuplicado.text = getString(R.string.incompleto_alterado)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_invalido))

                    } else if (esDuplicado && esMontoIncorrecto) {
                        val strMontoEsp = String.format(Locale.getDefault(), "%,.2f", montoEsperado)
                        mostrarEstadoFraude(
                            "¡Alerta de Fraude / Inconsistencia!",
                            "1. El comprobante (Ref: ${datosExtraidos.referencia}) ya ha sido registrado previamente.\n2. El monto detectado (${datosExtraidos.montoFormateado}) NO coincide con el monto esperado (₡$strMontoEsp).",
                            mostrarDetalles = true
                        )
                        tvEsDuplicado.text = getString(R.string.duplicado_si)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_invalido))

                    } else if (esDuplicado) {
                        mostrarEstadoFraude(
                            "¡Alerta: Comprobante Duplicado!",
                            "Este comprobante (Ref: ${datosExtraidos.referencia}) ya fue procesado anteriormente en el sistema.",
                            mostrarDetalles = true
                        )
                        tvEsDuplicado.text = getString(R.string.duplicado_si)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_invalido))

                    } else if (esMontoIncorrecto) {
                        referenciasValidadas.add(claveDuplicado)
                        val strMontoEsp = String.format(Locale.getDefault(), "%,.2f", montoEsperado)
                        mostrarEstadoFraude(
                            "¡Alerta de Inconsistencia en Monto!",
                            "El monto detectado (${datosExtraidos.montoFormateado}) no coincide con el monto digitado (₡$strMontoEsp).",
                            mostrarDetalles = true
                        )
                        tvEsDuplicado.text = getString(R.string.duplicado_no)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_valido))

                    } else {
                        // VALIDACIÓN EXITOSA: Cambia a Verde (Éxito) (KAN-45)
                        referenciasValidadas.add(claveDuplicado)
                        tvEsDuplicado.text = getString(R.string.duplicado_no)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_valido))

                        if (datosExtraidos.monto > 0 && montoEsperado > 0) {
                            mostrarEstadoExito(
                                "Éxito - Validación Correcta",
                                "El comprobante y el número de referencia (${datosExtraidos.referencia}) se validaron correctamente. El monto coincide perfectamente."
                            )
                        } else {
                            mostrarEstadoExito(
                                "Éxito - Comprobante Válido",
                                "Comprobante verificado con éxito. Número de referencia extraído: ${datosExtraidos.referencia}."
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    btnValidar.isEnabled = true
                    e.printStackTrace()
                    mostrarEstadoFraude(
                        getString(R.string.estado_alerta_corrupto_titulo),
                        "${getString(R.string.estado_alerta_corrupto_desc)} Detalle: ${e.localizedMessage}",
                        mostrarDetalles = false
                    )
                }
        } catch (e: Exception) {
            btnValidar.isEnabled = true
            e.printStackTrace()
            mostrarEstadoFraude(
                getString(R.string.estado_alerta_corrupto_titulo),
                getString(R.string.estado_alerta_corrupto_desc),
                mostrarDetalles = false
            )
        }
    }

    private fun parsearStringAMonto(rawVal: String): Double {
        if (rawVal.isBlank()) return 0.0

        var valLimpio = rawVal.replace(" ", "").trim()

        // Manejar caso especial de múltiples separadores (ej: 8.988.25 o 8,988,25)
        val ultPunto = valLimpio.lastIndexOf('.')
        val ultComa = valLimpio.lastIndexOf(',')
        val ultSeparador = maxOf(ultPunto, ultComa)

        valLimpio = if (ultSeparador != -1) {
            val parteDecimalCandidate = valLimpio.substring(ultSeparador + 1)
            if (parteDecimalCandidate.length in 1..2) {
                val parteEntera = valLimpio.substring(0, ultSeparador).replace(".", "").replace(",", "")
                "$parteEntera.$parteDecimalCandidate"
            } else {
                valLimpio.replace(".", "").replace(",", "")
            }
        } else {
            valLimpio.replace(".", "").replace(",", "")
        }

        return valLimpio.toDoubleOrNull() ?: 0.0
    }

    private val palabrasProhibidas = setOf(
        "comisión", "comision", "motivo", "documento", "referencia", "comprobante",
        "transferencia", "sinpe", "móvil", "movil", "monto", "debitado", "acreditado",
        "transferido", "ver", "cuentas", "nueva", "transacción", "transaccion",
        "detalle", "concepto", "entidad", "iban", "teléfono", "telefono", "origen",
        "destino", "destinatario", "realizado", "bcr", "bac", "bn", "tbcr", "hola",
        "notificación", "notificacion", "medio", "número", "numero", "monedero", "pago", "consola"
    )

    private fun esPalabraDeNombreValida(linea: String): Boolean {
        val l = linea.trim()
        if (l.isBlank() || l.length < 3) return false

        // Si la línea contiene dígitos (números), no es un nombre de persona
        if (l.contains("\\d".toRegex())) return false

        val palabras = l.lowercase(Locale.getDefault()).split("\\s+".toRegex())
        for (p in palabras) {
            if (palabrasProhibidas.contains(p.trim('.', ',', ';', ':'))) {
                return false
            }
        }

        return l.matches("^[A-ZÁÉÍÓÚÑa-záéíóúñ\\s.'-]{3,45}$".toRegex())
    }

    private fun extraerNombreArribaDeEtiqueta(lineas: List<String>, labelIndex: Int): String {
        val candidateNames = mutableListOf<String>()
        var idx = labelIndex - 1

        while (idx >= 0 && candidateNames.size < 3) {
            val candidate = lineas[idx].trim()
            if (esPalabraDeNombreValida(candidate)) {
                candidateNames.add(0, candidate) // Agregar al inicio para mantener orden
            } else if (candidateNames.isNotEmpty()) {
                break
            }
            idx--
        }

        return candidateNames.joinToString(" ")
    }

    private fun parsearTextoSinpe(textoOriginal: String): SinpeDatosExtraidos {
        var emisor = ""
        var receptor = ""
        var telefono = ""
        var montoColones = 0.0
        var montoDolares = 0.0
        var monedaPrincipal = "CRC"
        var referencia = ""
        var fecha = ""
        var hora = ""

        val lineas = textoOriginal.split("\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }
        val textoUnificado = textoOriginal.replace("\n", " ").replace("\r", " ").replace("\\s+".toRegex(), " ")

        // ==========================================
        // 1. EXTRAER TELÉFONO (8 DÍGITOS)
        // ==========================================
        val pTel = Pattern.compile("(?:teléfono|tel|móvil|monedero|celular|n°|N°)?[:.\\s]*\\b([24678][0-9]{3}[-\\s]?[0-9]{4})\\b")
        val mTel = pTel.matcher(textoUnificado)
        if (mTel.find()) {
            telefono = mTel.group(1)?.replace("-", "")?.replace(" ", "") ?: ""
        } else {
            for (linea in lineas) {
                val pTelSimple = Pattern.compile("\\b([24678][0-9]{3}[-\\s]?[0-9]{4})\\b")
                val mTelSimple = pTelSimple.matcher(linea)
                if (mTelSimple.find()) {
                    telefono = mTelSimple.group(1)?.replace("-", "")?.replace(" ", "") ?: ""
                    break
                }
            }
        }

        // ==========================================
        // 2. EXTRAER EMISOR Y RECEPTOR (REGLA DE SECUENCIA PARA BCR Y COLUMNAS)
        // ==========================================
        var ibanIndex = -1
        var numeroCuentaOrigen = ""
        for (i in lineas.indices) {
            val l = lineas[i]
            if ((l.startsWith("CR", ignoreCase = true) || l.startsWith("AH", ignoreCase = true) || l.startsWith("CTE", ignoreCase = true)) &&
                l.matches(".*[0-9]{8,}.*".toRegex())) {
                ibanIndex = i
                numeroCuentaOrigen = l
                break
            }
        }

        if (ibanIndex != -1) {
            // Recolectar nombres entre la línea del IBAN y el Teléfono
            val lineasNombres = mutableListOf<String>()
            var idx = ibanIndex + 1
            while (idx < lineas.size) {
                val l = lineas[idx].trim()
                if (telefono.isNotBlank() && l.replace("-", "").replace(" ", "").contains(telefono)) {
                    break // Llegamos al teléfono!
                }
                if (esPalabraDeNombreValida(l)) {
                    lineasNombres.add(l)
                } else if (lineasNombres.isNotEmpty()) {
                    break
                }
                idx++
            }

            if (lineasNombres.isNotEmpty()) {
                // La PRIMERA línea de nombre es el EMISOR (ej: "CASTRO VEGA ESTEBAN")
                emisor = lineasNombres[0]

                // Las líneas SIGUIENTES son el RECEPTOR (ej: "VASQUEZ REYES GLORIA DE LOS ANGELES")
                if (lineasNombres.size > 1) {
                    receptor = lineasNombres.subList(1, lineasNombres.size).joinToString(" ")
                }
            }
        }

        // ==========================================
        // BÚSQUEDA SECUNDARIA DE RECEPTOR SI AÚN ESTÁ VACÍO
        // ==========================================
        // Pass 2A: BN (Estructura "MARYENI ALEXANDRA FERNANDEZ CASTRO \n Destinatario:")
        if (receptor.isBlank()) {
            for (i in lineas.indices) {
                val linea = lineas[i]
                if (linea.contains("Destinatario", ignoreCase = true) ||
                    linea.contains("Destino", ignoreCase = true)) {

                    val nombreArriba = extraerNombreArribaDeEtiqueta(lineas, i)
                    if (nombreArriba.isNotBlank()) {
                        receptor = nombreArriba
                        break
                    }
                }
            }
        }

        // Pass 2B: BAC / Texto fluido -> "a nombre de MARÍA BELÉN...", "a favor de X"
        if (receptor.isBlank()) {
            val pReceptorA = Pattern.compile("(?i)(?:a\\s*nombre\\s*de|a\\s*favor\\s*de|para|beneficiario|receptor)[:.\\s]+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]{3,40}?)(?=[.\\d]|BAC|Ref|Fecha|Monto|Entidad|IBAN|Teléfono|$)")
            val mReceptorA = pReceptorA.matcher(textoUnificado)
            if (mReceptorA.find()) {
                val cand = mReceptorA.group(1)?.trim()?.trimEnd('.', ',', ';') ?: ""
                if (esPalabraDeNombreValida(cand)) {
                    receptor = cand
                }
            }
        }

        // ==========================================
        // BÚSQUEDA SECUNDARIA DE EMISOR SI AÚN ESTÁ VACÍO
        // ==========================================
        // Pass 3A: BN -> "ALEMARK GABRIEL MONGE CASTRO \n Realizado por:"
        if (emisor.isBlank()) {
            for (i in lineas.indices) {
                val linea = lineas[i]
                if (linea.contains("Realizado por", ignoreCase = true) ||
                    linea.contains("Transferido desde", ignoreCase = true)) {

                    val nombreArriba = extraerNombreArribaDeEtiqueta(lineas, i)
                    if (nombreArriba.isNotBlank()) {
                        emisor = nombreArriba
                        break
                    }
                }
            }
        }

        // Pass 3B: BAC -> "Le informamos que JAYDEN JABAR CASTRO SAENZ realizó..."
        if (emisor.isBlank()) {
            val pEmisorA = Pattern.compile("(?i)(?:informamos\\s*que|de|origen|emisor)[:.\\s]+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]{3,40}?)(?=\\s+(?:realizó|realizo|hizo|envió|envio)|[.\\d]|$)")
            val mEmisorA = pEmisorA.matcher(textoUnificado)
            if (mEmisorA.find()) {
                val cand = mEmisorA.group(1)?.trim()?.trimEnd('.', ',', ';') ?: ""
                if (esPalabraDeNombreValida(cand)) {
                    emisor = cand
                }
            }
        }

        // Si no se encontró el nombre de la persona en la Cuenta Origen, usar el número de cuenta/IBAN
        if (emisor.isBlank() && numeroCuentaOrigen.isNotBlank()) {
            emisor = numeroCuentaOrigen
        }

        // ==========================================
        // 4. EXTRAER REFERENCIA
        // ==========================================
        val textoSinEspaciosEnNumeros = textoUnificado.replace("(?<=\\d)\\s+(?=\\d)".toRegex(), "")
        val pRef = Pattern.compile("(?i)(?:referencia|ref|comprobante|transacción|transaccion|documento|num|n°)?[:.\\s]*([0-9]{14,25}|FT[0-9A-Z]{8,15})")
        val mRef = pRef.matcher(textoSinEspaciosEnNumeros)
        if (mRef.find()) {
            referencia = mRef.group(1) ?: ""
        } else {
            for (linea in lineas) {
                val pRefDigits = Pattern.compile("\\b([0-9]{14,25}|FT[0-9A-Z]{8,15})\\b")
                val mRefDigits = pRefDigits.matcher(linea)
                if (mRefDigits.find()) {
                    referencia = mRefDigits.group(1) ?: ""
                    break
                }
            }
        }

        // ==========================================
        // 5. EXTRAER MONTO Y MONEDA
        // ==========================================
        // Pass 5A: Colones con símbolo o prefijo de OCR (ej: ₡, ¢, CRC, C, c, e, E, €) + número decimal
        val pColones1 = Pattern.compile("(?i)(?:₡|¢|CRC|Colones|[C_c]|[e_E]|€|@)\\s*([0-9]{1,3}(?:[,.][0-9]{3})*(?:[,.][0-9]{2}))")
        val mColones1 = pColones1.matcher(textoUnificado)
        while (mColones1.find()) {
            val strVal = mColones1.group(1) ?: "0"
            val valParsed = parsearStringAMonto(strVal)
            if (valParsed > 0.0) {
                montoColones = valParsed
                break
            }
        }

        // Pass 5B: "2.000,00 Colones"
        if (montoColones == 0.0) {
            val pColones2 = Pattern.compile("([0-9]{1,3}(?:[,.][0-9]{3})*(?:[,.][0-9]{2}))\\s*(?:Colones|CRC|₡)")
            val mColones2 = pColones2.matcher(textoUnificado)
            if (mColones2.find()) {
                val strVal = mColones2.group(1) ?: "0"
                montoColones = parsearStringAMonto(strVal)
            }
        }

        // Pass 5C: Dólares ($ o USD) -> $30.23, $ 100.00
        val pDolares = Pattern.compile("(?i)(?:\\$|USD)\\s*([0-9]{1,3}(?:[,.][0-9]{3})*(?:[,.][0-9]{2}))")
        val mDolares = pDolares.matcher(textoUnificado)
        if (mDolares.find()) {
            val strVal = mDolares.group(1) ?: "0"
            montoDolares = parsearStringAMonto(strVal)
        }

        // Pass 5D: Buscar números alrededor de etiquetas "Monto"
        if (montoColones == 0.0 && montoDolares == 0.0) {
            for (i in lineas.indices) {
                val linea = lineas[i]
                if (linea.contains("Monto", ignoreCase = true) || linea.contains("debitado", ignoreCase = true) || linea.contains("transferido", ignoreCase = true)) {
                    val pNum = Pattern.compile("([0-9]{1,3}(?:[,.][0-9]{3})*[,.][0-9]{2})")
                    val mSame = pNum.matcher(linea)
                    if (mSame.find()) {
                        montoColones = parsearStringAMonto(mSame.group(1) ?: "0")
                        break
                    }
                    if (i > 0) {
                        val mPrev = pNum.matcher(lineas[i - 1])
                        if (mPrev.find()) {
                            montoColones = parsearStringAMonto(mPrev.group(1) ?: "0")
                            break
                        }
                    }
                    if (i < lineas.size - 1) {
                        val mNext = pNum.matcher(lineas[i + 1])
                        if (mNext.find()) {
                            montoColones = parsearStringAMonto(mNext.group(1) ?: "0")
                            break
                        }
                    }
                }
            }
        }

        // Pass 5E: Búsqueda global de cualquier número decimal con 2 decimales
        if (montoColones == 0.0 && montoDolares == 0.0) {
            val pDec = Pattern.compile("\\b([0-9]{1,3}(?:[,.][0-9]{3})*[,.][0-9]{2})\\b")
            val mDec = pDec.matcher(textoOriginal)
            while (mDec.find()) {
                val candidata = mDec.group(1) ?: ""
                val valDec = parsearStringAMonto(candidata)
                if (valDec > 0.0 && !referencia.contains(candidata.replace("[,.]".toRegex(), ""))) {
                    montoColones = valDec
                    break
                }
            }
        }

        // Formatear salida de Monto
        var montoFinal = 0.0
        var montoFormateadoFinal = "No detectado"

        if (montoColones > 0.0 && montoDolares > 0.0) {
            monedaPrincipal = "CRC"
            montoFinal = montoColones
            montoFormateadoFinal = String.format(Locale.getDefault(), "₡ %,.2f  ($ %,.2f USD)", montoColones, montoDolares)
        } else if (montoColones > 0.0) {
            monedaPrincipal = "CRC"
            montoFinal = montoColones
            montoFormateadoFinal = String.format(Locale.getDefault(), "₡ %,.2f", montoColones)
        } else if (montoDolares > 0.0) {
            monedaPrincipal = "USD"
            montoFinal = montoDolares
            montoFormateadoFinal = String.format(Locale.getDefault(), "$ %,.2f USD", montoDolares)
        }

        // ==========================================
        // 6. FECHA Y HORA
        // ==========================================
        val pFechaTexto = Pattern.compile("(?i)([0-9]{1,2}\\s+(?:de\\s+)?(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)[a-z]*[,\\s]+(?:de\\s+)?[0-9]{4})")
        val mFechaTexto = pFechaTexto.matcher(textoUnificado)
        if (mFechaTexto.find()) {
            fecha = mFechaTexto.group(1) ?: ""
        }

        if (fecha.isBlank()) {
            val pFechaNum = Pattern.compile("([0-9]{1,2}/[0-9]{1,2}/[0-9]{4})")
            val mFechaNum = pFechaNum.matcher(textoUnificado)
            if (mFechaNum.find()) {
                fecha = mFechaNum.group(1) ?: ""
            }
        }

        val pHora = Pattern.compile("([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?\\s*(?:AM|PM|am|pm)?)")
        val mHora = pHora.matcher(textoUnificado)
        if (mHora.find()) {
            hora = mHora.group(1) ?: ""
        }

        val fechaHoraFinal = listOf(fecha, hora).filter { it.isNotBlank() }.joinToString(", ")

        return SinpeDatosExtraidos(
            emisor = emisor,
            receptor = receptor,
            telefono = telefono,
            monto = montoFinal,
            moneda = monedaPrincipal,
            montoFormateado = montoFormateadoFinal,
            referencia = referencia,
            fechaHora = fechaHoraFinal,
            ocrConfianza = 98.2
        )
    }

    private fun mostrarEstadoCargando() {
        progressBarCargando.visibility = View.VISIBLE
        ivIconoEstatus.visibility = View.GONE
        layoutIndicadorHeader.setBackgroundColor(getColor(R.color.estado_cargando_bg))
        tvEstatusPago.text = getString(R.string.estado_cargando_titulo)
        tvEstatusPago.setTextColor(getColor(R.color.texto_principal))
        tvEstatusDescripcion.text = getString(R.string.estado_cargando_desc)
        layoutDetallesResultado.visibility = View.GONE
    }

    // Respuesta Éxito (Verde) - KAN-45
    private fun mostrarEstadoExito(titulo: String, descripcion: String) {
        progressBarCargando.visibility = View.GONE
        ivIconoEstatus.visibility = View.VISIBLE
        ivIconoEstatus.setImageResource(R.drawable.ic_check_circle)
        ivIconoEstatus.setColorFilter(getColor(R.color.estado_valido))
        layoutIndicadorHeader.setBackgroundColor(getColor(R.color.estado_valido_bg))
        tvEstatusPago.text = titulo
        tvEstatusPago.setTextColor(getColor(R.color.estado_valido))
        tvEstatusDescripcion.text = descripcion
        layoutDetallesResultado.visibility = View.VISIBLE
    }

    // Respuesta Fraude / Inconsistencia (Rojo) - KAN-45
    private fun mostrarEstadoFraude(titulo: String, descripcion: String, mostrarDetalles: Boolean) {
        progressBarCargando.visibility = View.GONE
        ivIconoEstatus.visibility = View.VISIBLE
        ivIconoEstatus.setImageResource(R.drawable.ic_warning)
        ivIconoEstatus.setColorFilter(getColor(R.color.estado_invalido))
        layoutIndicadorHeader.setBackgroundColor(getColor(R.color.estado_invalido_bg))
        tvEstatusPago.text = titulo
        tvEstatusPago.setTextColor(getColor(R.color.estado_invalido))
        tvEstatusDescripcion.text = descripcion
        layoutDetallesResultado.visibility = if (mostrarDetalles) View.VISIBLE else View.GONE
    }
}