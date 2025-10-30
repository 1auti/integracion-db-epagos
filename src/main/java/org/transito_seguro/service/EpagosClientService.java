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
import org.transito_seguro.dto.CredencialesDTO;
import org.transito_seguro.dto.contracargos.ContracargoDTO;
import org.transito_seguro.dto.contracargos.FiltroContracargoDTO;
import org.transito_seguro.dto.contracargos.request.ContracargosRequestDTO;
import org.transito_seguro.dto.contracargos.response.ContracargosResponseDTO;
import org.transito_seguro.dto.rendiciones.FiltroRendicionDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.dto.rendiciones.request.RendicionesRequestDTO;
import org.transito_seguro.dto.rendiciones.response.RendicionesResponseDTO;
import org.transito_seguro.dto.token.TokenRequestDTO;
import org.transito_seguro.dto.token.TokenResponseDTO;
import org.transito_seguro.exception.EpagosAuthException;
import org.transito_seguro.exception.EpagosConnectionException;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.util.FechaUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio cliente para comunicación con la API REST de e-Pagos.
 *
 * Este servicio encapsula toda la comunicación con el sistema e-Pagos,
 * realizando peticiones HTTP POST con payload JSON y recibiendo respuestas JSON.
 *
 * Arquitectura:
 * - Cliente HTTP: Apache HttpClient para peticiones HTTP
 * - Serialización: Jackson para JSON ↔ Java Objects
 * - Gestión de Token: Caché inteligente con renovación automática (24 horas)
 * - Reintentos: Patrón Retry con backoff exponencial para errores temporales
 *
 * Responsabilidades:
 * - Autenticación y gestión de tokens (caché de 24 horas)
 * - Invocación de métodos de la API e-Pagos vía HTTP POST + JSON
 * - Manejo de reintentos y timeouts
 * - Parseo y validación de respuestas JSON
 * - Gestión centralizada de errores y logging detallado
 *
 * Métodos de API soportados:
 * - obtener_token: Autenticación y obtención de token
 * - obtener_rendiciones: Consulta de rendiciones por rango de fechas
 * - obtener_contracargos: Consulta de contracargos por rango de fechas
 *
 * Formato de comunicación:
 * - REQUEST: HTTP POST con Content-Type: application/json
 * - RESPONSE: JSON con estructura según documentación e-Pagos v2.1
 */
@Service
@Slf4j
public class EpagosClientService {

    // ========================================================================
    // INYECCIÓN DE DEPENDENCIAS
    // ========================================================================

    /**
     * Cliente HTTP de Apache para realizar peticiones
     * Configurado en SoapClientConfig.java con timeouts y pool de conexiones
     */
    @Autowired
    private CloseableHttpClient httpClient;

    /**
     * ObjectMapper de Jackson para serialización JSON
     */
    private final ObjectMapper objectMapper;

    // ========================================================================
    // CONFIGURACIÓN DESDE APPLICATION.YML
    // ========================================================================

    /**
     * URL base de la API de e-Pagos
     * Ejemplo: https://www.epagos.com/svc/wsespeciales.asmx
     */
    @Value("${epagos.soap.url}")
    private String apiUrl;

    /**
     * Credenciales de autenticación proporcionadas por e-Pagos
     */
    @Value("${epagos.soap.usuario}")
    private String usuario;

    @Value("${epagos.soap.clave}")
    private String clave;

    /**
     * Código de organismo asignado por e-Pagos
     */
    @Value("${epagos.soap.id-organismo:1}")
    private Integer idOrganismo;

    /**
     * Configuración de reintentos
     */
    @Value("${epagos.soap.max-retries:3}")
    private int maxRetries;

    @Value("${epagos.soap.retry-delay:2000}")
    private int retryDelayMs;

    // ========================================================================
    // CACHÉ DE TOKEN
    // ========================================================================

    /**
     * Token de autenticación actual (válido por 24 horas según e-Pagos)
     */
    private String tokenActual;

    /**
     * Fecha de expiración del token actual
     */
    private LocalDateTime tokenExpiracion;

    // ========================================================================
    // CONSTANTES
    // ========================================================================

    /**
     * Versión del protocolo de e-Pagos (según documentación)
     */
    private static final String VERSION_PROTOCOLO = "2.1";

    /**
     * Duración de validez del token en horas
     */
    private static final int TOKEN_VALIDEZ_HORAS = 24;

    /**
     * Margen de seguridad antes de expiración (renovar 1 hora antes)
     */
    private static final int TOKEN_MARGEN_RENOVACION_HORAS = 1;

    /**
     * Content-Type para peticiones HTTP
     */
    private static final String CONTENT_TYPE = "application/json";

    /**
     * Encoding para peticiones HTTP
     */
    private static final String CHARSET = "UTF-8";

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
        log.info("EpagosClientService inicializado con cliente HTTP + JSON");
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - AUTENTICACIÓN
    // ========================================================================

    /**
     * Obtiene un token de autenticación válido.
     *
     * Estrategia de caché:
     * 1. Si hay token en caché y está vigente → retorna inmediatamente
     * 2. Si token próximo a expirar (< 1 hora) → renueva automáticamente
     * 3. Si no hay token o expiró → solicita nuevo a e-Pagos
     *
     * El token es válido por 24 horas según la API de e-Pagos.
     * Se implementa margen de seguridad de 1 hora para evitar expiración durante uso.
     *
     * @return Token de autenticación válido (String)
     * @throws EpagosAuthException si falla la autenticación con e-Pagos
     * @throws EpagosConnectionException si hay error de conexión
     */
    public String obtenerTokenValido() throws EpagosException {
        log.debug("Verificando validez del token actual");

        // Verificar si hay token en caché y está vigente
        if (tokenActual != null && tokenExpiracion != null) {
            LocalDateTime ahora = LocalDateTime.now();

            // Si el token sigue vigente (con margen de seguridad)
            if (ahora.plusHours(TOKEN_MARGEN_RENOVACION_HORAS).isBefore(tokenExpiracion)) {
                log.debug("Token en caché válido hasta: {}", tokenExpiracion);
                return tokenActual;
            }

            log.info("Token próximo a expirar ({}), renovando...", tokenExpiracion);
        }

        // Solicitar nuevo token
        return renovarToken();
    }

    /**
     * Renueva el token solicitando uno nuevo a e-Pagos.
     *
     * Proceso:
     * 1. Construye request JSON con credenciales
     * 2. Envía POST a /obtener_token
     * 3. Parsea respuesta JSON
     * 4. Actualiza caché interno
     * 5. Retorna token
     *
     * @return Nuevo token de autenticación
     * @throws EpagosAuthException si credenciales inválidas
     * @throws EpagosConnectionException si error de conexión
     */
    private String renovarToken() throws EpagosException {
        log.info("Solicitando nuevo token a e-Pagos");

        try {
            // Construir request
            TokenRequestDTO request = new TokenRequestDTO();
            request.setVersion(VERSION_PROTOCOLO);
            request.setUsuario(usuario);
            request.setClave(clave);

            log.debug("Request token: usuario={}, version={}", usuario, VERSION_PROTOCOLO);

            // Invocar API con reintentos
            TokenResponseDTO response = ejecutarConReintentos(
                    () -> {
                        String json = objectMapper.writeValueAsString(request);
                        HttpResponse httpResponse = ejecutarPost("/obtener_token", json);
                        String responseBody = EntityUtils.toString(httpResponse.getEntity(), CHARSET);
                        return objectMapper.readValue(responseBody, TokenResponseDTO.class);
                    }
            );

            // Validar respuesta
            if (response == null || !response.isExitosa()) {
                String mensaje = response != null ? response.getRespuesta() : "Respuesta nula";
                log.error("Error al obtener token: {} (código: {})",
                        mensaje, response != null ? response.getIdResp() : "N/A");
                throw new EpagosAuthException("Fallo en autenticación: " + mensaje);
            }

            // Actualizar caché
            tokenActual = response.getToken();
            tokenExpiracion = LocalDateTime.now().plusHours(TOKEN_VALIDEZ_HORAS);

            log.info("✓ Token obtenido exitosamente, válido hasta: {}", tokenExpiracion);
            return tokenActual;

        } catch (EpagosAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al renovar token", e);
            throw new EpagosConnectionException("Error al conectar con e-Pagos para obtener token", e);
        }
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - RENDICIONES
    // ========================================================================

    /**
     * Obtiene las rendiciones desde e-Pagos para un rango de fechas.
     *
     * Las rendiciones son reportes de pagos realizados que e-Pagos
     * transfiere periódicamente al organismo.
     *
     * Estructura de datos retornada:
     * - Lista de RendicionDTO (puede ser vacía)
     * - Cada rendición contiene:
     *   • Número y secuencia de rendición
     *   • Fechas (desde, hasta, depósito)
     *   • Montos (bruto, depositado, comisiones)
     *   • Estado (Pendiente, Depositada, Cancelada)
     *   • Detalles de transacciones incluidas
     *
     * Proceso:
     * 1. Valida parámetros de entrada
     * 2. Obtiene token válido (renovación automática si necesario)
     * 3. Construye request JSON con filtros
     * 4. Envía POST a /obtener_rendiciones
     * 5. Parsea respuesta JSON
     * 6. Valida código de respuesta (05001 = éxito)
     * 7. Retorna lista de rendiciones
     *
     * Restricciones de e-Pagos:
     * - Rango máximo: 90 días
     * - Formato fechas: yyyy-MM-dd
     * - Solo rendiciones depositadas
     *
     * @param fechaDesde Fecha inicial del rango (inclusiva)
     * @param fechaHasta Fecha final del rango (inclusiva)
     * @return Lista de rendiciones encontradas (nunca null, puede estar vacía)
     * @throws EpagosException si hay error en la consulta
     * @throws IllegalArgumentException si parámetros inválidos
     */
    public List<RendicionDTO> obtenerRendiciones(
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        log.info("Consultando rendiciones desde {} hasta {}", fechaDesde, fechaHasta);

        // Validar parámetros
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            // Obtener token válido (con renovación automática si necesario)
            String token = obtenerTokenValido();

            // Construir credenciales
            CredencialesDTO credenciales = new CredencialesDTO();
            credenciales.setIdOrganismo(idOrganismo);
            credenciales.setToken(token);

            // Construir filtros
            FiltroRendicionDTO filtro = new FiltroRendicionDTO();
            filtro.setFechaDesde(fechaDesde);
            filtro.setFechaHasta(fechaHasta);

            // Construir request
            RendicionesRequestDTO request = new RendicionesRequestDTO();
            request.setVersion(VERSION_PROTOCOLO);
            request.setCredenciales(credenciales);
            request.setRendicion(filtro);

            log.debug("Request rendiciones: fechaDesde={}, fechaHasta={}", fechaDesde, fechaHasta);

            // Invocar API con reintentos
            RendicionesResponseDTO response = ejecutarConReintentos(
                    () -> {
                        String json = objectMapper.writeValueAsString(request);
                        HttpResponse httpResponse = ejecutarPost("/obtener_rendiciones", json);
                        String responseBody = EntityUtils.toString(httpResponse.getEntity(), CHARSET);

                        log.debug("Response JSON (primeros 500 chars): {}",
                                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                        return objectMapper.readValue(responseBody, RendicionesResponseDTO.class);
                    }
            );

            // Validar respuesta
            if (response == null) {
                throw new EpagosException("Respuesta nula de e-Pagos");
            }

            // Verificar código de respuesta (05001 = éxito según documentación)
            if (!"05001".equals(response.getIdResp())) {
                log.warn("Código de respuesta no exitoso: {} - {}",
                        response.getIdResp(), response.getRespuesta());

                // Manejar errores específicos según documentación
                switch (response.getIdResp()) {
                    case "05002":
                        // Token inválido, forzar renovación y reintentar
                        log.warn("Token inválido, invalidando caché y reintentando");
                        tokenActual = null;
                        throw new EpagosAuthException("Token inválido, renovando...");

                    case "05004":
                        throw new EpagosException("Rango de fechas supera el límite permitido (90 días)");

                    case "05005":
                        throw new EpagosException("Error al validar parámetro: " + response.getRespuesta());

                    default:
                        throw new EpagosException("Error en e-Pagos: " + response.getRespuesta());
                }
            }

            // Extraer lista de rendiciones (nunca retornar null)
            List<RendicionDTO> rendiciones = response.getRendiciones();
            if (rendiciones == null) {
                rendiciones = new ArrayList<>();
            }

            log.info("✓ Rendiciones obtenidas: {} registros", rendiciones.size());

            // Logging detallado de las rendiciones
            if (!rendiciones.isEmpty()) {
                RendicionDTO primera = rendiciones.get(0);
                log.debug("Ejemplo primera rendición: numero={}, estado={}, monto={}, cantidad={}",
                        primera.getNumero(), primera.getEstado(), primera.getMonto(), primera.getCantidad());
            }

            return rendiciones;

        } catch (EpagosAuthException e) {
            // Si el token expiró, reintentar UNA VEZ con token renovado
            log.warn("Token expirado, reintentando con token renovado");
            tokenActual = null;
            tokenExpiracion = null;
            return obtenerRendiciones(fechaDesde, fechaHasta);

        } catch (Exception e) {
            log.error("Error al obtener rendiciones", e);
            throw new EpagosException("Error al consultar rendiciones en e-Pagos: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene rendiciones para una provincia específica.
     *
     * Versión sobrecargada que acepta código de provincia y java.util.Date
     * para compatibilidad con código legacy y logging específico por provincia.
     *
     * @param codigoProvincia Código de la provincia (ej: "PBA", "MDA", "CHACO")
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @return Lista de rendiciones encontradas
     * @throws EpagosException si hay error en la consulta
     */
    public List<RendicionDTO> obtenerRendiciones(
            String codigoProvincia,
            Date fechaDesde,
            Date fechaHasta) throws EpagosException {

        log.info("→ Consultando rendiciones para provincia: {}", codigoProvincia);

        // Convertir Date a LocalDate usando utilidad
        LocalDate desde = FechaUtil.convertirALocalDate(fechaDesde);
        LocalDate hasta = FechaUtil.convertirALocalDate(fechaHasta);

        return obtenerRendiciones(desde, hasta);
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - CONTRACARGOS
    // ========================================================================

    /**
     * Obtiene los contracargos desde e-Pagos para un rango de fechas.
     *
     * Los contracargos son reclamos de usuarios que no reconocen un pago.
     * El sistema debe detectarlos para iniciar procesos de revisión o devolución.
     *
     * Estados de contracargos según e-Pagos:
     * - Pendiente: Esperando respuesta del organismo
     * - Respondido: Ya fue respondido por el organismo
     * - Aceptado: Aceptado por el organismo o vencido sin respuesta
     * - Resuelto: Solucionado ante el medio de pago
     *
     * Estructura de datos retornada:
     * - Lista de ContracargoDTO (puede ser vacía)
     * - Cada contracargo contiene:
     *   • Número y estado
     *   • Medio de pago y transacción afectada
     *   • Montos y fechas relevantes
     *   • Comprobantes (si existen)
     *
     * Proceso:
     * 1. Valida parámetros de entrada
     * 2. Obtiene token válido
     * 3. Construye request JSON con filtros
     * 4. Envía POST a /obtener_contracargos
     * 5. Parsea respuesta JSON
     * 6. Valida código de respuesta (06001 = éxito)
     * 7. Analiza contracargos urgentes
     * 8. Retorna lista de contracargos
     *
     * @param fechaDesde Fecha inicial del rango (inclusiva)
     * @param fechaHasta Fecha final del rango (inclusiva)
     * @return Lista de contracargos encontrados (nunca null, puede estar vacía)
     * @throws EpagosException si hay error en la consulta
     * @throws IllegalArgumentException si parámetros inválidos
     */
    public List<ContracargoDTO> obtenerContracargos(
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        log.info("Consultando contracargos desde {} hasta {}", fechaDesde, fechaHasta);

        // Validar parámetros
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            // Obtener token válido
            String token = obtenerTokenValido();

            // Construir credenciales
            CredencialesDTO credenciales = new CredencialesDTO();
            credenciales.setIdOrganismo(idOrganismo);
            credenciales.setToken(token);

            // Construir filtros
            FiltroContracargoDTO filtro = new FiltroContracargoDTO();
            filtro.setFechaDesde(fechaDesde);
            filtro.setFechaHasta(fechaHasta);

            // Construir request
            ContracargosRequestDTO request = new ContracargosRequestDTO();
            request.setVersion(VERSION_PROTOCOLO);
            request.setCredenciales(credenciales);
            request.setDatosContracargos(filtro);

            log.debug("Request contracargos: fechaDesde={}, fechaHasta={}", fechaDesde, fechaHasta);

            // Invocar API con reintentos
            ContracargosResponseDTO response = ejecutarConReintentos(
                    () -> {
                        String json = objectMapper.writeValueAsString(request);
                        HttpResponse httpResponse = ejecutarPost("/obtener_contracargos", json);
                        String responseBody = EntityUtils.toString(httpResponse.getEntity(), CHARSET);

                        log.debug("Response JSON (primeros 500 chars): {}",
                                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                        return objectMapper.readValue(responseBody, ContracargosResponseDTO.class);
                    }
            );

            // Validar respuesta
            if (response == null) {
                throw new EpagosException("Respuesta nula de e-Pagos");
            }

            // Verificar código de respuesta (06001 = éxito según documentación)
            if (!"06001".equals(response.getIdResp())) {
                log.warn("Código de respuesta no exitoso: {} - {}",
                        response.getIdResp(), response.getRespuesta());

                // Manejar errores específicos según documentación
                switch (response.getIdResp()) {
                    case "06002":
                        // Token inválido, forzar renovación
                        log.warn("Token inválido, invalidando caché y reintentando");
                        tokenActual = null;
                        throw new EpagosAuthException("Token inválido, renovando...");

                    case "06004":
                        throw new EpagosException("El rango de fechas no es correcto");

                    case "06005":
                        throw new EpagosException("Error al validar parámetro: " + response.getRespuesta());

                    case "06006":
                        throw new EpagosException("Versión inválida del protocolo");

                    default:
                        throw new EpagosException("Error en e-Pagos: " + response.getRespuesta());
                }
            }

            // Extraer lista de contracargos (nunca retornar null)
            List<ContracargoDTO> contracargos = response.getContracargos();
            if (contracargos == null) {
                contracargos = new ArrayList<>();
            }

            log.info("✓ Contracargos obtenidos: {} registros", contracargos.size());

            // Análisis de contracargos urgentes (requieren atención < 2 días)
            long contracargosUrgentes = contracargos.stream()
                    .filter(ContracargoDTO::requiereAtencionUrgente)
                    .count();

            if (contracargosUrgentes > 0) {
                log.warn("⚠️ ALERTA: {} contracargos requieren atención URGENTE (< 2 días para vencer)",
                        contracargosUrgentes);
            }

            // Logging detallado por estado
            if (!contracargos.isEmpty()) {
                Map<String, Long> porEstado = contracargos.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                ContracargoDTO::getEstado,
                                java.util.stream.Collectors.counting()
                        ));
                log.debug("Contracargos por estado: {}", porEstado);
            }

            return contracargos;

        } catch (EpagosAuthException e) {
            // Si el token expiró, reintentar UNA VEZ con token renovado
            log.warn("Token expirado, reintentando con token renovado");
            tokenActual = null;
            tokenExpiracion = null;
            return obtenerContracargos(fechaDesde, fechaHasta);

        } catch (Exception e) {
            log.error("Error al obtener contracargos", e);
            throw new EpagosException("Error al consultar contracargos en e-Pagos: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene contracargos para una provincia específica.
     *
     * Versión sobrecargada para compatibilidad con java.util.Date y
     * logging específico por provincia.
     *
     * @param codigoProvincia Código de la provincia
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @return Lista de contracargos encontrados
     * @throws EpagosException si hay error en la consulta
     */
    public List<ContracargoDTO> obtenerContracargos(
            String codigoProvincia,
            Date fechaDesde,
            Date fechaHasta) throws EpagosException {

        log.info("→ Consultando contracargos para provincia: {}", codigoProvincia);

        // Convertir Date a LocalDate
        LocalDate desde = FechaUtil.convertirALocalDate(fechaDesde);
        LocalDate hasta = FechaUtil.convertirALocalDate(fechaHasta);

        return obtenerContracargos(desde, hasta);
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - UTILIDADES HTTP
    // ========================================================================

    /**
     * Ejecuta una petición HTTP POST con JSON.
     *
     * @param endpoint Endpoint relativo (ej: "/obtener_rendiciones")
     * @param jsonBody Body de la petición en formato JSON
     * @return HttpResponse de Apache HttpClient
     * @throws Exception si hay error en la petición
     */
    private HttpResponse ejecutarPost(String endpoint, String jsonBody) throws Exception {
        String url = apiUrl + endpoint;

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
    // MÉTODOS PRIVADOS - VALIDACIONES
    // ========================================================================

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

    // ========================================================================
    // MÉTODOS PÚBLICOS - UTILIDADES
    // ========================================================================

    /**
     * Invalida el token actual forzando su renovación en el próximo uso.
     *
     * Útil para:
     * - Testing y debugging
     * - Cuando se detecta que el token no es válido
     * - Forzar renovación manual
     */
    public void invalidarToken() {
        log.info("Invalidando token actual manualmente");
        this.tokenActual = null;
        this.tokenExpiracion = null;
    }

    /**
     * Verifica si hay un token válido en caché.
     *
     * Considera el margen de seguridad de renovación (1 hora).
     *
     * @return true si hay token válido y vigente
     */
    public boolean tieneTokenValido() {
        if (tokenActual == null || tokenExpiracion == null) {
            return false;
        }

        return LocalDateTime.now()
                .plusHours(TOKEN_MARGEN_RENOVACION_HORAS)
                .isBefore(tokenExpiracion);
    }

    /**
     * Obtiene la fecha de expiración del token actual.
     *
     * @return LocalDateTime de expiración, o null si no hay token
     */
    public LocalDateTime getTokenExpiracion() {
        return tokenExpiracion;
    }
}