package org.transito_seguro.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transito_seguro.service.ConsultaRendicionesService;
import org.transito_seguro.service.RendicionService;
import org.transito_seguro.service.SincronizacionRendicionesService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler para sincronización automática de rendiciones desde e-Pagos.
 *
 * Este componente ejecuta tareas programadas para:
 * - Sincronizar rendiciones de la última semana (diario/semanal)
 * - Detectar transacciones huérfanas
 * - Generar reportes de sincronización
 *
 * Configuración mediante propiedades:
 * - sincronizacion.rendiciones.enabled: Activa/desactiva el scheduler
 * - sincronizacion.rendiciones.dias-atras: Días hacia atrás para consultar
*/
@Component
@Slf4j
public class SincronizacionScheduler {

    @Autowired
    private ConsultaRendicionesService consultaService;

    @Autowired
    private RendicionService rendicionService;

    @Autowired
    private SincronizacionRendicionesService sincronizacionRendicionesService;

    /**
     * Activa/desactiva la ejecución del scheduler.
     * Configurable desde application.yml
     */
    @Value("${sincronizacion.rendiciones.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Días hacia atrás para consultar rendiciones.
     * Por defecto: 7 días (1 semana)
     */
    @Value("${sincronizacion.rendiciones.dias-atras:7}")
    private int diasAtras;

    /**
     * Códigos de provincia a sincronizar, separados por comas.
     * Ejemplo: "PBA,MDA,CHACO"
     */
    @Value("${sincronizacion.rendiciones.provincias:PBA}")
    private String[] provincias;

    // Formatter para logs
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    // SINCRONIZACIÓN DIARIA DE RENDICIONES
    // ========================================================================

    /**
     * Job programado para sincronización DIARIA de rendiciones.
     *
     * Se ejecuta todos los días a las 2:00 AM (hora Argentina).
     * Consulta las rendiciones de los últimos N días configurados.
     *
     * Expresión Cron: "0 0 2 * * ?"
     * - Segundo: 0
     * - Minuto: 0
     * - Hora: 2 (2 AM)
     * - Día del mes: * (todos)
     * - Mes: * (todos)
     * - Día de la semana: ? (cualquiera)
     *
     * Esta sincronización es INCREMENTAL:
     * - Solo consulta los últimos N días
     * - Actualiza solo las rendiciones nuevas o modificadas
     * - Evita consultas masivas innecesarias
     */
    @Scheduled(cron = "${sincronizacion.rendiciones.cron:0 0 2 * * ?}")
    public void sincronizarRendicionesDiario() {

        // Validar si el scheduler está habilitado
        if (!schedulerEnabled) {
            log.debug("Scheduler de rendiciones deshabilitado. " +
                    "Configurar 'sincronizacion.rendiciones.enabled=true'");
            return;
        }

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  INICIO - SINCRONIZACIÓN AUTOMÁTICA DE RENDICIONES          ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Fecha/Hora: {}                              ║",
                LocalDateTime.now().format(FORMATTER));
        log.info("║  Rango: últimos {} días                                      ║", diasAtras);
        log.info("║  Provincias: {}                                              ║",
                String.join(", ", provincias));
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        try {
            // Ejecutar sincronización para cada provincia configurada
            int totalActualizadas = 0;

            for (String codigoProvincia : provincias) {
                log.info("→ Procesando provincia: {}", codigoProvincia);

                try {
                    int actualizadas = sincronizacionRendicionesService.sincronizarRendiciones(
                            codigoProvincia,
                            diasAtras
                    );

                    totalActualizadas += actualizadas;
                    log.info("✓ Provincia {} procesada: {} cobranzas actualizadas",
                            codigoProvincia, actualizadas);

                } catch (Exception e) {
                    log.error("✗ Error al sincronizar provincia {}: {}",
                            codigoProvincia, e.getMessage(), e);
                    // Continuar con las demás provincias
                }
            }

            // Resumen final
            log.info("╔═══════════════════════════════════════════════════════════════╗");
            log.info("║  FIN - SINCRONIZACIÓN COMPLETADA                            ║");
            log.info("╠═══════════════════════════════════════════════════════════════╣");
            log.info("║  Total cobranzas actualizadas: {}                           ║",
                    totalActualizadas);
            log.info("║  Fecha/Hora: {}                              ║",
                    LocalDateTime.now().format(FORMATTER));
            log.info("╚═══════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  ERROR CRÍTICO EN SINCRONIZACIÓN                            ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            log.error("Error no esperado durante sincronización: {}", e.getMessage(), e);
        }
    }

    // ========================================================================
    // SINCRONIZACIÓN SEMANAL COMPLETA (OPCIONAL)
    // ========================================================================

    /**
     * Job programado para sincronización SEMANAL completa.
     *
     * Se ejecuta todos los lunes a las 3:00 AM.
     * Realiza una sincronización más amplia (últimas 4 semanas).
     *
     * Expresión Cron: "0 0 3 * * MON"
     * - Segundo: 0
     * - Minuto: 0
     * - Hora: 3 (3 AM)
     * - Día del mes: * (todos)
     * - Mes: * (todos)
     * - Día de la semana: MON (solo lunes)
     *
     * Este proceso es más exhaustivo y puede tomar más tiempo.
     */
    @Scheduled(cron = "${sincronizacion.rendiciones.semanal.cron:0 0 3 * * MON}")
    public void sincronizarRendicionesSemanal() {

        if (!schedulerEnabled) {
            return;
        }

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN SEMANAL COMPLETA - INICIANDO                ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        try {
            // Sincronización más amplia: últimas 4 semanas (28 días)
            int diasSemanal = 28;
            int totalActualizadas = 0;

            for (String codigoProvincia : provincias) {
                log.info("→ Sincronización semanal para provincia: {}", codigoProvincia);

                try {
                    int actualizadas = sincronizacionRendicionesService.sincronizarRendiciones(
                            codigoProvincia,
                            diasSemanal
                    );

                    totalActualizadas += actualizadas;

                } catch (Exception e) {
                    log.error("Error en sincronización semanal de {}: {}",
                            codigoProvincia, e.getMessage());
                }
            }

            log.info("✓ Sincronización semanal completada: {} actualizaciones",
                    totalActualizadas);

        } catch (Exception e) {
            log.error("Error crítico en sincronización semanal: {}", e.getMessage(), e);
        }
    }

    // ========================================================================
    // VERIFICACIÓN DE SALUD DEL SISTEMA (OPCIONAL)
    // ========================================================================

    /**
     * Job de verificación de salud del sistema de sincronización.
     *
     * Se ejecuta cada hora para verificar:
     * - Conectividad con e-Pagos
     * - Validez del token
     * - Estado de transacciones pendientes
     *
     * Expresión Cron: "0 0 * * * ?"
     * Cada hora en punto
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void verificarSaludSistema() {

        if (!schedulerEnabled) {
            return;
        }

        try {
            // Verificar conectividad básica
            boolean sistemaDisponible = sincronizacionRendicionesService.verificarConectividad();

            if (!sistemaDisponible) {
                log.warn("⚠ ALERTA: Sistema e-Pagos no responde correctamente");
            } else {
                log.debug("✓ Sistema e-Pagos operativo");
            }

        } catch (Exception e) {
            log.error("Error en verificación de salud: {}", e.getMessage());
        }
    }

    // ========================================================================
    // MÉTODOS DE UTILIDAD
    // ========================================================================

    /**
     * Obtiene el estado actual del scheduler.
     *
     * @return true si está habilitado, false si está deshabilitado
     */
    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    /**
     * Habilita el scheduler en tiempo de ejecución.
     */
    public void habilitarScheduler() {
        this.schedulerEnabled = true;
        log.info("✓ Scheduler de rendiciones HABILITADO");
    }

    /**
     * Deshabilita el scheduler en tiempo de ejecución.
     */
    public void deshabilitarScheduler() {
        this.schedulerEnabled = false;
        log.warn("✗ Scheduler de rendiciones DESHABILITADO");
    }
}