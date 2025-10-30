package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.util.FechaUtil;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Servicio de CONSULTA de rendiciones desde e-Pagos.
 *
 * RESPONSABILIDAD ÚNICA:
 * Este servicio se encarga EXCLUSIVAMENTE de consultar datos desde la API
 * de e-Pagos. NO procesa ni actualiza datos en la base de datos.
 *
 * Patrón de diseño: FACADE + STRATEGY
 * - FACADE: Simplifica la interacción con la API de e-Pagos
 * - STRATEGY: Permite diferentes estrategias de consulta (por fecha, por número, etc.)
 *
 * Principios SOLID aplicados:
 * - Single Responsibility: Solo consulta, no procesa
 * - Open/Closed: Extendible para nuevos tipos de consulta sin modificar existente
 * - Dependency Inversion: Depende de abstracciones (EpagosClientService)
 *
 * Responsabilidades:
 * 1. ✅ Calcular rangos de fechas para consultas
 * 2. ✅ Validar parámetros de entrada
 * 3. ✅ Invocar EpagosClientService
 * 4. ✅ Validar respuestas de e-Pagos
 * 5. ✅ Manejar errores de comunicación
 * 6. ✅ Logging de operaciones
 *
 * NO hace:
 * ❌ Procesar rendiciones
 * ❌ Actualizar base de datos
 * ❌ Lógica de negocio
 * ❌ Transacciones de BD
 *
 * Flujo típico:
 * 1. Cliente llama a consultarRendiciones(provincia, diasAtras)
 * 2. Se calculan fechas (hoy - diasAtras, hoy)
 * 3. Se validan parámetros
 * 4. Se invoca EpagosClientService
 * 5. Se valida y retorna la respuesta
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
@Service
@Slf4j
public class ConsultaRendicionesService {

    // ========================================================================
    // DEPENDENCIAS
    // ========================================================================

    /**
     * Cliente para comunicación con API de e-Pagos.
     * Encapsula toda la lógica HTTP/SOAP.
     */
    @Autowired
    private EpagosClientService epagosClientService;

    // ========================================================================
    // CONSTANTES
    // ========================================================================

    /**
     * Máximo de días permitidos en una consulta (para evitar timeouts).
     */
    private static final int MAX_DIAS_CONSULTA = 90;

    /**
     * Mínimo de días para una consulta.
     */
    private static final int MIN_DIAS_CONSULTA = 1;

    // ========================================================================
    // MÉTODOS PÚBLICOS - CONSULTA POR DÍAS RETROSPECTIVOS
    // ========================================================================

    /**
     * Consulta rendiciones de los últimos N días para una provincia.
     *
     * Este es el método más usado: consulta retrospectiva simple.
     * Calcula automáticamente el rango: (hoy - diasAtras) hasta hoy.
     *
     * Ejemplo de uso:
     * ```java
     * // Consultar rendiciones de la última semana
     * List<RendicionDTO> rendiciones =
     *     consultaService.consultarRendiciones("PBA", 7);
     * ```
     *
     * @param codigoProvincia Código de provincia (ej: "PBA", "MDA", "CHACO")
     * @param diasAtras Cantidad de días hacia atrás (1-90)
     * @return Lista de rendiciones obtenidas desde e-Pagos (nunca null, puede estar vacía)
     * @throws IllegalArgumentException si los parámetros son inválidos
     * @throws EpagosException si hay error en la comunicación con e-Pagos
     */
    public List<RendicionDTO> consultarRendiciones(String codigoProvincia, int diasAtras) throws EpagosException {

        log.debug("Iniciando consulta de rendiciones: provincia={}, diasAtras={}",
                codigoProvincia, diasAtras);

        // PASO 1: Validar parámetros
        validarCodigoProvincia(codigoProvincia);
        validarDiasAtras(diasAtras);

        // PASO 2: Calcular rango de fechas
        LocalDate[] rango = calcularRangoRetrospectivo(diasAtras);
        LocalDate fechaDesde = rango[0];
        LocalDate fechaHasta = rango[1];

        // PASO 3: Ejecutar consulta
        return consultarRendiciones(codigoProvincia, fechaDesde, fechaHasta);
    }

    /**
     * Consulta rendiciones para un rango de fechas específico.
     *
     * Útil para:
     * - Reprocesar períodos específicos
     * - Consultas personalizadas
     * - Auditorías de meses anteriores
     *
     * Ejemplo de uso:
     * ```java
     * LocalDate desde = LocalDate.of(2025, 10, 1);
     * LocalDate hasta = LocalDate.of(2025, 10, 31);
     * List<RendicionDTO> rendiciones =
     *     consultaService.consultarRendiciones("PBA", desde, hasta);
     * ```
     *
     * @param codigoProvincia Código de provincia
     * @param fechaDesde Fecha inicial del rango (inclusive)
     * @param fechaHasta Fecha final del rango (inclusive)
     * @return Lista de rendiciones obtenidas
     * @throws IllegalArgumentException si los parámetros son inválidos
     * @throws EpagosException si hay error en la comunicación
     */
    public List<RendicionDTO> consultarRendiciones(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  CONSULTA DE RENDICIONES - e-Pagos                        ║");
        log.info("╠════════════════════════════════════════════════════════════╣");
        log.info("║  Provincia:    {}                                         ║", codigoProvincia);
        log.info("║  Desde:        {}                                         ║", fechaDesde);
        log.info("║  Hasta:        {}                                         ║", fechaHasta);
        log.info("╚════════════════════════════════════════════════════════════╝");

        // PASO 1: Validar parámetros
        validarCodigoProvincia(codigoProvincia);
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            // PASO 2: Convertir fechas (LocalDate → Date)
            Date desde = FechaUtil.convertirADate(fechaDesde);
            Date hasta = FechaUtil.convertirADate(fechaHasta);

            // PASO 3: Invocar cliente de e-Pagos
            log.debug("→ Invocando EpagosClientService.obtenerRendiciones()...");
            List<RendicionDTO> rendiciones = epagosClientService.obtenerRendiciones(
                    codigoProvincia,
                    desde,
                    hasta
            );

            // PASO 4: Validar respuesta
            validarRespuesta(rendiciones);

            // PASO 5: Log de resultado
            log.info("╔════════════════════════════════════════════════════════════╗");
            log.info("║  CONSULTA COMPLETADA                                      ║");
            log.info("╠════════════════════════════════════════════════════════════╣");
            log.info("║  Rendiciones obtenidas: {}                                ║",
                    rendiciones.size());
            log.info("╚════════════════════════════════════════════════════════════╝");

            return rendiciones;

        } catch (EpagosException e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║  ERROR EN CONSULTA DE RENDICIONES                         ║");
            log.error("╠════════════════════════════════════════════════════════════╣");
            log.error("║  Provincia: {}                                            ║", codigoProvincia);
            log.error("║  Error:     {}                                            ║", e.getMessage());
            log.error("╚════════════════════════════════════════════════════════════╝");
            throw e;

        } catch (Exception e) {
            log.error("Error inesperado al consultar rendiciones", e);
            throw new EpagosException(
                    "Error inesperado al consultar rendiciones: " + e.getMessage(),
                    e
            );
        }
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - CONSULTAS ESPECIALES
    // ========================================================================

    /**
     * Consulta rendiciones de la última semana (7 días).
     *
     * Método de conveniencia para el caso de uso más común.
     *
     * @param codigoProvincia Código de provincia
     * @return Lista de rendiciones
     */
    public List<RendicionDTO> consultarUltimaSemana(String codigoProvincia) throws EpagosException {
        log.debug("Consultando última semana para provincia: {}", codigoProvincia);
        return consultarRendiciones(codigoProvincia, 7);
    }

    /**
     * Consulta rendiciones del último mes (30 días).
     *
     * @param codigoProvincia Código de provincia
     * @return Lista de rendiciones
     */
    public List<RendicionDTO> consultarUltimoMes(String codigoProvincia) throws EpagosException {
        log.debug("Consultando último mes para provincia: {}", codigoProvincia);
        return consultarRendiciones(codigoProvincia, 30);
    }

    /**
     * Consulta rendiciones del mes actual (desde día 1 hasta hoy).
     *
     * @param codigoProvincia Código de provincia
     * @return Lista de rendiciones
     */
    public List<RendicionDTO> consultarMesActual(String codigoProvincia) throws EpagosException {
        log.debug("Consultando mes actual para provincia: {}", codigoProvincia);

        LocalDate[] rango = FechaUtil.obtenerRangoMesActual();
        return consultarRendiciones(codigoProvincia, rango[0], rango[1]);
    }

    /**
     * Consulta rendiciones del mes anterior completo.
     *
     * @param codigoProvincia Código de provincia
     * @return Lista de rendiciones
     */
    public List<RendicionDTO> consultarMesAnterior(String codigoProvincia) throws EpagosException {
        log.debug("Consultando mes anterior para provincia: {}", codigoProvincia);

        LocalDate[] rango = FechaUtil.obtenerRangoMesAnterior();
        return consultarRendiciones(codigoProvincia, rango[0], rango[1]);
    }

    // ========================================================================
    // MÉTODOS DE UTILIDAD
    // ========================================================================

    /**
     * Calcula el rango de fechas para una consulta retrospectiva.
     *
     * Rango calculado:
     * - Fecha desde: hoy - diasAtras
     * - Fecha hasta: hoy
     *
     * @param diasAtras Cantidad de días hacia atrás
     * @return Array con [fechaDesde, fechaHasta]
     */
    private LocalDate[] calcularRangoRetrospectivo(int diasAtras) {
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);

        log.debug("Rango calculado: {} → {} ({} días)",
                fechaDesde, fechaHasta, diasAtras);

        return new LocalDate[]{fechaDesde, fechaHasta};
    }

    /**
     * Verifica la conectividad con e-Pagos.
     *
     * Útil para:
     * - Health checks
     * - Validación antes de operaciones masivas
     * - Diagnóstico de problemas
     *
     * @return true si e-Pagos responde correctamente, false si hay problemas
     */
    public boolean verificarConectividad() {
        try {
            log.debug("Verificando conectividad con e-Pagos...");
            epagosClientService.obtenerTokenValido();
            log.debug("✓ Conectividad OK");
            return true;

        } catch (Exception e) {
            log.warn("✗ Error de conectividad con e-Pagos: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // VALIDACIONES
    // ========================================================================

    /**
     * Valida el código de provincia.
     *
     * @param codigoProvincia Código a validar
     * @throws IllegalArgumentException si el código es inválido
     */
    private void validarCodigoProvincia(String codigoProvincia) {
        if (codigoProvincia == null || codigoProvincia.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "El código de provincia no puede estar vacío"
            );
        }

        // Validación opcional: longitud y formato
        if (codigoProvincia.length() > 10) {
            throw new IllegalArgumentException(
                    "El código de provincia no puede tener más de 10 caracteres"
            );
        }
    }

    /**
     * Valida la cantidad de días hacia atrás.
     *
     * @param diasAtras Días a validar
     * @throws IllegalArgumentException si los días son inválidos
     */
    private void validarDiasAtras(int diasAtras) {
        if (diasAtras < MIN_DIAS_CONSULTA) {
            throw new IllegalArgumentException(
                    String.format("Los días deben ser al menos %d", MIN_DIAS_CONSULTA)
            );
        }

        if (diasAtras > MAX_DIAS_CONSULTA) {
            throw new IllegalArgumentException(
                    String.format("Los días no pueden superar %d (riesgo de timeout)",
                            MAX_DIAS_CONSULTA)
            );
        }
    }

    /**
     * Valida un rango de fechas.
     *
     * Validaciones:
     * - Ninguna fecha puede ser nula
     * - fechaDesde debe ser anterior o igual a fechaHasta
     * - fechaHasta no puede ser futura
     * - El rango no puede exceder MAX_DIAS_CONSULTA
     *
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @throws IllegalArgumentException si el rango es inválido
     */
    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {

        // Validar nulidad
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException(
                    "Las fechas no pueden ser nulas"
            );
        }

        // Validar orden
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta"
            );
        }

        // Validar que no sea futura
        if (fechaHasta.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha hasta no puede ser futura"
            );
        }

        // Validar tamaño del rango
        long diasDiferencia = java.time.temporal.ChronoUnit.DAYS
                .between(fechaDesde, fechaHasta);

        if (diasDiferencia > MAX_DIAS_CONSULTA) {
            throw new IllegalArgumentException(
                    String.format(
                            "El rango de fechas no puede superar %d días (actual: %d días)",
                            MAX_DIAS_CONSULTA, diasDiferencia
                    )
            );
        }
    }

    /**
     * Valida la respuesta obtenida de e-Pagos.
     *
     * @param rendiciones Lista de rendiciones obtenida
     * @throws EpagosException si la respuesta es inválida
     */
    private void validarRespuesta(List<RendicionDTO> rendiciones) throws EpagosException {
        if (rendiciones == null) {
            throw new EpagosException(
                    "La respuesta de e-Pagos es nula (error de comunicación)"
            );
        }

        // Log adicional si está vacía
        if (rendiciones.isEmpty()) {
            log.debug("⚠ La consulta no retornó rendiciones (puede ser normal)");
        }
    }

    // ========================================================================
    // MÉTODOS INFORMATIVOS
    // ========================================================================

    /**
     * Obtiene información sobre los límites de consulta.
     *
     * @return Mapa con límites configurados
     */
    public java.util.Map<String, Integer> obtenerLimitesConsulta() {
        java.util.Map<String, Integer> limites = new java.util.HashMap<>();
        limites.put("minDias", MIN_DIAS_CONSULTA);
        limites.put("maxDias", MAX_DIAS_CONSULTA);
        return limites;
    }
}