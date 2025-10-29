package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.util.FechaUtil;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;


/** Servicio orquestador para la sincronizacion de rendionces con E-PAGOS
 *  Se implementa el Patron FACADE ( simplifica la interaccion entre componentes )
 *
 *  Flujo de sincronizacion:
 *  1. Calcular rango de fechas ( hoy - N dias )
 *  2. Consultar rendiciones via E-PAGOS
 *  3. Delegar el procesamiento a Rendicioens Service
 *  4. Gestionar transacciones y logging centralizado
 *  5. Manejamos errores y reintentos
 * */
@Service
@Slf4j
public class SincronizacionRendicionesService {

    @Autowired
    private EpagosClientService epagosClientService;

    @Autowired
    private RendicionService rendicionService;

    // ========================================================================
    // MÉTODOS PÚBLICOS - SINCRONIZACIÓN
    // ========================================================================

    /**
     * Sincroniza rendiciones de una provincia para un rango retrospectivo.
     *
     * Este método es el punto de entrada principal para la sincronización.
     * Calcula automáticamente el rango de fechas y ejecuta todo el flujo.
     *
     * Flujo de ejecución:
     * 1. Validar parámetros de entrada
     * 2. Calcular rango de fechas (hoy - diasAtras hasta hoy)
     * 3. Consultar rendiciones a e-Pagos
     * 4. Validar respuesta
     * 5. Procesar y actualizar cobranzas
     * 6. Retornar métricas de procesamiento
     *
     * @param codigoProvincia Código de la provincia (ej: "PBA", "MDA", "CHACO")
     * @param diasAtras Cantidad de días hacia atrás para consultar (ej: 7 = última semana)
     * @return Cantidad de cobranzas actualizadas exitosamente
     * @throws IllegalArgumentException si los parámetros son inválidos
     * @throws EpagosException si hay error en la comunicación con e-Pagos
     */
    @Transactional
    public int sincronizarRendiciones(String codigoProvincia, int diasAtras) throws EpagosException {

        // PASO 1: Validar parámetros
        validarParametrosSincronizacion(codigoProvincia, diasAtras);

        // PASO 2: Calcular rango de fechas
        LocalDate[] rango = calcularRangoFechas(diasAtras);
        LocalDate fechaDesde = rango[0];
        LocalDate fechaHasta = rango[1];

        log.info("════════════════════════════════════════════════════════════");
        log.info("SINCRONIZACIÓN DE RENDICIONES - INICIO");
        log.info("────────────────────────────────────────────────────────────");
        log.info("  Provincia:     {}", codigoProvincia);
        log.info("  Fecha desde:   {}", fechaDesde);
        log.info("  Fecha hasta:   {}", fechaHasta);
        log.info("  Días consulta: {}", diasAtras);
        log.info("════════════════════════════════════════════════════════════");

        try {
            // PASO 3: Consultar rendiciones a e-Pagos
            List<RendicionDTO> rendiciones = consultarRendiciones(
                    codigoProvincia,
                    fechaDesde,
                    fechaHasta
            );

            // PASO 4: Validar respuesta
            if (rendiciones == null || rendiciones.isEmpty()) {
                log.info("→ No se encontraron rendiciones para el período consultado");
                return 0;
            }

            log.info("→ Rendiciones obtenidas: {}", rendiciones.size());

            // PASO 5: Procesar rendiciones y actualizar cobranzas
            int cobranzasActualizadas = procesarRendiciones(
                    codigoProvincia,
                    rendiciones
            );

            // PASO 6: Log de resultados
            log.info("════════════════════════════════════════════════════════════");
            log.info("SINCRONIZACIÓN DE RENDICIONES - COMPLETADA");
            log.info("────────────────────────────────────────────────────────────");
            log.info("  ✓ Rendiciones procesadas: {}", rendiciones.size());
            log.info("  ✓ Cobranzas actualizadas: {}", cobranzasActualizadas);
            log.info("════════════════════════════════════════════════════════════");

            return cobranzasActualizadas;

        } catch (EpagosException e) {
            log.error("════════════════════════════════════════════════════════════");
            log.error("ERROR EN SINCRONIZACIÓN DE RENDICIONES");
            log.error("────────────────────────────────────────────────────────────");
            log.error("  Provincia: {}", codigoProvincia);
            log.error("  Error: {}", e.getMessage());
            log.error("════════════════════════════════════════════════════════════");
            throw e;

        } catch (Exception e) {
            log.error("Error inesperado en sincronización de rendiciones", e);
            throw new EpagosException(
                    "Error al sincronizar rendiciones: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Sincroniza rendiciones para un rango de fechas específico.
     *
     * Útil cuando se necesita consultar un período puntual
     * (ej: reprocesar un mes específico).
     *
     * @param codigoProvincia Código de la provincia
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @return Cantidad de cobranzas actualizadas
     */
    @Transactional
    public int sincronizarRendiciones(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        validarParametrosSincronizacion(codigoProvincia, 1);
        validarRangoFechas(fechaDesde, fechaHasta);

        log.info("Sincronizando rendiciones: provincia={}, desde={}, hasta={}",
                codigoProvincia, fechaDesde, fechaHasta);

        try {
            List<RendicionDTO> rendiciones = consultarRendiciones(
                    codigoProvincia,
                    fechaDesde,
                    fechaHasta
            );

            if (rendiciones == null || rendiciones.isEmpty()) {
                log.info("No hay rendiciones para procesar en el rango especificado");
                return 0;
            }

            return procesarRendiciones(codigoProvincia, rendiciones);

        } catch (Exception e) {
            log.error("Error en sincronización de rendiciones: {}", e.getMessage(), e);
            throw new EpagosException(
                    "Error al sincronizar rendiciones",
                    e
            );
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - LÓGICA INTERNA
    // ========================================================================

    /**
     * Calcula el rango de fechas para consulta retrospectiva.
     *
     * Rango calculado:
     * - Fecha desde: hoy - diasAtras
     * - Fecha hasta: hoy
     *
     * Ejemplo: Si hoy es 2025-10-29 y diasAtras=7
     * - fechaDesde: 2025-10-22
     * - fechaHasta: 2025-10-29
     *
     * @param diasAtras Cantidad de días hacia atrás
     * @return Array con [fechaDesde, fechaHasta]
     */
    private LocalDate[] calcularRangoFechas(int diasAtras) {
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);

        log.debug("Rango calculado: {} → {}", fechaDesde, fechaHasta);

        return new LocalDate[]{fechaDesde, fechaHasta};
    }

    /**
     * Consulta rendiciones a e-Pagos para un rango de fechas.
     *
     * Delega la comunicación HTTP/SOAP al EpagosClientService.
     * Este método solo se encarga de convertir tipos y manejar errores.
     *
     * @param codigoProvincia Código de provincia
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @return Lista de rendiciones obtenidas
     * @throws EpagosException si hay error en la consulta
     */
    private List<RendicionDTO> consultarRendiciones(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) throws EpagosException {

        log.debug("→ Consultando rendiciones a e-Pagos...");

        // Convertir LocalDate a Date para compatibilidad con cliente
        Date desde = FechaUtil.convertirADate(fechaDesde);
        Date hasta = FechaUtil.convertirADate(fechaHasta);

        // Invocar cliente de e-Pagos
        List<RendicionDTO> rendiciones = epagosClientService.obtenerRendiciones(
                codigoProvincia,
                desde,
                hasta
        );

        log.debug("← Rendiciones recibidas: {}",
                rendiciones != null ? rendiciones.size() : 0);

        return rendiciones;
    }

    /**
     * Procesa las rendiciones obtenidas y actualiza las cobranzas.
     *
     * Delega el procesamiento al RendicionService, que se encarga de:
     * - Buscar cobranzas relacionadas
     * - Actualizar estados
     * - Registrar rendiciones en auditoría
     *
     * @param codigoProvincia Código de provincia
     * @param rendiciones Lista de rendiciones a procesar
     * @return Cantidad de cobranzas actualizadas
     */
    private int procesarRendiciones(
            String codigoProvincia,
            List<RendicionDTO> rendiciones) {

        log.debug("→ Procesando {} rendiciones...", rendiciones.size());

        // Delegar procesamiento al servicio especializado
        int cobranzasActualizadas = rendicionService.procesarRendiciones(
                codigoProvincia,
                rendiciones
        );

        log.debug("← Cobranzas actualizadas: {}", cobranzasActualizadas);

        return cobranzasActualizadas;
    }

    // ========================================================================
    // VALIDACIONES
    // ========================================================================

    /**
     * Valida los parámetros de entrada para sincronización.
     *
     * Validaciones:
     * - codigoProvincia no puede ser nulo o vacío
     * - diasAtras debe ser mayor a 0 y menor a 365
     *
     * @param codigoProvincia Código de provincia
     * @param diasAtras Días hacia atrás
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    private void validarParametrosSincronizacion(
            String codigoProvincia,
            int diasAtras) {

        if (codigoProvincia == null || codigoProvincia.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "El código de provincia no puede estar vacío"
            );
        }

        if (diasAtras <= 0) {
            throw new IllegalArgumentException(
                    "Los días hacia atrás deben ser mayor a 0"
            );
        }

        if (diasAtras > 365) {
            throw new IllegalArgumentException(
                    "Los días hacia atrás no pueden superar 365 días"
            );
        }
    }

    /**
     * Valida que el rango de fechas sea válido.
     *
     * Validaciones:
     * - Ninguna fecha puede ser nula
     * - fechaDesde debe ser anterior a fechaHasta
     * - El rango no puede ser mayor a 1 año
     *
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @throws IllegalArgumentException si el rango es inválido
     */
    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {

        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException(
                    "Las fechas no pueden ser nulas"
            );
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta"
            );
        }

        long diasDiferencia = java.time.temporal.ChronoUnit.DAYS
                .between(fechaDesde, fechaHasta);

        if (diasDiferencia > 365) {
            throw new IllegalArgumentException(
                    "El rango de fechas no puede superar 1 año"
            );
        }
    }

    // ========================================================================
    // MÉTODOS DE UTILIDAD
    // ========================================================================

    /**
     * Verifica la conectividad con e-Pagos.
     *
     * Útil para health checks y monitoreo del sistema.
     *
     * @return true si e-Pagos está disponible, false en caso contrario
     */
    public boolean verificarConectividad() {
        try {
            // Intentar obtener un token como prueba de conectividad
            epagosClientService.obtenerTokenValido();
            return true;
        } catch (Exception e) {
            log.error("Error al verificar conectividad con e-Pagos: {}",
                    e.getMessage());
            return false;
        }
    }

    /**
     * Sincroniza rendiciones de la última semana.
     *
     * Método de conveniencia para la sincronización más común.
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarUltimaSemana(String codigoProvincia) throws EpagosException {
        return sincronizarRendiciones(codigoProvincia, 7);
    }

    /**
     * Sincroniza rendiciones del último mes.
     *
     * @param codigoProvincia Código de provincia
     * @return Cantidad de cobranzas actualizadas
     */
    public int sincronizarUltimoMes(String codigoProvincia) throws EpagosException {
        return sincronizarRendiciones(codigoProvincia, 30);
    }

}
