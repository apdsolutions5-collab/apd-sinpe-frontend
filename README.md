# Módulo SINPE Check - Validación de Comprobantes (A.P.D.)

Módulo de software desarrollado para la validación automática de comprobantes SINPE Móvil mediante visión por computadora (OCR) y análisis de firmas/patrones de texto. Diseñado para integrarse de forma transparente en el ecosistema core de **A.P.D. Software Solutions**.

## Architecture & Technical Overview

El módulo consta de un componente móvil (Android) y un motor de extracción OCR en el cliente/backend:

1. **Android Client (`com.example.apd_sinpe_check`):**
    - **UI Layer:** Formularios con Material Design para captura de monto esperado e importación de comprobantes (Cámara nativa / Galería).
    - **OCR Processing Engine:** Integración con Google ML Kit Text Recognition para procesamiento rápido *on-device*.
    - **Data Extraction & Regex Parsing:** Normalización de cadenas y procesamiento mediante patrones para extracción de datos clave:
        - `Emisor`
        - `Receptor`
        - `Número de Referencia`
        - `Monto Transaccionado`
        - `Fecha y Hora`
    - **Anti-Fraud & Consistency Engine:**
        - Detección de comprobantes duplicados mediante registro de referencias.
        - Comparación precisa de montos ingresados vs. detectados (manejo robusto de separadores de miles y decimales).

2. **Backend API Service (Python):**
    - Procesamiento de solicitudes multipart para imágenes de comprobantes.
    - Manejo centralizado de excepciones con respuestas estructuradas `400 Bad Request` ante inconsistencias o imágenes corruptas.

---

## Integración y Guía para Desarrolladores

Para integrar este módulo dentro de otros productos core de A.P.D., siga estas directrices:

### Requisitos Previos
- **Android Studio:** Ladybug / Jellyfish o superior.
- **Min SDK:** 24 (Android 7.0) | **Target SDK:** 34+
- **Kotlin:** 1.9+
- **Dependencias Clave:**
  ```kotlin
  // ML Kit OCR
  implementation("com.google.mlkit:text-recognition:16.0.1")
  // Material Design Components
  implementation("com.google.android.material:material:1.11.0")
  // Retrofit / OkHttp (Multipart Requests)
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  ```
