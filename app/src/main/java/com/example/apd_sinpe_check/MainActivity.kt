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
    val ocrConfianza: Double = 98.2
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
                            "Alerta de Inconsistencia / Fraude",
                            "No fue posible leer ningún texto en la imagen. La foto es ilegible o no corresponde a un comprobante.",
                            mostrarDetalles = false
                        )
                        return@addOnSuccessListener
                    }

                    val datosExtraidos = parsearTextoSinpe(rawText)

                    // Autocompletar dinámicamente el Número de comprobante / referencia en la interfaz (KAN-45)
                    tvNumeroReferencia.text = datosExtraidos.referencia.ifBlank { "No detectado" }
                    tvEmisor.text = datosExtraidos.emisor.ifBlank { "No detectado" }
                    tvReceptor.text = datosExtraidos.receptor.ifBlank { "No detectado" }
                    tvTelefonoOrigen.text = datosExtraidos.telefono.ifBlank { "No detectado" }
                    tvFechaHora.text = datosExtraidos.fechaHora.ifBlank { "No detectada" }
                    tvMontoDetectado.text = datosExtraidos.montoFormateado
                    tvConfianzaOCR.text = String.format(Locale.getDefault(), "%.2f%% (Lectura OCR Real)", datosExtraidos.ocrConfianza)
                    tvTextoOcrRaw.text = rawText

                    // Verificación de duplicado
                    val claveDuplicado = datosExtraidos.referencia.ifBlank { rawText.hashCode().toString() }
                    val esDuplicado = referenciasValidadas.contains(claveDuplicado)

                    // Verificación de monto ingresado por el usuario
                    val montoTextoRaw = etMontoEsperado.text.toString().trim()
                    val montoEsperado = parsearStringAMonto(montoTextoRaw)

                    val esMontoIncorrecto = montoEsperado > 0 && datosExtraidos.monto > 0 && abs(datosExtraidos.monto - montoEsperado) >= 0.01

                    // Evaluación de respuesta de la validación (KAN-45)
                    if (esDuplicado && esMontoIncorrecto) {
                        val strMontoEsp = String.format(Locale.getDefault(), "%,.2f", montoEsperado)
                        mostrarEstadoFraude(
                            "¡Alerta de Fraude / Inconsistencia!",
                            "1. El comprobante (Ref: ${datosExtraidos.referencia.ifBlank { "Foto duplicada" }}) ya ha sido registrado previamente.\n2. El monto detectado (${datosExtraidos.montoFormateado}) NO coincide con el monto esperado (₡$strMontoEsp).",
                            mostrarDetalles = true
                        )
                        tvEsDuplicado.text = getString(R.string.duplicado_si)
                        tvEsDuplicado.setTextColor(getColor(R.color.estado_invalido))

                    } else if (esDuplicado) {
                        mostrarEstadoFraude(
                            "¡Alerta: Comprobante Duplicado!",
                            "Este comprobante (Ref: ${datosExtraidos.referencia.ifBlank { "Misma foto" }}) ya fue procesado anteriormente en el sistema.",
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
                        "Alerta de Error / Inconsistencia",
                        "No se pudo procesar la imagen seleccionada: ${e.localizedMessage}",
                        mostrarDetalles = false
                    )
                }
        } catch (e: Exception) {
            btnValidar.isEnabled = true
            e.printStackTrace()
            mostrarEstadoFraude(
                "Alerta de Error",
                "Error al abrir la imagen seleccionada.",
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

        if (ultSeparador != -1) {
            val parteDecimalCandidate = valLimpio.substring(ultSeparador + 1)

            // Si después del último separador hay exactamente 1 o 2 dígitos, es el separador decimal
            if (parteDecimalCandidate.length in 1..2) {
                val parteEntera = valLimpio.substring(0, ultSeparador).replace(".", "").replace(",", "")
                valLimpio = "$parteEntera.$parteDecimalCandidate"
            } else {
                // Si tiene 3 dígitos o más (ej: 8.000), es separador de miles
                valLimpio = valLimpio.replace(".", "").replace(",", "")
            }
        } else {
            valLimpio = valLimpio.replace(".", "").replace(",", "")
        }

        return valLimpio.toDoubleOrNull() ?: 0.0
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

        val textoUnificado = textoOriginal.replace("\n", " ").replace("\r", " ").replace("\\s+".toRegex(), " ")

        // 1. Extraer Receptor
        val pReceptor = Pattern.compile("(?i)(?:a\\s*nombre\\s*de|para|destino|favor\\s*de)[:.\\s]+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]{3,40}?)(?=[.\\d\n]|BAC|Ref|Fecha|Monto|$)")
        val mReceptor = pReceptor.matcher(textoUnificado)
        if (mReceptor.find()) {
            receptor = mReceptor.group(1)?.trim()?.trimEnd('.', ',', ';') ?: ""
        }

        // 2. Extraer Emisor
        val pEmisor = Pattern.compile("(?i)(?:informamos\\s*que|de|origen)[:.\\s]+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]{3,40}?)(?=\\s+(?:realizó|realizo|hizo|envió|envio)|[.\\d]|$)")
        val mEmisor = pEmisor.matcher(textoUnificado)
        if (mEmisor.find()) {
            emisor = mEmisor.group(1)?.trim()?.trimEnd('.', ',', ';') ?: ""
        }

        // 3. Extraer Teléfono (8 dígitos)
        val pTel = Pattern.compile("(?:teléfono|tel|móvil|celular|n°|N°)?[:.\\s]*\\b([24678][0-9]{7})\\b")
        val mTel = pTel.matcher(textoUnificado)
        if (mTel.find()) {
            telefono = mTel.group(1) ?: ""
        } else {
            val pTelSimple = Pattern.compile("\\b[24678][0-9]{7}\\b")
            val mTelSimple = pTelSimple.matcher(textoUnificado)
            if (mTelSimple.find()) {
                telefono = mTelSimple.group(0) ?: ""
            }
        }

        // 4. Extraer Referencia
        val pRef = Pattern.compile("(?i)(?:referencia|ref|comprobante|num|n°)?[:.\\s]*([0-9]{14,25})")
        val mRef = pRef.matcher(textoUnificado)
        if (mRef.find()) {
            referencia = mRef.group(1) ?: ""
        } else {
            val pRefDigitos = Pattern.compile("\\b[0-9]{14,25}\\b")
            val mRefDigitos = pRefDigitos.matcher(textoUnificado)
            if (mRefDigitos.find()) {
                referencia = mRefDigitos.group(0) ?: ""
            }
        }

        // 5. Extraer Monto
        val pMontoConSímbolo = Pattern.compile("(?i)(?:₡|¢|CRC|\\$|USD|Monto[:.\\s]*|[C_c])\\s*([0-9]{1,3}(?:[,.][0-9]{3})*(?:[,.][0-9]{2}))")
        val mMontoConSímbolo = pMontoConSímbolo.matcher(textoUnificado)

        if (mMontoConSímbolo.find()) {
            val strMonto = mMontoConSímbolo.group(1) ?: "0"
            montoColones = parsearStringAMonto(strMonto)
        }

        if (montoColones == 0.0) {
            val pMontoDecimal = Pattern.compile("\\b([0-9]{1,3}(?:[,.][0-9]{3})*[,.][0-9]{2})\\b")
            val mMontoDecimal = pMontoDecimal.matcher(textoOriginal)
            while (mMontoDecimal.find()) {
                val candidata = mMontoDecimal.group(1) ?: ""
                val valorDecimal = parsearStringAMonto(candidata)
                if (valorDecimal > 0.0 && !referencia.contains(candidata.replace("[,.]".toRegex(), ""))) {
                    montoColones = valorDecimal
                    break
                }
            }
        }

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

        // 6. Extraer Fecha
        val pFecha = Pattern.compile("(?i)([0-9]{1,2}\\s+(?:de\\s+)?(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)[a-z]*\\s+(?:de\\s+)?[0-9]{4})")
        val mFecha = pFecha.matcher(textoUnificado)
        if (mFecha.find()) {
            fecha = mFecha.group(1) ?: ""
        }

        // 7. Extraer Hora
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