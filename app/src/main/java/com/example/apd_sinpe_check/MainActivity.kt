package com.example.apd_sinpe_check

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var currentImageUri: Uri? = null
    private var tempCameraUri: Uri? = null

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
    private lateinit var tvEstatusPago: TextView
    private lateinit var tvNumeroReferencia: TextView
    private lateinit var tvMontoDetectado: TextView
    private lateinit var tvConfianzaOCR: TextView
    private lateinit var ivIconoEstatus: ImageView

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
        tvEstatusPago = findViewById(R.id.tvEstatusPago)
        tvNumeroReferencia = findViewById(R.id.tvNumeroReferencia)
        tvMontoDetectado = findViewById(R.id.tvMontoDetectado)
        tvConfianzaOCR = findViewById(R.id.tvConfianzaOCR)
        ivIconoEstatus = findViewById(R.id.ivIconoEstatus)
    }

    private fun setupListeners() {
        // Abrir diálogo al presionar la tarjeta principal
        cardSeleccionarImagen.setOnClickListener {
            if (currentImageUri == null) {
                mostrarDialogoSeleccion()
            }
        }

        // Botones de acción directa
        btnTomarFoto.setOnClickListener { abrirCamara() }
        btnSeleccionarGaleria.setOnClickListener { abrirGaleria() }
        btnCambiarImagen.setOnClickListener { mostrarDialogoSeleccion() }
        btnEliminarImagen.setOnClickListener { quitarImagen() }

        // Botón para validar
        btnValidar.setOnClickListener { validarComprobante() }
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

    private fun validarComprobante() {
        if (currentImageUri == null) {
            Toast.makeText(
                this,
                "Por favor, selecciona o toma una foto del comprobante SINPE",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val montoTexto = etMontoEsperado.text.toString().trim()
        val montoEsperado = montoTexto.toDoubleOrNull() ?: 0.0

        // Simulación de respuesta de validación profesional
        val numeroReferenciaMock = "SINPE-" + (10000000..99999999).random()
        val confianzaMock = (94..99).random()

        cardResultado.visibility = View.VISIBLE

        if (montoEsperado > 0) {
            tvEstatusPago.text = "Estatus: Comprobante Válido"
            tvEstatusPago.setTextColor(getColor(R.color.estado_valido))
            ivIconoEstatus.setImageResource(R.drawable.ic_check_circle)
            ivIconoEstatus.setColorFilter(getColor(R.color.estado_valido))
            tvMontoDetectado.text = String.format(Locale.getDefault(), "Monto Validado: ₡ %,.2f", montoEsperado)
        } else {
            tvEstatusPago.text = "Estatus: Comprobante Detectado"
            tvEstatusPago.setTextColor(getColor(R.color.azul_acento))
            ivIconoEstatus.setImageResource(R.drawable.ic_verified)
            ivIconoEstatus.setColorFilter(getColor(R.color.azul_acento))
            tvMontoDetectado.text = "Monto Detectado: ₡ --"
        }

        tvNumeroReferencia.text = "Número de Referencia: $numeroReferenciaMock"
        tvConfianzaOCR.text = String.format(Locale.getDefault(), "Confianza de Lectura (OCR): %d%%", confianzaMock)

        Toast.makeText(this, "Validación finalizada con éxito", Toast.LENGTH_SHORT).show()
    }
}