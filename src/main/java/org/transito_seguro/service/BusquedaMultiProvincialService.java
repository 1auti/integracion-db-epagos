package org.transito_seguro.service;


import lombok.extern.slf4j.Slf4j;
import org.transito_seguro.dto.contracargos.ContracargoDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.model.ResultadoBusquedaMultiprovincial;
import org.transito_seguro.model.ResultadoBusquedaProvincia;
import org.transito_seguro.exception.BusquedaMultiProvincialException;
import org.transito_seguro.exception.EpagosConnectionException;
import org.transito_seguro.exception.EpagosTimeoutException;
import org.transito_seguro.util.FechaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio para búsqueda y sincronización multiprovincial de infracciones
 * con el sistema e-pagos.
 *
 * Este servicio implementa el patrón Strategy para permitir diferentes
 * estrategias de búsqueda según la provincia y el patrón Template Method
 * para estandarizar el flujo de consulta.
 *
 * Responsabilidades:
 * - Consultar infracciones pendientes por provincia en rangos de fechas
 * - Sincronizar datos de rendiciones y contracargos con e-pagos
 * - Gestionar consultas concurrentes a múltiples provincias
 * - Consolidar resultados y actualizar estado de infracciones
 */
@Service
@Slf4j
public class BusquedaMultiProvincialService {


    // Rango de días para búsqueda retrospectiva (1 semana por defecto)
    private static final int DIAS_BUSQUEDA_ATRAS = 7;

    // Timeout para consultas a provincias (segundos)
    private static final int TIMEOUT_CONSULTA_SEGUNDOS = 30;

    // Tamaño del pool de threads para consultas concurrentes
    private static final int POOL_SIZE = 5;

    @Autowired
    private EpagosClientService epagosClientService;

    @Autowired
    private RendicionService rendicionService;

    @Autowired
    private ContracargoService contracargoService;

    @Autowired
    private ActualizacionService actualizacionService;

    // Pool de threads para consultas concurrentes
    private final ExecutorService executorService;

    /**
     * Constructor que inicializa el pool de threads para consultas concurrentes.
     */
    public BusquedaMultiProvincialService() {
        this.executorService = Executors.newFixedThreadPool(POOL_SIZE);
    }

    /**
     * Realiza búsqueda multiprovincial de infracciones actualizadas en e-pagos
     * en el rango de fechas configurado (última semana).
     *
     * @param codigosProvincias Lista de códigos de provincias a consultar
     * @return Resultado consolidado de la búsqueda multiprovincial
     */
    @Transactional
    public ResultadoBusquedaMultiprovincial buscarActualizacionesMultiprovincial(
            List<String> codigosProvincias) {

        log.info("Iniciando búsqueda multiprovincial para {} provincias",
                codigosProvincias.size());

        // Calcular rango de fechas (última semana)
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(DIAS_BUSQUEDA_ATRAS);

        return buscarActualizacionesMultiprovincial(codigosProvincias, fechaDesde, fechaHasta);
    }

    /**
     * Realiza búsqueda multiprovincial con rango de fechas personalizado.
     *
     * Este método coordina la consulta concurrente a múltiples provincias,
     * consolida los resultados y actualiza el estado de las infracciones.
     *
     * @param codigosProvincias Lista de códigos de provincias a consultar
     * @param fechaDesde        Fecha inicial del rango de búsqueda
     * @param fechaHasta        Fecha final del rango de búsqueda
     * @return Resultado consolidado con estadísticas por provincia
     */
    @Transactional
    public ResultadoBusquedaMultiprovincial buscarActualizacionesMultiprovincial(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.info("Búsqueda multiprovincial en rango: {} a {} para provincias: {}",
                fechaDesde, fechaHasta, codigosProvincias);

        // Validar parámetros de entrada
        validarParametrosBusqueda(codigosProvincias, fechaDesde, fechaHasta);


        try {
            // Ejecutar búsquedas concurrentes por provincia
            Map<String, Future<ResultadoBusquedaProvincia>> futuresPorProvincia =
                    ejecutarBusquedasConcurrentes(codigosProvincias, fechaDesde, fechaHasta);

            // Consolidar resultados de todas las provincias
            ResultadoBusquedaMultiprovincial resultado =
                    consolidarResultados(futuresPorProvincia);

            log.info("Búsqueda multiprovincial completada. Total procesado: {} infracciones",
                    resultado.getTotalInfraccionesProcesadas());

            return resultado;

        } catch (Exception e) {
            log.error("Error en búsqueda multiprovincial", e);
            throw new BusquedaMultiProvincialException(
                    "Error al ejecutar búsqueda multiprovincial", e);
        }
    }

    /**
     * Ejecuta búsquedas concurrentes para cada provincia utilizando el pool de threads.
     *
     * @param codigosProvincias Códigos de provincias a consultar
     * @param fechaDesde        Fecha inicial del rango
     * @param fechaHasta        Fecha final del rango
     * @return Mapa de futures con los resultados por provincia
     */
    private Map<String, Future<ResultadoBusquedaProvincia>> ejecutarBusquedasConcurrentes(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        Map<String, Future<ResultadoBusquedaProvincia>> futures = new HashMap<>();

        // Crear una tarea de búsqueda por cada provincia
        for (String codigoProvincia : codigosProvincias) {
            Future<ResultadoBusquedaProvincia> future = executorService.submit(() ->
                    buscarEnProvincia(codigoProvincia, fechaDesde, fechaHasta)
            );
            futures.put(codigoProvincia, future);
        }

        return futures;
    }

    /**
     * Realiza la búsqueda de infracciones actualizadas para una provincia específica.
     * <p>
     * Este método implementa el patrón Template Method, definiendo el flujo
     * estándar de búsqueda que incluye:
     * 1. Consulta de rendiciones en e-pagos
     * 2. Consulta de contracargos en e-pagos
     * 3. Actualización de infracciones locales
     *
     * @param codigoProvincia Código de la provincia a consultar
     * @param fechaDesde      Fecha inicial del rango
     * @param fechaHasta      Fecha final del rango
     * @return Resultado de la búsqueda para la provincia
     */
    private ResultadoBusquedaProvincia buscarEnProvincia(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.debug("Iniciando búsqueda en provincia: {}", codigoProvincia);

        ResultadoBusquedaProvincia resultado = new ResultadoBusquedaProvincia(codigoProvincia);

        try {
            // 1. Obtener rendiciones actualizadas desde e-pagos
            List<RendicionDTO> rendiciones =
                    obtenerRendicionesActualizadas(codigoProvincia, fechaDesde, fechaHasta);
            resultado.setRendicionesEncontradas(rendiciones.size());

            // 2. Obtener contracargos desde e-pagos
            List<ContracargoDTO> contracargos =
                    obtenerContracargosActualizados(codigoProvincia, fechaDesde, fechaHasta);
            resultado.setContracargosEncontrados(contracargos.size());

            // 3. Procesar y actualizar infracciones con los datos obtenidos
            int infraccionesActualizadas =
                    procesarYActualizarInfracciones(codigoProvincia, rendiciones, contracargos);
            resultado.setInfraccionesActualizadas(infraccionesActualizadas);

            resultado.setExitoso(true);
            log.info("Búsqueda exitosa en provincia {}: {} rendiciones, {} contracargos, {} actualizadas",
                    codigoProvincia, rendiciones.size(), contracargos.size(), infraccionesActualizadas);

        } catch (EpagosTimeoutException e) {
            log.warn("Timeout en provincia {}: {}", codigoProvincia, e.getMessage());
            resultado.setExitoso(false);
            resultado.setMensajeError("Timeout en consulta a e-pagos");

        } catch (EpagosConnectionException e) {
            log.error("Error de conexión en provincia {}: {}", codigoProvincia, e.getMessage());
            resultado.setExitoso(false);
            resultado.setMensajeError("Error de conexión con e-pagos");

        } catch (Exception e) {
            log.error("Error inesperado en provincia " + codigoProvincia, e);
            resultado.setExitoso(false);
            resultado.setMensajeError("Error inesperado: " + e.getMessage());
        }

        return resultado;
    }

    /**
     * Obtiene las rendiciones actualizadas desde e-pagos para una provincia
     * en el rango de fechas especificado.
     * <p>
     * Utiliza el método 'obtener_rendiciones' de la API de e-pagos.
     *
     * @param codigoProvincia Código de la provincia
     * @param fechaDesde      Fecha inicial del rango
     * @param fechaHasta      Fecha final del rango
     * @return Lista de rendiciones obtenidas
     */
    private List<RendicionDTO> obtenerRendicionesActualizadas(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.debug("Consultando rendiciones para provincia {} desde {} hasta {}",
                codigoProvincia, fechaDesde, fechaHasta);

        try {
            // Consultar rendiciones vía servicio de e-pagos
            // Nota: El servicio EpagosClientService encapsula la comunicación SOAP
            List<RendicionDTO> rendiciones = epagosClientService.obtenerRendiciones(
                    codigoProvincia,
                    FechaUtil.convertirADate(fechaDesde),
                    FechaUtil.convertirADate(fechaHasta)
            );

            log.debug("Obtenidas {} rendiciones para provincia {}",
                    rendiciones.size(), codigoProvincia);

            return rendiciones;

        } catch (Exception e) {
            log.error("Error al obtener rendiciones de provincia " + codigoProvincia, e);
            throw new EpagosConnectionException(
                    "Error al consultar rendiciones para provincia " + codigoProvincia, e);
        }
    }

    /**
     * Obtiene los contracargos actualizados desde e-pagos para una provincia
     * en el rango de fechas especificado.
     * <p>
     * Utiliza el método 'obtener_contracargos' de la API de e-pagos.
     *
     * @param codigoProvincia Código de la provincia
     * @param fechaDesde      Fecha inicial del rango
     * @param fechaHasta      Fecha final del rango
     * @return Lista de contracargos obtenidos
     */
    private List<ContracargoDTO> obtenerContracargosActualizados(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        log.debug("Consultando contracargos para provincia {} desde {} hasta {}",
                codigoProvincia, fechaDesde, fechaHasta);

        try {
            // Consultar contracargos vía servicio de e-pagos
            List<ContracargoDTO> contracargos = epagosClientService.obtenerContracargos(
                    codigoProvincia,
                    FechaUtil.convertirADate(fechaDesde),
                    FechaUtil.convertirADate(fechaHasta)
            );

            log.debug("Obtenidos {} contracargos para provincia {}",
                    contracargos.size(), codigoProvincia);

            return contracargos;

        } catch (Exception e) {
            log.error("Error al obtener contracargos de provincia " + codigoProvincia, e);
            throw new EpagosConnectionException(
                    "Error al consultar contracargos para provincia " + codigoProvincia, e);
        }
    }

    /**
     * Procesa las rendiciones y contracargos obtenidos, actualizando el estado
     * de las infracciones correspondientes en la base de datos local.
     *
     * @param codigoProvincia Código de la provincia
     * @param rendiciones     Lista de rendiciones a procesar
     * @param contracargos    Lista de contracargos a procesar
     * @return Cantidad de infracciones actualizadas exitosamente
     */
    private int procesarYActualizarInfracciones(
            String codigoProvincia,
            List<RendicionDTO> rendiciones,
            List<ContracargoDTO> contracargos) {

        int totalActualizadas = 0;

        // Procesar rendiciones
        totalActualizadas += rendicionService.procesarRendiciones(codigoProvincia, rendiciones);

        // Procesar contracargos
        totalActualizadas += contracargoService.procesarContracargos(codigoProvincia, contracargos);

        return totalActualizadas;
    }

    /**
     * Consolida los resultados de todas las búsquedas provinciales.
     * <p>
     * Espera a que todas las tareas concurrentes finalicen (con timeout)
     * y recopila las estadísticas consolidadas.
     *
     * @param futuresPorProvincia Mapa de futures con resultados por provincia
     * @return Resultado consolidado de todas las provincias
     */
    private ResultadoBusquedaMultiprovincial consolidarResultados(
            Map<String, Future<ResultadoBusquedaProvincia>> futuresPorProvincia) {

        ResultadoBusquedaMultiprovincial resultadoConsolidado =
                new ResultadoBusquedaMultiprovincial();

        for (Map.Entry<String, Future<ResultadoBusquedaProvincia>> entry :
                futuresPorProvincia.entrySet()) {

            String codigoProvincia = entry.getKey();
            Future<ResultadoBusquedaProvincia> future = entry.getValue();

            try {
                // Esperar resultado con timeout
                ResultadoBusquedaProvincia resultado =
                        future.get(TIMEOUT_CONSULTA_SEGUNDOS, TimeUnit.SECONDS);

                resultadoConsolidado.agregarResultadoProvincia(resultado);

            } catch (TimeoutException e) {
                log.warn("Timeout esperando resultado de provincia {}", codigoProvincia);
                ResultadoBusquedaProvincia resultadoTimeout =
                        new ResultadoBusquedaProvincia(codigoProvincia);
                resultadoTimeout.setExitoso(false);
                resultadoTimeout.setMensajeError("Timeout en consulta");
                resultadoConsolidado.agregarResultadoProvincia(resultadoTimeout);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupción esperando resultado de provincia {}", codigoProvincia);

            } catch (ExecutionException e) {
                log.error("Error ejecutando búsqueda en provincia " + codigoProvincia, e);
                ResultadoBusquedaProvincia resultadoError =
                        new ResultadoBusquedaProvincia(codigoProvincia);
                resultadoError.setExitoso(false);
                resultadoError.setMensajeError("Error en ejecución: " + e.getMessage());
                resultadoConsolidado.agregarResultadoProvincia(resultadoError);
            }
        }

        return resultadoConsolidado;
    }

    /**
     * Valida los parámetros de entrada para la búsqueda.
     *
     * @throws IllegalArgumentException si los parámetros no son válidos
     */
    private void validarParametrosBusqueda(
            List<String> codigosProvincias,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {

        if (codigosProvincias == null || codigosProvincias.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe especificar al menos una provincia para búsqueda");
        }

        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException(
                    "Las fechas de búsqueda no pueden ser nulas");
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta");
        }

        if (fechaHasta.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha hasta no puede ser futura");
        }
    }

    /**
     * Libera los recursos del pool de threads al destruir el servicio.
     */
    public void destroy() {
        log.info("Liberando recursos del ExecutorService");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }



}

