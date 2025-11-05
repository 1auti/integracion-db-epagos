package org.transito_seguro.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transito_seguro.model.ResultadoBusquedaMultiprovincial;
import org.transito_seguro.model.ResultadoSincronizacion;
import org.transito_seguro.service.BusquedaMultiProvincialService;
import org.transito_seguro.service.SincronizacionService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Scheduler para sincronización automática con e-Pagos.
 * FLUJO ACTUALIZADO:
 * 1. Scheduler (cron job) - ESTE COMPONENTE
 *     ↓
 * 2. SincronizacionService (coordinador estratégico)
 *     ↓
 * 3. Para cada provincia:
 *    - EpagosClientService → Obtiene rendiciones y contracargos
 *    - RendicionService → Actualiza cobranzas en BD
 *    - ContracargoService → Registra contracargos en BD
 * MODOS DE OPERACIÓN:
 * - Paralelo: Procesa provincias simultáneamente
 * CONFIGURACIÓN:
 * Todas las configuraciones se gestionan desde application.yml:
 * - sincronizacion.rendiciones.enabled: Activa/desactiva el scheduler
 * - sincronizacion.rendiciones.dias-atras: Días hacia atrás para consultar
 * - sincronizacion.rendiciones.provincias: Lista de provincias a procesar
 */
@Component
@Slf4j
public class SincronizacionScheduler {


    /**
     * Servicio coordinador principal de sincronización.
     * Responsabilidad: Orquestar todo el flujo de sincronización
     */
    @Autowired
    private SincronizacionService sincronizacionService;

    /**
     * Servicio de búsqueda multiprovincial (modo paralelo).
     * Responsabilidad: Procesar múltiples provincias concurrentemente
     */
    @Autowired
    private BusquedaMultiProvincialService busquedaMultiProvincialService;

    /**
     * Activa/desactiva la ejecución del scheduler.
     */
    @Value("${sincronizacion.rendiciones.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Días hacia atrás para consultar.
     * Por defecto: 7 días (1 semana)
     */
    @Value("${sincronizacion.rendiciones.dias-atras:7}")
    private int diasAtras;

    /**
     * Códigos de provincia a sincronizar, separados por comas.
     * Ejemplo: "PBA,MDA,CHACO,ENTRERIOS,FORMOSA,SANTAROSA"
     */
    @Value("${sincronizacion.rendiciones.provincias:PBA,MDA,CHACO}")
    private String[] provincias;

    /**
     * Modo de procesamiento: "paralelo" o "secuencial"
     * Por defecto: secuencial (más estable)
     */
    @Value("${sincronizacion.rendiciones.modo:secuencial}")
    private String modoProcesamiento;

    // Formatter para logs
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    // SINCRONIZACIÓN DIARIA AUTOMÁTICA
    // ========================================================================

    /**
     * Job programado para sincronización DIARIA de TODAS las provincias.
     *
     * EXPRESIÓN CRON:
     * "0 0 2 * * ?" = Todos los días a las 2:00 AM
     * - 0: segundo 0
     * - 0: minuto 0
     * - 2: hora 2 AM
     * - *: cualquier día del mes
     * - *: cualquier mes
     * - ?: cualquier día de la semana
     *
     * PROCESO:
     * 1. Verificar si el scheduler está habilitado
     * 2. Ejecutar sincronización
     * 3. Registrar métricas y resultados
     *
     * CONFIGURACIÓN DESDE application.yml:
     * sincronizacion.rendiciones.cron: Permite cambiar el horario
     * Ejemplo: "0 0 3 * * ?" = 3:00 AM
     */
    @Scheduled(cron = "${sincronizacion.rendiciones.cron:0 0 2 * * ?}")
    public void sincronizarAutomatico() {

        // Validar si el scheduler está habilitado
        if (!schedulerEnabled) {
            log.debug("Scheduler deshabilitado. " +
                    "Configurar 'sincronizacion.rendiciones.enabled=true'");
            return;
        }

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN AUTOMÁTICA - INICIO                           ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Fecha/Hora:   {}                                             ║",
                LocalDateTime.now().format(FORMATTER));
        log.info("║  Rango:        últimos {} días                                ║",
                diasAtras);
        log.info("║  Provincias:   {} ({})                                        ║",
                provincias.length, String.join(", ", provincias));
        log.info("║  Modo:         {}                                             ║",
                modoProcesamiento.toUpperCase());
        log.info("║  Sincroniza:   RENDICIONES + CONTRACARGOS                     ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        long tiempoInicio = System.currentTimeMillis();

        try {
            sincronizarEnParalelo();

            long duracion = System.currentTimeMillis() - tiempoInicio;

            log.info("╔═══════════════════════════════════════════════════════════════╗");
            log.info("║  SINCRONIZACIÓN AUTOMÁTICA - COMPLETADA                       ║");
            log.info("╠═══════════════════════════════════════════════════════════════╣");
            log.info("║  Duración:     {} ms ({} segundos)                            ║",
                    duracion, duracion / 1000);
            log.info("║  Fecha/Hora:   {}                                             ║",
                    LocalDateTime.now().format(FORMATTER));
            log.info("╚═══════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  ERROR CRÍTICO EN SINCRONIZACIÓN                              ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            log.error("Error no esperado durante sincronización: {}", e.getMessage(), e);
        }
    }


    /**
     * Ejecuta sincronización en modo PARALELO.
     * DELEGACIÓN:
     * Este método delega la complejidad de la concurrencia al
     * BusquedaMultiProvincialService que gestiona el ExecutorService.
     */
    private void sincronizarEnParalelo() {
        log.info("→ Iniciando sincronización PARALELA de {} provincias", provincias.length);

        // Convertir array a lista
        List<String> listaProvincias = Arrays.asList(provincias);

        // Delegar a BusquedaMultiProvincialService (gestiona concurrencia)
        ResultadoBusquedaMultiprovincial resultado =
                busquedaMultiProvincialService.sincronizarProvinciasEnParalelo(listaProvincias);

        // Log de resultados
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  RESUMEN - SINCRONIZACIÓN PARALELA                            ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincias procesadas:     {}                                ║",
                resultado.getTotalProvinciasConsultadas());
        log.info("║  Provincias exitosas:       {}                                ║",
                resultado.getProvinciasExitosas());
        log.info("║  Rendiciones actualizadas:  {}                                ║",
                resultado.getTotalInfraccionesProcesadas());
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // Detalle por provincia
        log.debug("Detalle por provincia:");
        resultado.getResultadosPorProvincia().forEach(prov -> {
            if (prov.isExitoso()) {
                log.debug("  ✓ {} → {} rendiciones, {} contracargos",
                        prov.getCodigoProvincia(),
                        prov.getInfraccionesActualizadas(),
                        prov.getContracargosProcesados());
            } else {
                log.warn("  ✗ {} → ERROR: {}",
                        prov.getCodigoProvincia(),
                        prov.getMensajeError());
            }
        });
    }



}