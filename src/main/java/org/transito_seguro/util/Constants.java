package org.transito_seguro.util;

/**
 * Clase de constantes del sistema de integración con e-pagos.
 *
 * Centraliza todas las constantes utilizadas en el sistema para facilitar
 * el mantenimiento y evitar valores hardcodeados dispersos en el código.
 *
 * Categorías de constantes:
 * - Configuración de e-pagos API
 * - Estados de infracciones y transacciones
 * - Códigos de respuesta
 * - Formatos y patrones
 * - Configuración de procesos
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
public final class Constants {

    // ==================== CONFIGURACIÓN API E-PAGOS ====================

    /**
     * Versión de la API de e-pagos utilizada.
     */
    public static final String EPAGOS_API_VERSION = "2.1";

    /**
     * URL base del endpoint SOAP de e-pagos.
     */
    public static final String EPAGOS_WSDL_BASE_URL = "https://api.epagos.com.ar/wsdl/2.1/index.php";

    /**
     * Namespace del servicio SOAP de e-pagos.
     */
    public static final String EPAGOS_NAMESPACE = "https://api.epagos.net/";

    /**
     * Encoding style SOAP.
     */
    public static final String SOAP_ENCODING_STYLE = "http://schemas.xmlsoap.org/soap/encoding/";

    /**
     * Timeout para conexiones SOAP en milisegundos (30 segundos).
     */
    public static final int EPAGOS_CONNECTION_TIMEOUT_MS = 30000;

    /**
     * Timeout para lectura de respuestas SOAP en milisegundos (60 segundos).
     */
    public static final int EPAGOS_READ_TIMEOUT_MS = 60000;

    /**
     * Número máximo de reintentos para conexiones fallidas.
     */
    public static final int EPAGOS_MAX_RETRY_ATTEMPTS = 3;

    /**
     * Tiempo de espera entre reintentos en milisegundos (5 segundos).
     */
    public static final int EPAGOS_RETRY_DELAY_MS = 5000;

    // ==================== MÉTODOS API E-PAGOS ====================

    /**
     * Método SOAP para obtener token de autenticación.
     */
    public static final String METODO_OBTENER_TOKEN = "obtener_token";

    /**
     * Método SOAP para obtener rendiciones.
     */
    public static final String METODO_OBTENER_RENDICIONES = "obtener_rendiciones";

    /**
     * Método SOAP para obtener contracargos.
     */
    public static final String METODO_OBTENER_CONTRACARGOS = "obtener_contracargos";

    /**
     * Método SOAP para obtener pagos.
     */
    public static final String METODO_OBTENER_PAGOS = "obtener_pagos";

    /**
     * Método SOAP para obtener pagos adicionales.
     */
    public static final String METODO_OBTENER_PAGOS_ADICIONALES = "obtener_pagos_adicionales";

    // ==================== ESTADOS DE INFRACCIONES ====================

    /**
     * Infracción pendiente de pago.
     */
    public static final String ESTADO_INFRACCION_PENDIENTE = "PENDIENTE";

    /**
     * Infracción pagada.
     */
    public static final String ESTADO_INFRACCION_PAGADA = "PAGADA";

    /**
     * Infracción con pago parcial.
     */
    public static final String ESTADO_INFRACCION_PAGO_PARCIAL = "PAGO_PARCIAL";

    /**
     * Infracción rendida.
     */
    public static final String ESTADO_INFRACCION_RENDIDA = "RENDIDA";

    /**
     * Infracción con contracargo.
     */
    public static final String ESTADO_INFRACCION_CONTRACARGO = "CONTRACARGO";

    /**
     * Infracción anulada.
     */
    public static final String ESTADO_INFRACCION_ANULADA = "ANULADA";

    /**
     * Infracción en proceso judicial.
     */
    public static final String ESTADO_INFRACCION_PROCESO_JUDICIAL = "PROCESO_JUDICIAL";

    // ==================== ESTADOS DE RENDICIONES ====================

    /**
     * Rendición generada y pendiente de transferencia.
     */
    public static final String ESTADO_RENDICION_GENERADA = "GENERADA";

    /**
     * Rendición transferida al organismo.
     */
    public static final String ESTADO_RENDICION_TRANSFERIDA = "TRANSFERIDA";

    /**
     * Rendición acreditada en cuenta.
     */
    public static final String ESTADO_RENDICION_ACREDITADA = "ACREDITADA";

    /**
     * Rendición observada por inconsistencias.
     */
    public static final String ESTADO_RENDICION_OBSERVADA = "OBSERVADA";

    /**
     * Rendición cancelada.
     */
    public static final String ESTADO_RENDICION_CANCELADA = "CANCELADA";

    // ==================== ESTADOS DE CONTRACARGOS ====================

    /**
     * Contracargo recibido, pendiente de análisis.
     */
    public static final String ESTADO_CONTRACARGO_RECIBIDO = "RECIBIDO";

    /**
     * Contracargo en análisis por equipo antifraude.
     */
    public static final String ESTADO_CONTRACARGO_EN_ANALISIS = "EN_ANALISIS";

    /**
     * Contracargo rechazado, pago se mantiene.
     */
    public static final String ESTADO_CONTRACARGO_RECHAZADO = "RECHAZADO";

    /**
     * Contracargo aceptado, se devuelve el monto.
     */
    public static final String ESTADO_CONTRACARGO_ACEPTADO = "ACEPTADO";

    /**
     * Contracargo en proceso de devolución.
     */
    public static final String ESTADO_CONTRACARGO_DEVOLUCION = "DEVOLUCION";

    /**
     * Contracargo finalizado.
     */
    public static final String ESTADO_CONTRACARGO_FINALIZADO = "FINALIZADO";

    // ==================== CÓDIGOS DE RESPUESTA E-PAGOS ====================

    /**
     * Código de respuesta exitosa.
     */
    public static final String CODIGO_RESPUESTA_EXITO = "0";

    /**
     * Código de error de autenticación.
     */
    public static final String CODIGO_ERROR_AUTENTICACION = "1";

    /**
     * Código de error de parámetros inválidos.
     */
    public static final String CODIGO_ERROR_PARAMETROS = "2";

    /**
     * Código de error de servicio no disponible.
     */
    public static final String CODIGO_ERROR_SERVICIO_NO_DISPONIBLE = "3";

    /**
     * Código de error de timeout.
     */
    public static final String CODIGO_ERROR_TIMEOUT = "4";

    /**
     * Código de error general del sistema.
     */
    public static final String CODIGO_ERROR_SISTEMA = "99";

    // ==================== FORMATOS DE FECHA ====================

    /**
     * Formato de fecha estándar ISO: yyyy-MM-dd.
     */
    public static final String FORMATO_FECHA_ISO = "yyyy-MM-dd";

    /**
     * Formato de fecha y hora ISO: yyyy-MM-dd'T'HH:mm:ss.
     */
    public static final String FORMATO_FECHA_HORA_ISO = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * Formato de fecha utilizado por e-pagos: dd/MM/yyyy.
     */
    public static final String FORMATO_FECHA_EPAGOS = "dd/MM/yyyy";

    /**
     * Formato de fecha y hora utilizado por e-pagos: dd/MM/yyyy HH:mm:ss.
     */
    public static final String FORMATO_FECHA_HORA_EPAGOS = "dd/MM/yyyy HH:mm:ss";

    /**
     * Formato de timestamp para archivos: yyyyMMdd_HHmmss.
     */
    public static final String FORMATO_TIMESTAMP_ARCHIVO = "yyyyMMdd_HHmmss";

    // ==================== CONFIGURACIÓN DE PROCESOS ====================

    /**
     * Días hacia atrás para búsqueda retrospectiva por defecto (1 semana).
     */
    public static final int DIAS_BUSQUEDA_RETROSPECTIVA_DEFAULT = 7;

    /**
     * Días hacia atrás máximos permitidos para búsqueda (90 días).
     */
    public static final int DIAS_BUSQUEDA_RETROSPECTIVA_MAX = 90;

    /**
     * Tamaño del lote para procesamiento batch de infracciones.
     */
    public static final int BATCH_SIZE_INFRACCIONES = 100;

    /**
     * Tamaño del pool de threads para procesamiento concurrente.
     */
    public static final int THREAD_POOL_SIZE = 5;

    /**
     * Timeout para tareas concurrentes en segundos.
     */
    public static final int CONCURRENT_TASK_TIMEOUT_SECONDS = 30;

    // ==================== CÓDIGOS DE PROVINCIAS ====================

    /**
     * Código de provincia: Buenos Aires.
     */
    public static final String PROVINCIA_BUENOS_AIRES = "BA";

    /**
     * Código de provincia: Ciudad Autónoma de Buenos Aires.
     */
    public static final String PROVINCIA_CABA = "CF";

    /**
     * Código de provincia: Córdoba.
     */
    public static final String PROVINCIA_CORDOBA = "CBA";

    /**
     * Código de provincia: Santa Fe.
     */
    public static final String PROVINCIA_SANTA_FE = "SF";

    /**
     * Código de provincia: Mendoza.
     */
    public static final String PROVINCIA_MENDOZA = "MDZ";

    /**
     * Código de provincia: Tucumán.
     */
    public static final String PROVINCIA_TUCUMAN = "TUC";

    /**
     * Código de provincia: Entre Ríos.
     */
    public static final String PROVINCIA_ENTRE_RIOS = "ER";

    /**
     * Código de provincia: Salta.
     */
    public static final String PROVINCIA_SALTA = "SAL";

    // ==================== TIPOS DE TRANSACCIÓN ====================

    /**
     * Tipo de transacción: Pago.
     */
    public static final String TIPO_TRANSACCION_PAGO = "PAGO";

    /**
     * Tipo de transacción: Rendición.
     */
    public static final String TIPO_TRANSACCION_RENDICION = "RENDICION";

    /**
     * Tipo de transacción: Contracargo.
     */
    public static final String TIPO_TRANSACCION_CONTRACARGO = "CONTRACARGO";

    /**
     * Tipo de transacción: Ajuste.
     */
    public static final String TIPO_TRANSACCION_AJUSTE = "AJUSTE";

    /**
     * Tipo de transacción: Devolución.
     */
    public static final String TIPO_TRANSACCION_DEVOLUCION = "DEVOLUCION";



    // ==================== MENSAJES DE ERROR ====================

    /**
     * Mensaje de error: Conexión fallida con e-pagos.
     */
    public static final String ERROR_CONEXION_EPAGOS =
            "Error de conexión con el servicio de e-pagos";

    /**
     * Mensaje de error: Timeout en consulta.
     */
    public static final String ERROR_TIMEOUT_CONSULTA =
            "Timeout al consultar servicio de e-pagos";

    /**
     * Mensaje de error: Credenciales inválidas.
     */
    public static final String ERROR_CREDENCIALES_INVALIDAS =
            "Credenciales de autenticación inválidas";

    /**
     * Mensaje de error: Parámetros inválidos.
     */
    public static final String ERROR_PARAMETROS_INVALIDOS =
            "Los parámetros proporcionados son inválidos";

    /**
     * Mensaje de error: Infracción no encontrada.
     */
    public static final String ERROR_INFRACCION_NO_ENCONTRADA =
            "No se encontró la infracción especificada";

    /**
     * Mensaje de error: Error al procesar respuesta.
     */
    public static final String ERROR_PROCESAMIENTO_RESPUESTA =
            "Error al procesar la respuesta del servicio";

    // ==================== EXPRESIONES REGULARES ====================

    /**
     * Patrón regex para validar número de infracción (formato: PROV-NNNNNNNN).
     */
    public static final String REGEX_NUMERO_INFRACCION = "^[A-Z]{2,3}-\\d{8,12}$";

    /**
     * Patrón regex para validar código de provincia (2-3 letras mayúsculas).
     */
    public static final String REGEX_CODIGO_PROVINCIA = "^[A-Z]{2,3}$";

    /**
     * Patrón regex para validar patente argentina (formato: AAA111 o AA111AA).
     */
    public static final String REGEX_PATENTE_ARGENTINA =
            "^([A-Z]{3}\\d{3}|[A-Z]{2}\\d{3}[A-Z]{2})$";

    // ==================== CONFIGURACIÓN DE SCHEDULER ====================

    /**
     * Cron expression para sincronización diaria: todos los días a las 02:00 AM.
     */
    public static final String CRON_SINCRONIZACION_DIARIA = "0 0 2 * * ?";

    /**
     * Cron expression para búsqueda cada 6 horas: a las 00:00, 06:00, 12:00, 18:00.
     */
    public static final String CRON_BUSQUEDA_CADA_6_HORAS = "0 0 */6 * * ?";

    /**
     * Cron expression para auditoría semanal: todos los lunes a las 03:00 AM.
     */
    public static final String CRON_AUDITORIA_SEMANAL = "0 0 3 ? * MON";

    // ==================== LÍMITES Y VALIDACIONES ====================

    /**
     * Longitud máxima para número de infracción.
     */
    public static final int MAX_LENGTH_NUMERO_INFRACCION = 20;

    /**
     * Longitud máxima para observaciones.
     */
    public static final int MAX_LENGTH_OBSERVACIONES = 500;

    /**
     * Monto mínimo de infracción.
     */
    public static final double MONTO_MINIMO_INFRACCION = 100.00;

    /**
     * Monto máximo de infracción.
     */
    public static final double MONTO_MAXIMO_INFRACCION = 1000000.00;

    /**
     * Constructor privado para prevenir instanciación.
     * Esta clase solo contiene constantes estáticas.
     */
    private Constants() {
        throw new AssertionError("No se puede instanciar la clase Constants");
    }
}