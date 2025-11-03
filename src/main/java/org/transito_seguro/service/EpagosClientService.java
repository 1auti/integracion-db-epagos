package org.transito_seguro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.dto.rendiciones.request.RendicionesRequestDTO;
import org.transito_seguro.dto.rendiciones.response.RendicionesResponseDTO;
import org.transito_seguro.exception.EpagosConnectionException;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.util.FechaUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio cliente para comunicación con el Proxy Mapper de e-Pagos.
 *
 * Arquitectura:
 * - Cliente HTTP: Apache HttpClient para peticiones HTTP
 * - Serialización: Jackson para JSON ↔ Java Objects
 * - Reintentos: Patrón Retry con backoff exponencial para errores temporales
 *
 * Responsabilidades:
 * - Invocación del proxy mapper vía HTTP POST + JSON
 * - Manejo de reintentos y timeouts
 * - Parseo y validación de respuestas JSON
 * - Gestión centralizada de errores y logging detallado
 *
 * Métodos de API soportados:
 * - obtenerRendiciones: Consulta de rendiciones por rango de fechas
 */
@Service
@Slf4j
public class EpagosClientService {

    // ========================================================================
    // INYECCIÓN DE DEPENDENCIAS
    // ========================================================================

    /**
     * Cliente HTTP de Apache para realizar peticiones.
     * Configurado con timeouts y pool de conexiones.
     */
    @Autowired
    private CloseableHttpClient httpClient;

    /**
     * ObjectMapper de Jackson para serialización JSON.
     */
    private final ObjectMapper objectMapper;

    // ========================================================================
    // CONFIGURACIÓN DESDE APPLICATION.YML
    // ========================================================================

    /**
     * URL del servicio Proxy Mapper.
     * Ejemplo: http://proxy-mapper-service:8080/api/rendiciones
     *
     * NOTA: Ya no se conecta directamente a e-Pagos, sino al proxy mapper.
     */
    @Value("${epagos.proxy.url}")
    private String proxyMapperUrl;

    /**
     * Endpoint específico para obtener rendiciones.
     * Ejemplo: /obtenerRendicionFull
     */
    @Value("${epagos.proxy.endpoint.rendiciones:/obtenerRendicionFull}")
    private String rendicionesEndpoint;

    /**
     * Configuración de reintentos.
     */
    @Value("${epagos.proxy.max-retries:3}")
    private int maxRetries;

    @Value("${epagos.proxy.retry-delay:2000}")
    private int retryDelayMs;

    // ========================================================================
    // CONSTANTES
    // ========================================================================

    /**
     * Content-Type para peticiones HTTP.
     */
    private static final String CONTENT_TYPE = "application/json";

    /**
     * Encoding para peticiones HTTP.
     */
    private static final String CHARSET = "UTF-8";

    /**
     * Formato de fecha para e-Pagos (yyyy-MM-dd).
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Código de respuesta exitosa de e-Pagos.
     */
    private static final Integer CODIGO_EXITO = 5001;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Constructor con inicialización de ObjectMapper.
     * Configura Jackson para manejo correcto de fechas y campos null.
     */
    public EpagosClientService() {
        this.objectMapper = new ObjectMapper();
        // Configurar ObjectMapper para Java 8 Date/Time API
        this.objectMapper.findAndRegisterModules();
        log.info("EpagosClientService inicializado para Proxy Mapper con cliente HTTP + JSON");
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - RENDICIONES
    // ========================================================================

    /**
     * Obtiene las rendiciones desde el Proxy Mapper para un organismo y rango de fechas.
     *
     * Las rendiciones son reportes de pagos realizados que e-Pagos
     * transfiere periódicamente al organismo.
     *
     * Estructura de datos retornada:
     * - RendicionesResponseDTO con lista de rendiciones
     * - Cada rendición contiene:
     *   • Número y secuencia de rendición
     *   • Fechas (desde, hasta, depósito)
     *   • Montos (bruto, depositado, comisiones)
     *   • Estado (A=Abierta/Pendiente, D=Depositada)
     *   • Detalles de transacciones incluidas
     *   • Contracargos (si existen)
     *
     * Proceso:
     * 1. Valida parámetros de entrada
     * 2. Construye request JSON con credenciales y filtros
     * 3. Envía POST al proxy mapper
     * 4. Parsea respuesta JSON
     * 5. Valida código de respuesta (5001 = éxito)
     * 6. Retorna RendicionesResponseDTO completo
     *
     * Restricciones de e-Pagos:
     * - Rango máximo: 90 días
     * - Formato fechas: yyyy-MM-dd
     *
     * @param idOrganismo Identificador del organismo
     * @param idUsuario Identificador del usuario
     * @param password Password encriptado
     * @param hash Hash de seguridad
     * @param fechaDesde Fecha inicial del rango (inclusiva)
     * @param fechaHasta Fecha final del rango (inclusiva)
     * @param convenios String con convenios separados por coma (ej: "14704,24704,34704")
     * @return RendicionesResponseDTO con la respuesta completa del servicio
     * @throws EpagosException si hay error en la consulta
     * @throws IllegalArgumentException si parámetros inválidos
     */
    public RendicionesResponseDTO obtenerRendiciones(
            String idOrganismo,
            String idUsuario,
            String password,
            String hash,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String convenios) throws EpagosException {

        log.info("Consultando rendiciones para organismo {} desde {} hasta {}",
                idOrganismo, fechaDesde, fechaHasta);

        // Validar parámetros
        validarParametrosRendiciones(idOrganismo, idUsuario, password, hash, fechaDesde, fechaHasta, convenios);
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            // Construir request
            RendicionesRequestDTO request = construirRendicionesRequest(
                    idOrganismo, idUsuario, password, hash, fechaDesde, fechaHasta, convenios
            );

            log.debug("Request rendiciones: organismo={}, fechaDesde={}, fechaHasta={}, convenios={}",
                    idOrganismo, fechaDesde, fechaHasta, convenios);

            // Invocar Proxy Mapper con reintentos
            RendicionesResponseDTO response = ejecutarConReintentos(
                    () -> {
                        String json = objectMapper.writeValueAsString(request);
                        HttpResponse httpResponse = ejecutarPost(rendicionesEndpoint, json);
                        String responseBody = EntityUtils.toString(httpResponse.getEntity(), CHARSET);

                        log.debug("Response JSON (primeros 500 chars): {}",
                                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                        return objectMapper.readValue(responseBody, RendicionesResponseDTO.class);
                    }
            );

            // Validar respuesta
            validarRespuestaRendiciones(response, idOrganismo);

            // Extraer y loguear información de rendiciones
            int totalRendiciones = response.getRendiciones().size();
            log.info("✓ Rendiciones obtenidas para organismo {}: {} registros", idOrganismo, totalRendiciones);

            // Logging detallado de las rendiciones
            if (!response.getRendiciones().isEmpty()) {
                RendicionDTO primera = response.getRendiciones().get(0);
                log.debug("Ejemplo primera rendición: numero={}, estado={}, monto={}, cantidad={}",
                        primera.getNumero(), primera.getEstado(), primera.getMonto(), primera.getCantidad());
            }

            return response;

        } catch (Exception e) {
            log.error("Error al obtener rendiciones para organismo {}", idOrganismo, e);
            throw new EpagosException("Error al consultar rendiciones en Proxy Mapper: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene rendiciones (versión con java.util.Date).
     *
     * Versión sobrecargada para compatibilidad con código legacy.
     *
     * @param idOrganismo Identificador del organismo
     * @param idUsuario Identificador del usuario
     * @param password Password encriptado
     * @param hash Hash de seguridad
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @param convenios String con convenios separados por coma
     * @return RendicionesResponseDTO con la respuesta completa
     * @throws EpagosException si hay error en la consulta
     */
    public RendicionesResponseDTO obtenerRendiciones(
            String idOrganismo,
            String idUsuario,
            String password,
            String hash,
            Date fechaDesde,
            Date fechaHasta,
            String convenios) throws EpagosException {

        log.info("→ Consultando rendiciones para organismo: {}", idOrganismo);

        // Convertir Date a LocalDate usando utilidad
        LocalDate desde = FechaUtil.convertirALocalDate(fechaDesde);
        LocalDate hasta = FechaUtil.convertirALocalDate(fechaHasta);

        return obtenerRendiciones(idOrganismo, idUsuario, password, hash, desde, hasta, convenios);
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - CONSTRUCCIÓN DE REQUESTS
    // ========================================================================

    /**
     * Construye el DTO de request para obtener rendiciones.
     *
     * @param idOrganismo ID del organismo
     * @param idUsuario ID del usuario
     * @param password Password encriptado
     * @param hash Hash de seguridad
     * @param fechaDesde Fecha desde
     * @param fechaHasta Fecha hasta
     * @param convenios Convenios separados por coma
     * @return RendicionesRequestDTO configurado
     */
    private RendicionesRequestDTO construirRendicionesRequest(
            String idOrganismo,
            String idUsuario,
            String password,
            String hash,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String convenios) {

        RendicionesRequestDTO request = new RendicionesRequestDTO();
        request.setIdOrganismo(idOrganismo);
        request.setIdUsuario(idUsuario);
        request.setPassword(password);
        request.setHash(hash);
        request.setFechaDesde(fechaDesde.format(DATE_FORMATTER));
        request.setFechaHasta(fechaHasta.format(DATE_FORMATTER));
        request.setConvenios(convenios);

        return request;
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - VALIDACIONES
    // ========================================================================

    /**
     * Valida los parámetros de entrada para obtener rendiciones.
     *
     * @param idOrganismo ID del organismo
     * @param idUsuario ID del usuario
     * @param password Password
     * @param hash Hash
     * @param fechaDesde Fecha desde
     * @param fechaHasta Fecha hasta
     * @param convenios Convenios
     * @throws IllegalArgumentException si algún parámetro es inválido
     */
    private void validarParametrosRendiciones(
            String idOrganismo,
            String idUsuario,
            String password,
            String hash,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String convenios) {

        if (idOrganismo == null || idOrganismo.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del organismo no puede ser nulo o vacío");
        }

        if (idUsuario == null || idUsuario.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del usuario no puede ser nulo o vacío");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("El password no puede ser nulo o vacío");
        }

        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("El hash no puede ser nulo o vacío");
        }

        if (convenios == null || convenios.trim().isEmpty()) {
            throw new IllegalArgumentException("Los convenios no pueden ser nulos o vacíos");
        }
    }

    /**
     * Valida que el rango de fechas sea correcto.
     *
     * Validaciones realizadas:
     * - Fechas no nulas
     * - Fecha desde ≤ fecha hasta
     * - Fecha hasta ≤ hoy
     * - Rango ≤ 90 días (restricción de e-Pagos)
     *
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @throws IllegalArgumentException si el rango no es válido
     */
    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("Las fechas no pueden ser nulas");
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta");
        }

        if (fechaHasta.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha hasta no puede ser futura");
        }

        // Validar rango máximo de 90 días (restricción de e-Pagos)
        long diasDiferencia = FechaUtil.calcularDiferenciaEnDias(fechaDesde, fechaHasta);
        if (diasDiferencia > 90) {
            throw new IllegalArgumentException(
                    "El rango de fechas no puede superar los 90 días (restricción de e-Pagos)");
        }
    }

    /**
     * Valida la respuesta de rendiciones del Proxy Mapper.
     *
     * @param response Respuesta a validar
     * @param idOrganismo ID del organismo (para logging)
     * @throws EpagosException si la respuesta no es válida
     */
    private void validarRespuestaRendiciones(RendicionesResponseDTO response, String idOrganismo)
            throws EpagosException {

        if (response == null) {
            throw new EpagosException("Respuesta nula del Proxy Mapper para organismo " + idOrganismo);
        }

        // Verificar código de respuesta (5001 = éxito según documentación)
        if (!CODIGO_EXITO.equals(response.getIdRespuesta())) {
            log.warn("Código de respuesta no exitoso para organismo {}: {} - {}",
                    idOrganismo, response.getIdRespuesta(), response.getRespuesta());

            throw new EpagosException(
                    String.format("Error en e-Pagos para organismo %s: %s (código: %d)",
                            idOrganismo, response.getRespuesta(), response.getIdRespuesta())
            );
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - UTILIDADES HTTP
    // ========================================================================

    /**
     * Ejecuta una petición HTTP POST con JSON.
     *
     * @param endpoint Endpoint relativo (ej: "/obtenerRendicionFull")
     * @param jsonBody Body de la petición en formato JSON
     * @return HttpResponse de Apache HttpClient
     * @throws Exception si hay error en la petición
     */
    private HttpResponse ejecutarPost(String endpoint, String jsonBody) throws Exception {
        String url = proxyMapperUrl + endpoint;

        log.debug("POST {} - Body: {}", url,
                jsonBody.length() > 200 ? jsonBody.substring(0, 200) + "..." : jsonBody);

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", CONTENT_TYPE);
        post.setHeader("Accept", CONTENT_TYPE);
        post.setEntity(new StringEntity(jsonBody, CHARSET));

        HttpResponse response = httpClient.execute(post);

        int statusCode = response.getStatusLine().getStatusCode();
        log.debug("Response status: {}", statusCode);

        if (statusCode != 200) {
            String errorBody = EntityUtils.toString(response.getEntity(), CHARSET);
            log.error("Error HTTP {}: {}", statusCode, errorBody);
            throw new EpagosConnectionException("Error HTTP " + statusCode + ": " + errorBody, null);
        }

        return response;
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - REINTENTOS
    // ========================================================================

    /**
     * Ejecuta una operación con reintentos automáticos.
     *
     * Implementa el patrón Retry con backoff exponencial:
     * 1. Intenta ejecutar la operación
     * 2. Si falla con error temporal (timeout, conexión), espera y reintenta
     * 3. Aumenta el tiempo de espera exponencialmente en cada reintento
     * 4. Después de maxRetries intentos, lanza la excepción
     *
     * Errores que provocan reintentos:
     * - SocketTimeoutException
     * - ConnectException
     * - Mensajes con "timeout" o "connection"
     *
     * @param operacion Operación a ejecutar (lambda o método)
     * @param <T> Tipo de retorno
     * @return Resultado de la operación
     * @throws Exception si falla después de todos los reintentos
     */
    private <T> T ejecutarConReintentos(OperacionApi<T> operacion) throws Exception {
        int intento = 0;
        Exception ultimaExcepcion = null;

        while (intento < maxRetries) {
            try {
                intento++;
                log.debug("Intento {}/{}", intento, maxRetries);

                return operacion.ejecutar();

            } catch (Exception e) {
                ultimaExcepcion = e;

                // Verificar si es un error que amerita reintento
                if (esErrorTemporal(e) && intento < maxRetries) {
                    long delay = calcularDelayReintento(intento);
                    log.warn("Error temporal en intento {}, reintentando en {}ms: {}",
                            intento, delay, e.getMessage());

                    Thread.sleep(delay);
                } else {
                    // Error no recuperable o último intento
                    throw e;
                }
            }
        }

        // Si llegamos aquí, se agotaron los reintentos
        log.error("Se agotaron los {} reintentos", maxRetries);
        throw ultimaExcepcion;
    }

    /**
     * Verifica si un error es temporal y amerita reintento.
     *
     * Errores temporales típicos:
     * - Timeouts de red
     * - Errores de conexión
     * - Socket cerrado inesperadamente
     *
     * @param e Excepción a evaluar
     * @return true si es error temporal y se debe reintentar
     */
    private boolean esErrorTemporal(Exception e) {
        String mensaje = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        return mensaje.contains("timeout") ||
                mensaje.contains("connection") ||
                mensaje.contains("socket") ||
                e instanceof java.net.SocketTimeoutException ||
                e instanceof java.net.ConnectException;
    }

    /**
     * Calcula el delay para el siguiente reintento usando backoff exponencial.
     *
     * Formula: delay * 2^(intento-1)
     * Ejemplo con delay=2000ms:
     * - Intento 1: 2000ms (2s)
     * - Intento 2: 4000ms (4s)
     * - Intento 3: 8000ms (8s)
     *
     * @param intento Número de intento actual (1-based)
     * @return Delay en milisegundos
     */
    private long calcularDelayReintento(int intento) {
        return retryDelayMs * (long) Math.pow(2, intento - 1);
    }

    // ========================================================================
    // INTERFAZ FUNCIONAL PARA OPERACIONES API
    // ========================================================================

    /**
     * Interfaz funcional para operaciones de API con reintentos.
     * Permite usar lambdas o referencias a métodos.
     *
     * @param <T> Tipo de retorno de la operación
     */
    @FunctionalInterface
    private interface OperacionApi<T> {
        T ejecutar() throws Exception;
    }
}