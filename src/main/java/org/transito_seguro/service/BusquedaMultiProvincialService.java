package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.model.ResultadoBusquedaMultiprovincial;
import org.transito_seguro.model.ResultadoBusquedaProvincia;
import org.transito_seguro.exception.BusquedaMultiProvincialException;

import javax.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio de COORDINACIÓN para búsqueda multiprovincial CONCURRENTE.
 *
 * Responsabilidades:
 * 1.  Gestionar ExecutorService para concurrencia
 * 2.  Lanzar tareas paralelas por provincia
 * 3.  Esperar resultados con timeout
 * 4.  Consolidar resultados de múltiples provincias
 * 5.  Manejar errores y timeouts
 */
@Service
@Slf4j
public class BusquedaMultiProvincialService {

    // ========================================================================
    // DEPENDENCIAS - Servicios especializados
    // ========================================================================

    /**
     * Servicio de CONSULTA a e-Pagos.
     * Responsabilidad: Obtener datos desde la API externa.
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
     * Lazy initialization para usar configuración inyectada.
     */
    private ExecutorService executorService;

    // ========================================================================
    // MÉTODOS PÚBLICOS - SINCRONIZACIÓN MULTIPROVINCIAL
    // ========================================================================

    /**
     * Sincroniza múltiples provincias en paralelo (última semana).
     *
     * Este es el método principal para sincronización concurrente.
     * Procesa todas las provincias simultáneamente en threads separados.
     *
     * @param codigosProvincias Lista de códigos de provincia (ej: ["PBA", "MDA"])
     * @return Resultado consolidado con estadísticas
     * @throws BusquedaMultiProvincialException si hay error crítico
     */
    public ResultadoBusquedaMultiprovincial sincronizarProvinciasEnParalelo(
            List<String> codigosProvincias) {

        // Calcular rango de fechas
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);

        return sincronizarProvinciasEnParalelo(codigosProvincias, fechaDesde, fechaHasta);
    }

    /**
     * Sincroniza múltiples provincias en paralelo para un rango de fechas.
     *
     * Flujo de ejecución:
     * 1. Validar parámetros
     * 2. Inicializar ExecutorService
     * 3. Lanzar una tarea por provincia
     * 4. Cada tarea:
     *    a) Consulta rendiciones (ConsultaRendicionesService)
     *    b) Procesa rendiciones (RendicionService)
     * 5. Esperar a que todas terminen (con timeout)
     * 6. Consolidar y retornar resultados
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
                    runnable -> {
                        Thread thread = new Thread(runnable);
                        thread.setName("SincroMulti-" + thread.getId());
                        return thread;
                    }
            );
        }
    }

    /**
     * Lanza tareas concurrentes para cada provincia.
     *
     * Cada tarea ejecutará:
     * 1. ConsultaRendicionesService.consultarRendiciones()
     * 2. RendicionService.procesarRendiciones()
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

        for (String codigoProvincia : codigosProvincias) {
            log.debug("→ Lanzando tarea para provincia: {}", codigoProvincia);

            // Crear y lanzar tarea
            Future<ResultadoBusquedaProvincia> future = executorService.submit(() ->
                    procesarProvincia(codigoProvincia, fechaDesde, fechaHasta)
            );

            futures.put(codigoProvincia, future);
        }

        log.info("║ {} tareas lanzadas en paralelo", codigosProvincias.size());

        return futures;
    }

    /**
     * Procesa una provincia completa: consulta + procesamiento.
     *
     * Este método se ejecuta en un thread separado para cada provincia.
     *
     * Flujo:
     * 1. Consultar rendiciones desde e-Pagos (ConsultaRendicionesService)
     * 2. Procesar y actualizar cobranzas (RendicionService)
     * 3. Retornar resultado estructurado
     *
     * @param codigoProvincia Código de la provincia
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @return Resultado del procesamiento
     */
    private ResultadoBusquedaProvincia procesarProvincia(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.info("║ → Procesando provincia: {}", codigoProvincia);

        ResultadoBusquedaProvincia resultado = new ResultadoBusquedaProvincia(codigoProvincia);

        try {
            // PASO 1: Consultar rendiciones desde e-Pagos
            // DELEGACIÓN a ConsultaRendicionesService
            List<RendicionDTO> rendiciones = consultaService.consultarRendiciones(
                    codigoProvincia,
                    fechaDesde,
                    fechaHasta
            );

            resultado.setRendicionesEncontradas(rendiciones.size());

            // PASO 2: Procesar rendiciones y actualizar BD
            // DELEGACIÓN a RendicionService
            int cobranzasActualizadas = rendicionService.procesarRendiciones(
                    codigoProvincia,
                    rendiciones
            );

            resultado.setInfraccionesActualizadas(cobranzasActualizadas);
            resultado.setExitoso(true);

            log.info("║ ✓ Provincia {} completada: {} rendiciones, {} cobranzas actualizadas",
                    codigoProvincia, rendiciones.size(), cobranzasActualizadas);

        } catch (IllegalArgumentException e) {
            log.warn("║ ⚠ Validación fallida en provincia {}: {}",
                    codigoProvincia, e.getMessage());
            resultado.setExitoso(false);
            resultado.setMensajeError("Parámetros inválidos: " + e.getMessage());

        } catch (Exception e) {
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
     *
     * @param futures Mapa de Futures por provincia
     * @return Resultado consolidado
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
                log.warn("║ ⏱ Timeout en provincia {} (>{} seg)", provincia, timeoutSegundos);

                ResultadoBusquedaProvincia resultadoTimeout =
                        new ResultadoBusquedaProvincia(provincia);
                resultadoTimeout.setExitoso(false);
                resultadoTimeout.setMensajeError("Timeout: excedió " + timeoutSegundos + " segundos");

                consolidado.agregarResultadoProvincia(resultadoTimeout);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("║ ⚠ Interrupción en provincia {}", provincia);

            } catch (ExecutionException e) {
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
     * Valida los parámetros de entrada.
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
    }

    // ========================================================================
    // LOGGING Y REPORTING
    // ========================================================================

    /**
     * Log de resumen de la sincronización multiprovincial.
     */
    private void logResumen(
            ResultadoBusquedaMultiprovincial resultado,
            int totalProvincias,
            long duracionMs) {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN MULTIPROVINCIAL COMPLETADA                   ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Total cobranzas:  {}                                        ║",
                resultado.getTotalInfraccionesProcesadas());
        log.info("║  Exitosas:         {} / {}                                   ║",
                resultado.getProvinciasExitosas(), totalProvincias);
        log.info("║  Duración:         {} ms ({} seg)                            ║",
                duracionMs, duracionMs / 1000);
        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Libera recursos del ExecutorService al destruir el bean.
     */
    @PreDestroy
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Apagando ExecutorService multiprovincial...");

            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("Forzando apagado del ExecutorService (timeout)");
                    executorService.shutdownNow();
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