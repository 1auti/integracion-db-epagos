package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.transito_seguro.model.ResultadoBusquedaMultiprovincial;
import org.transito_seguro.model.ResultadoBusquedaProvincia;
import org.transito_seguro.model.ResultadoSincronizacion;
import org.transito_seguro.exception.BusquedaMultiProvincialException;

import javax.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio de COORDINACIÓN para búsqueda multiprovincial CONCURRENTE.
 *
 * FLUJO:
 * 1. Recibe lista de provincias
 * 2. Crea un thread por provincia
 * 3. Cada thread ejecuta: SincronizacionService.sincronizarProvincia()
 * 4. Espera resultados con timeout
 * 5. Consolida y retorna
 */
@Service
@Slf4j
public class BusquedaMultiProvincialService {

    // ========================================================================
    // DEPENDENCIAS
    // ========================================================================

    /**
     * Servicio coordinador que sincroniza UNA provincia completa.
     * Este servicio contiene TODA la lógica de sincronización.
     */
    @Autowired
    private SincronizacionService sincronizacionService;

    // ========================================================================
    // CONFIGURACIÓN
    // ========================================================================

    /**
     * Días hacia atrás para consultar por defecto.
     */
    @Value("${sincronizacion.rendiciones.dias-atras:7}")
    private int diasAtras;

    /**
     * Timeout para cada consulta provincial en segundos.
     */
    @Value("${sincronizacion.multiprovincial.timeout-segundos:60}")
    private int timeoutSegundos;

    /**
     * Tamaño del pool de threads para consultas concurrentes.
     */
    @Value("${sincronizacion.multiprovincial.pool-size:5}")
    private int poolSize;

    /**
     * Pool de threads para ejecución concurrente.
     * Lazy initialization para usar la configuración inyectada.
     */
    private ExecutorService executorService;

    // ========================================================================
    // MÉTODOS PÚBLICOS - COORDINACIÓN MULTIPROVINCIAL
    // ========================================================================

    /**
     * Sincroniza múltiples provincias en paralelo (última semana).
     *
     * RESPONSABILIDAD:
     * Solo coordina la ejecución paralela. La lógica de sincronización
     * está delegada a SincronizacionService.
     *
     * @param codigosProvincias Lista de códigos de provincia
     * @return Resultado consolidado con estadísticas
     */
    public ResultadoBusquedaMultiprovincial sincronizarProvinciasEnParalelo(
            List<String> codigosProvincias) {

        // Calcular rango de fechas (últimos N días)
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);

        return sincronizarProvinciasEnParalelo(codigosProvincias, fechaDesde, fechaHasta);
    }

    /**
     * Sincroniza múltiples provincias en paralelo para un rango de fechas.
     *
     * COORDINACIÓN PURA:
     * 1. Validar parámetros
     * 2. Inicializar ExecutorService
     * 3. Lanzar una tarea por provincia
     * 4. Cada tarea ejecuta: SincronizacionService.sincronizarProvincia()
     * 5. Esperar resultados con timeout
     * 6. Consolidar y retornar
     *
     * @param codigosProvincias Lista de provincias a sincronizar
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @return Resultado consolidado
     */
    public ResultadoBusquedaMultiprovincial sincronizarProvinciasEnParalelo(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN MULTIPROVINCIAL CONCURRENTE                  ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincias:   {} ({})                                       ║",
                codigosProvincias.size(), String.join(", ", codigosProvincias));
        log.info("║  Rango:        {} → {}                                       ║",
                fechaDesde, fechaHasta);
        log.info("║  Threads:      {} paralelos                                  ║", poolSize);
        log.info("║  Sincroniza:   RENDICIONES + CONTRACARGOS                   ║");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // PASO 1: Validar parámetros
        validarParametros(codigosProvincias, fechaDesde, fechaHasta);

        // PASO 2: Inicializar pool de threads
        inicializarExecutorService();

        long tiempoInicio = System.currentTimeMillis();

        try {
            // PASO 3: Lanzar tareas concurrentes
            Map<String, Future<ResultadoBusquedaProvincia>> futures =
                    lanzarTareasConcurrentes(codigosProvincias, fechaDesde, fechaHasta);

            // PASO 4: Consolidar resultados
            ResultadoBusquedaMultiprovincial resultado = consolidarResultados(futures);

            long duracion = System.currentTimeMillis() - tiempoInicio;

            // PASO 5: Log de resumen
            logResumen(resultado, codigosProvincias.size(), duracion);

            return resultado;

        } catch (Exception e) {
            log.error("Error crítico en sincronización multiprovincial", e);
            throw new BusquedaMultiProvincialException(
                    "Error al sincronizar múltiples provincias", e);
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - GESTIÓN DE CONCURRENCIA
    // ========================================================================

    /**
     * Inicializa el ExecutorService si aún no existe.
     * Lazy initialization para usar la configuración inyectada.
     */
    private synchronized void inicializarExecutorService() {
        if (executorService == null || executorService.isShutdown()) {
            log.debug("Inicializando ExecutorService con {} threads", poolSize);
            executorService = Executors.newFixedThreadPool(
                    poolSize,
                    new ThreadFactory() {
                        private int contador = 0;

                        @Override
                        public Thread newThread(Runnable runnable) {
                            Thread thread = new Thread(runnable);
                            thread.setName("SincroMulti-" + (++contador));
                            thread.setDaemon(false);
                            return thread;
                        }
                    }
            );
        }
    }

    /**
     * Lanza tareas concurrentes para cada provincia.
     *
     * DELEGACIÓN TOTAL:
     * Cada tarea simplemente llama a:
     * SincronizacionService.sincronizarProvincia()
     *
     * Ese servicio se encarga de TODO:
     * - Consultar rendiciones (vía EpagosClientService)
     * - Procesar rendiciones (vía RendicionService)
     * - Consultar contracargos (vía EpagosClientService)
     * - Procesar contracargos (vía ContracargoService)
     *
     * @param codigosProvincias Lista de provincias
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @return Mapa de Futures con resultados pendientes
     */
    private Map<String, Future<ResultadoBusquedaProvincia>> lanzarTareasConcurrentes(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        Map<String, Future<ResultadoBusquedaProvincia>> futures = new ConcurrentHashMap<>();

        // Calcular días atrás para cada provincia
        final int diasParaConsultar = (int) java.time.temporal.ChronoUnit.DAYS.between(
                fechaDesde,
                fechaHasta
        );

        for (String codigoProvincia : codigosProvincias) {
            log.debug("→ Lanzando tarea para provincia: {}", codigoProvincia);

            // Crear y lanzar tarea
            // DELEGACIÓN TOTAL a SincronizacionService
            final String provincia = codigoProvincia; // Efectivamente final para lambda

            Future<ResultadoBusquedaProvincia> future = executorService.submit(
                    new Callable<ResultadoBusquedaProvincia>() {
                        @Override
                        public ResultadoBusquedaProvincia call() {
                            return procesarProvincia(provincia, diasParaConsultar);
                        }
                    }
            );

            futures.put(codigoProvincia, future);
        }

        log.info("║ {} tareas lanzadas en paralelo", codigosProvincias.size());

        return futures;
    }

    /**
     * Procesa una provincia en un thread separado.
     *
     * DELEGACIÓN PURA + ADAPTER:
     * 1. Llama a SincronizacionService.sincronizarProvincia()
     * 2. Convierte ResultadoSincronizacion → ResultadoBusquedaProvincia
     * 3. Retorna resultado en formato esperado por el consolidador
     *
     * @param codigoProvincia Código de la provincia
     * @param diasAtras Días hacia atrás para consultar
     * @return Resultado del procesamiento en formato ResultadoBusquedaProvincia
     */
    private ResultadoBusquedaProvincia procesarProvincia(
            String codigoProvincia,
            int diasAtras) {

        log.info("║ → Procesando provincia: {}", codigoProvincia);

        ResultadoBusquedaProvincia resultado = new ResultadoBusquedaProvincia(codigoProvincia);

        try {
            // ================================================================
            // DELEGACIÓN TOTAL a SincronizacionService
            // ================================================================
            // Este servicio se encarga de:
            // 1. Consultar rendiciones (EpagosClientService)
            // 2. Procesar rendiciones (RendicionService)
            // 3. Consultar contracargos (EpagosClientService)
            // 4. Procesar contracargos (ContracargoService)

            ResultadoSincronizacion resultadoSync =
                    sincronizacionService.sincronizarProvincia(codigoProvincia, diasAtras);

            // ================================================================
            // ADAPTER PATTERN: Convertir ResultadoSincronizacion → ResultadoBusquedaProvincia
            // ================================================================

            // Mapear datos de rendiciones
            resultado.setRendicionesEncontradas(resultadoSync.getRendicionesObtenidas());
            resultado.setInfraccionesActualizadas(resultadoSync.getCobranzasActualizadas());

            // Mapear datos de contracargos
            resultado.setContracargosEncontrados(resultadoSync.getContracargosObtenidos());
            resultado.setContracargosProcesados(resultadoSync.getContracargosProcesados());

            // Mapear estado general
            resultado.setExitoso(resultadoSync.isExitoso());

            // Mapear mensaje de error si existe
            if (!resultadoSync.isExitoso() && resultadoSync.getMensajeError() != null) {
                resultado.setMensajeError(resultadoSync.getMensajeError());
            }

            // Log de éxito
            log.info("║ ✓ Provincia {} completada: {} rendiciones obtenidas, {} cobranzas actualizadas, {} contracargos",
                    codigoProvincia,
                    resultadoSync.getRendicionesObtenidas(),
                    resultadoSync.getCobranzasActualizadas(),
                    resultadoSync.getContracargosProcesados());

        } catch (IllegalArgumentException e) {
            // Error de validación de parámetros
            log.warn("║ ⚠ Validación fallida en provincia {}: {}",
                    codigoProvincia, e.getMessage());
            resultado.setExitoso(false);
            resultado.setMensajeError("Parámetros inválidos: " + e.getMessage());

        } catch (Exception e) {
            // Error genérico durante sincronización
            log.error("║ ✗ Error en provincia {}: {}", codigoProvincia, e.getMessage());
            resultado.setExitoso(false);
            resultado.setMensajeError("Error: " + e.getMessage());
        }

        return resultado;
    }

    /**
     * Consolida los resultados de todas las tareas concurrentes.
     *
     * Espera a que cada Future termine (con timeout) y recopila estadísticas.
     * Maneja timeouts, interrupciones y errores de ejecución.
     *
     * @param futures Mapa con los Futures de cada provincia
     * @return Resultado consolidado de todas las provincias
     */
    private ResultadoBusquedaMultiprovincial consolidarResultados(
            Map<String, Future<ResultadoBusquedaProvincia>> futures) {

        ResultadoBusquedaMultiprovincial consolidado = new ResultadoBusquedaMultiprovincial();

        for (Map.Entry<String, Future<ResultadoBusquedaProvincia>> entry : futures.entrySet()) {
            String provincia = entry.getKey();
            Future<ResultadoBusquedaProvincia> future = entry.getValue();

            try {
                // Esperar resultado con timeout
                ResultadoBusquedaProvincia resultado = future.get(
                        timeoutSegundos,
                        TimeUnit.SECONDS
                );

                consolidado.agregarResultadoProvincia(resultado);

            } catch (TimeoutException e) {
                // Timeout: la provincia tardó más del tiempo permitido
                log.warn("║ ⏱ Timeout en provincia {} (>{} seg)", provincia, timeoutSegundos);

                ResultadoBusquedaProvincia resultadoTimeout =
                        new ResultadoBusquedaProvincia(provincia);
                resultadoTimeout.setExitoso(false);
                resultadoTimeout.setMensajeError("Timeout: excedió " + timeoutSegundos + " segundos");

                consolidado.agregarResultadoProvincia(resultadoTimeout);

            } catch (InterruptedException e) {
                // Thread fue interrumpido
                Thread.currentThread().interrupt();
                log.error("║ ⚠ Interrupción en provincia {}", provincia);

                ResultadoBusquedaProvincia resultadoInterrumpido =
                        new ResultadoBusquedaProvincia(provincia);
                resultadoInterrumpido.setExitoso(false);
                resultadoInterrumpido.setMensajeError("Proceso interrumpido");

                consolidado.agregarResultadoProvincia(resultadoInterrumpido);

            } catch (ExecutionException e) {
                // Error durante la ejecución de la tarea
                log.error("║ ✗ Error de ejecución en provincia {}: {}",
                        provincia, e.getCause().getMessage());

                ResultadoBusquedaProvincia resultadoError =
                        new ResultadoBusquedaProvincia(provincia);
                resultadoError.setExitoso(false);
                resultadoError.setMensajeError(e.getCause().getMessage());

                consolidado.agregarResultadoProvincia(resultadoError);
            }
        }

        return consolidado;
    }

    // ========================================================================
    // VALIDACIONES
    // ========================================================================

    /**
     * Valida los parámetros de entrada para sincronización multiprovincial.
     *
     * @param codigosProvincias Lista de códigos de provincia
     * @param fechaDesde Fecha inicial del rango
     * @param fechaHasta Fecha final del rango
     * @throws IllegalArgumentException si algún parámetro es inválido
     */
    private void validarParametros(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        if (codigosProvincias == null || codigosProvincias.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe especificar al menos una provincia");
        }

        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException(
                    "Las fechas no pueden ser nulas");
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a fecha hasta");
        }

        // Validar que no se excedan los 90 días (límite de e-Pagos)
        long diasDiferencia = java.time.temporal.ChronoUnit.DAYS.between(
                fechaDesde,
                fechaHasta
        );

        if (diasDiferencia > 90) {
            throw new IllegalArgumentException(
                    "El rango de fechas no puede superar los 90 días (límite de e-Pagos)");
        }
    }

    // ========================================================================
    // LOGGING Y REPORTING
    // ========================================================================

    /**
     * Log de resumen final de sincronización multiprovincial.
     *
     * @param resultado Resultado consolidado
     * @param totalProvincias Total de provincias procesadas
     * @param duracionMs Duración en milisegundos
     */
    private void logResumen(
            ResultadoBusquedaMultiprovincial resultado,
            int totalProvincias,
            long duracionMs) {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN MULTIPROVINCIAL COMPLETADA                    ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincias totales:   {}                                     ║",
                totalProvincias);
        log.info("║  Provincias exitosas:  {}                                     ║",
                resultado.getProvinciasExitosas());
        log.info("║  Provincias fallidas:  {}                                     ║",
                totalProvincias - resultado.getProvinciasExitosas());
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Rendiciones procesadas: {}                                   ║",
                resultado.getTotalInfraccionesProcesadas());
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Duración total:       {} ms ({} seg)                         ║",
                duracionMs, duracionMs / 1000);
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // Detalle por provincia (solo en modo debug)
        if (log.isDebugEnabled()) {
            log.debug("Detalle por provincia:");
            resultado.getResultadosPorProvincia().forEach(prov -> {
                if (prov.isExitoso()) {
                    log.debug("  ✓ {} → {} rendiciones, {} cobranzas, {} contracargos",
                            prov.getCodigoProvincia(),
                            prov.getRendicionesEncontradas(),
                            prov.getInfraccionesActualizadas(),
                            prov.getContracargosProcesados());
                } else {
                    log.debug("  ✗ {} → ERROR: {}",
                            prov.getCodigoProvincia(),
                            prov.getMensajeError());
                }
            });
        }
    }

    // ========================================================================
    // LIFECYCLE - Limpieza de recursos
    // ========================================================================

    /**
     * Método de ciclo de vida para cerrar el ExecutorService correctamente.
     *
     * Se ejecuta automáticamente cuando Spring destruye el bean.
     * Asegura que todos los threads se cierren correctamente.
     */
    @PreDestroy
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Apagando ExecutorService multiprovincial...");

            // Solicitar apagado graceful
            executorService.shutdown();

            try {
                // Esperar hasta 60 segundos para que terminen las tareas
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    // Si no terminaron, forzar apagado
                    log.warn("Forzando apagado del ExecutorService (timeout)");
                    executorService.shutdownNow();

                    // Esperar un poco más para el apagado forzado
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("ExecutorService no terminó después del apagado forzado");
                    }
                }
            } catch (InterruptedException e) {
                log.error("Interrupción durante apagado del ExecutorService");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            log.info("ExecutorService apagado correctamente");
        }
    }
}