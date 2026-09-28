# Módulo SINPE Check - Validación de Comprobantes (A.P.D.)

Módulo de software desarrollado para la validación automática de comprobantes SINPE Móvil mediante visión por computadora (OCR) y análisis de patrones de texto. Diseñado para integrarse en el ecosistema core de **A.P.D. Software Solutions**.

## 🏗 Arquitectura y Visión General

El módulo consta de un componente móvil (Android) y un motor de extracción OCR:

1. **Cliente Android (`com.example.apd_sinpe_check`):**
    - **Interfaz:** Formularios en Material Design para ingresar el monto esperado y cargar comprobantes desde cámara o galería.
    - **Motor OCR:** Integración con Google ML Kit Text Recognition para procesamiento local.
    - **Extracción de datos:** Extracción de Emisor, Receptor, Teléfono, Referencia, Monto y Fecha/Hora.
    - **Seguridad:** Validación de comprobantes duplicados y comprobación de montos.

2. **Estructura de Datos Devuelta:**
   ```kotlin
   data class SinpeDatosExtraidos(
       val emisor: String,
       val receptor: String,
       val telefono: String,
       val monto: Double,
       val moneda: String,
       val montoFormateado: String,
       val referencia: String,
       val fechaHora: String,
       val ocrConfianza: Double
   )