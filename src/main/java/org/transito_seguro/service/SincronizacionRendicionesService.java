package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.exception.EpagosException;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio orquestador para sincronización SECUENCIAL de rendiciones.
 *
 * RESPONSABILIDAD ÚNICA:
 * Coordinar la sincronización de UNA provincia a la vez (secuencial).
 *
 * Diferencia clave con BusquedaMultiProvincialService:
 * - SincronizacionRendicionesService: UNA provincia, SECUENCIAL
 * - BusquedaMultiProvincialService: MÚLTIPLES provincias, PARALELO
 *
 * Patrón de diseño: FACADE
 * Simplifica la interacción entre ConsultaRendicionesService y RendicionService.
 *
 * Delegación de responsabilidades:
 * - ConsultaRendicionesService: Consulta datos desde e-Pagos
 * - RendicionService: Procesa y actualiza base de datos
 *
 * Casos de uso:
 * 1. Sincronización manual vía API REST
 * 2. Reproceso de una provincia específica
 * 3. Testing de integración
 * 4. Sincronización bajo demanda
 *
 * Flujo de ejecución:
 * 1. Validar parámetros
 * 2. Delegar consulta a ConsultaRendicionesService
 * 3. Delegar procesamiento a RendicionService
 * 4. Retornar métricas
 *
 * @author Sistema Tránsito Seguro
 * @version 2.0 - Refactorizado con separación de responsabilidades
 */
@Service
@Slf4j
public class SincronizacionRendicionesService {

    // ========================================================================
    // DEPENDENCIAS - Servicios especializados
    // ========================================================================

    /**
     * Servicio de CONSULTA a e-Pagos.
     * Responsabilidad: Obtener rendiciones desde la API externa.
     */
    @Autowired
    private ConsultaRendicionesService consultaService;

    /**
     * Servicio de PROCESAMIENTO de rendiciones.
     * Responsabilidad: Actualizar base de datos local.
     */
    @Autowired
    private RendicionService rendicionService;

    // ========================================================================
    // MÉTODOS PÚBLICOS - SINCRONIZACIÓN SECUENCIAL
    // ========================================================================

    /**
     * Sincroniza rendiciones de una provincia (últimos N días).
     *
     * Este es el método principal para sincronización secuencial.
     * Útil para:
     * - Triggers manuales desde API REST
     * - Reprocesos específicos
     * - Testing
     *
     * Flujo:
     * 1. Validar parámetros
     * 2. Consultar rendiciones (ConsultaRendicionesService)
     * 3. Procesar rendiciones (RendicionService)
     * 4. Retornar cantidad actualizada
     *
     * @param codigoProvincia Código de provincia (ej: "PBA", "MDA")
     * @param diasAtras Días hacia atrás para consultar (1-90)
     * @return Cantidad de cobranzas actualizadas
     * @throws IllegalArgumentException si los parámetros son inválidos
     * @throws EpagosException si hay error de comunicación
     */
    @Transactional
    public int sincronizarRendiciones(String codigoProvincia, int diasAtras)
            throws EpagosException {

        log.info("════════════════════════════════════════════════════════════");
        log.info("SINCRONIZACIÓN SECUENCIAL - INICIO");
        log.info("────────────────────────────────────────────────────────────");
        log.info("  Provincia:     {}", codigoProvincia);
        log.info("  Días atrás:    {}", diasAtras);
        log.info("════════════════════════════════════════════════════════════");

        try {
            // PASO 1: Consultar rendiciones desde e-Pagos
            // DELEGACIÓN a ConsultaRendicionesService
            List<RendicionDTO> rendiciones = consultaService.consultarRendiciones(
                    codigoProvincia,
                    diasAtras
            );

            // Validación
            if (rendiciones == null || rendiciones.isEmpty()) {
                log.info("→ No se encontraron rendiciones para el período consultado");
                logResumen(0, 0);
                return 0;
            }

            log.info("→ Rendiciones obtenidas: {}", rendiciones.size());

            // PASO 2: Procesar rendiciones y actualizar BD
            // DELEGACIÓN a RendicionService
            int cobranzasActualizadas = rendicionService.procesarRendiciones(
                    codigoProvincia,
                    rendiciones
            );

            // PASO 3: Log de resultados
            logResumen(rendiciones.size(), cobranzasActualizadas);

            return cobranzasActualizadas;

        } catch (EpagosException e) {
            log.error("════════════════════════════════════════════════════════════");
            log.error("ERROR EN SINCRONIZACIÓN");
            log.error("────────────────────────────────────────────────────────────");
            log.error("  Provincia: {}", codigoProvincia);
            log.error("  Error: {}", e.getMessage());
            log.error("════════════════════════════════════════════════════════════");
            throw e;

        } catch (Exception e) {
            log.error("Error inesperado en sincronización", e);
            throw new EpagosException(
                    "Error al sincronizar rendiciones: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Sincroniza rendiciones para un rango de fechas específico.
     *
     * Útil para:
     * - Reprocesar períodos concretos (un mes específico)
     * - Auditorías históricas
     * - Corrección de datos
     *
     * @param codigoProvincia Código de provincia
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @return Cantidad de cobranzas actualizadas
     * @throws EpagosException si hay error
     */
    @Transactional
    public int sincronizarRendiciones(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        log.info("Sincronizando rendiciones: provincia={}, desde={}, hasta={}",
                codigoProvincia, fechaDesde, fechaHasta);

        try {
            // PASO 1: Consultar rendiciones
            // DELEGACIÓN a ConsultaRendicionesService
            List<RendicionDTO> rendiciones = consultaService.consultarRendiciones(
                    codigoProvincia,
                    fechaDesde,
                    fechaHasta
            );

            // Validación
            if (rendiciones == null || rendiciones.isEmpty()) {
                log.info("No hay rendiciones para procesar en el rango especificado");
                return 0;
            }

            // PASO 2: Procesar rendiciones
            // DELEGACIÓN a RendicionService
            return rendicionService.procesarRendiciones(
                    codigoProvincia,
                    rendiciones
            );

        } catch (Exception e) {
            log.error("Error en sincronización de rendiciones: {}", e.getMessage(), e);
            throw new EpagosException(
                    "Error al sincronizar rendiciones",
                    e
            );
        }
    }

    // ========================================================================
    // MÉTODOS DE CONVENIENCIA
    // ========================================================================

    /**
     * Sincroniza rendiciones de la última semana (7 días).
     *
     * Método de conveniencia para el caso de uso más común.
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarUltimaSemana(String codigoProvincia) throws EpagosException {
        log.debug("Sincronizando última semana para provincia: {}", codigoProvincia);
        return sincronizarRendiciones(codigoProvincia, 7);
    }

    /**
     * Sincroniza rendiciones del último mes (30 días).
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarUltimoMes(String codigoProvincia) throws EpagosException {
        log.debug("Sincronizando último mes para provincia: {}", codigoProvincia);
        return sincronizarRendiciones(codigoProvincia, 30);
    }

    /**
     * Sincroniza rendiciones del mes actual.
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarMesActual(String codigoProvincia) throws EpagosException {
        log.debug("Sincronizando mes actual para provincia: {}", codigoProvincia);

        // Delegar a ConsultaRendicionesService que calcula el rango
        List<RendicionDTO> rendiciones = consultaService.consultarMesActual(codigoProvincia);

        if (rendiciones == null || rendiciones.isEmpty()) {
            return 0;
        }

        return rendicionService.procesarRendiciones(codigoProvincia, rendiciones);
    }

    /**
     * Sincroniza rendiciones del mes anterior.
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarMesAnterior(String codigoProvincia) throws EpagosException {
        log.debug("Sincronizando mes anterior para provincia: {}", codigoProvincia);

        // Delegar a ConsultaRendicionesService que calcula el rango
        List<RendicionDTO> rendiciones = consultaService.consultarMesAnterior(codigoProvincia);

        if (rendiciones == null || rendiciones.isEmpty()) {
            return 0;
        }

        return rendicionService.procesarRendiciones(codigoProvincia, rendiciones);
    }

    // ========================================================================
    // MÉTODOS DE UTILIDAD
    // ========================================================================

    /**
     * Verifica la conectividad con e-Pagos.
     *
     * Útil para:
     * - Health checks
     * - Validación antes de operaciones masivas
     * - Diagnóstico de problemas
     *
     * @return true si e-Pagos está disponible, false si hay problemas
     */
    public boolean verificarConectividad() {
        // DELEGACIÓN a ConsultaRendicionesService
        return consultaService.verificarConectividad();
    }

    // ========================================================================
    // LOGGING
    // ========================================================================

    /**
     * Log de resumen de sincronización.
     */
    private void logResumen(int rendicionesProcesadas, int cobranzasActualizadas) {
        log.info("════════════════════════════════════════════════════════════");
        log.info("SINCRONIZACIÓN SECUENCIAL - COMPLETADA");
        log.info("────────────────────────────────────────────────────────────");
        log.info("  ✓ Rendiciones procesadas: {}", rendicionesProcesadas);
        log.info("  ✓ Cobranzas actualizadas: {}", cobranzasActualizadas);
        log.info("════════════════════════════════════════════════════════════");
    }
}