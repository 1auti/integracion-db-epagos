package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.rendiciones.DetalleRendicionDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.entity.Cobranza;
import org.transito_seguro.entity.ConcesionConfiguracionConvenio;
import org.transito_seguro.entity.RendicionPercepcion;
import org.transito_seguro.model.ResultadoConciliacion;
import org.transito_seguro.repository.CobranzaRepository;
import org.transito_seguro.repository.ConcesionConfiguracionConvenioRepository;
import org.transito_seguro.repository.RendicionPercepcionRepository;
import org.transito_seguro.util.FechaUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio especializado en gestión de percepciones de rendiciones desde e-Pagos.
 *
 * PROPÓSITO:
 * Registrar y conciliar cada transacción individual que e-Pagos reporta
 * en sus rendiciones, verificando correspondencia con pagos recibidos (COBRANZA).
 *
 * CONTEXTO DE NEGOCIO:
 * Cuando e-Pagos procesa pagos por el organismo, periódicamente genera "rendiciones"
 * que agrupan múltiples transacciones. Cada transacción individual es una "percepción".
 *
 * Este servicio:
 * 1. Registra cada percepción como evidencia de lo que e-Pagos dice haber cobrado
 * 2. Concilia percepciones con cobranzas (pagos reales recibidos)
 * 3. Identifica discrepancias: huérfanas, duplicados, diferencias de monto
 * 4. Mantiene trazabilidad completa para auditorías
 *
 * FLUJO DE PROCESAMIENTO:
 *
 *   Rendición e-Pagos (1 rendición con N detalles)
 *          ↓
 *   Por cada Detalle:
 *          ↓
 *   1. Crear RendicionPercepcion (estado: PERCIBIDA)
 *   2. Buscar Cobranza correspondiente por numero_transaccion
 *          ↓
 *   SI EXISTE Cobranza:
 *      - Estado: CONCILIADA
 *      - Vincular ID de cobranza
 *      - Verificar montos coincidan
 *          ↓
 *   SI NO EXISTE Cobranza:
 *      - Estado: HUERFANA
 *      - Requiere investigación
 *
 * PATRÓN DE DISEÑO:
 * - Strategy Pattern: Diferentes estrategias de conciliación (por monto, por fecha, etc.)
 * - Repository Pattern: Abstracción de persistencia
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
@Service
@Slf4j
public class RendicionPercepcionService {

    // ========================================================================
    // INYECCIÓN DE DEPENDENCIAS
    // ========================================================================

    @Autowired
    private RendicionPercepcionRepository percepcionRepository;

    @Autowired
    private CobranzaRepository cobranzaRepository;

    @Autowired
    private ConcesionConfiguracionConvenioRepository concesionConfiguracionConvenioRepository;

    // ========================================================================
    // CONSTANTES DE ESTADOS
    // ========================================================================

    /**
     * Estados de conciliación posibles.
     */
    private static final String ESTADO_PERCIBIDA = "PERCIBIDA";
    private static final String ESTADO_CONCILIADA = "CONCILIADA";
    private static final String ESTADO_HUERFANA = "HUERFANA";
    private static final String ESTADO_DUPLICADA = "DUPLICADA";
    private static final String ESTADO_ERROR = "ERROR";

    /**
     * Tolerancia para comparación de montos (centavos).
     * Permite diferencias mínimas por redondeos.
     */
    private static final double TOLERANCIA_MONTO = 0.01;

    // ========================================================================
    // MÉTODOS PÚBLICOS - PROCESAMIENTO PRINCIPAL
    // ========================================================================

    /**
     * Procesa y concilia una lista de rendiciones desde e-Pagos.
     *
     * FLUJO COMPLETO:
     * 1. Validar rendiciones
     * 2. Extraer todas las transacciones
     * 3. Buscar cobranzas en lote (optimización)
     * 4. Crear percepciones y conciliar
     * 5. Guardar todo en batch
     * 6. Retornar métricas del proceso
     *
     * IMPORTANTE: Este método es transaccional, si falla algo se hace rollback completo.
     *
     * @param rendiciones Lista de rendiciones desde e-Pagos
     * @param codigoProvincia Código de provincia que procesa
     * @return Resultado con estadísticas de conciliación
     */
    @Transactional
    public ResultadoConciliacion procesarYConciliarRendiciones(
            List<RendicionDTO> rendiciones,
            String codigoProvincia) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("INICIO: Procesamiento de percepciones para provincia: {}", codigoProvincia);
        log.info("Rendiciones a procesar: {}", rendiciones != null ? rendiciones.size() : 0);
        log.info("═══════════════════════════════════════════════════════════");

        // Inicializar resultado
        ResultadoConciliacion resultado = new ResultadoConciliacion();
        resultado.setFechaInicio(new Date());

        // Validación básica
        if (rendiciones == null || rendiciones.isEmpty()) {
            log.info("No hay rendiciones para procesar");
            resultado.setFechaFin(new Date());
            return resultado;
        }

        try {
            // PASO 1: Extraer todos los números de transacción
            Set<String> numerosTransaccion = extraerNumerosTransaccion(rendiciones);
            log.info("Total de transacciones únicas detectadas: {}", numerosTransaccion.size());

            // PASO 2: Buscar TODAS las cobranzas en UN SOLO query (optimización crucial)
            Map<String, Cobranza> cobranzasPorNumero = buscarCobranzasEnLote(numerosTransaccion);
            log.info("Cobranzas encontradas en BD: {} de {}",
                    cobranzasPorNumero.size(), numerosTransaccion.size());

            // PASO 3: Procesar cada rendición y crear percepciones
            List<RendicionPercepcion> percepcionesParaGuardar = new ArrayList<>();

            for (RendicionDTO rendicion : rendiciones) {
                try {
                    // Validar rendición
                    if (!esRendicionValida(rendicion)) {
                        log.warn("Rendición {} no válida, saltando", rendicion.getNumero());
                        resultado.incrementarErrores();
                        continue;
                    }

                    // Procesar detalles de la rendición
                    List<RendicionPercepcion> percepciones = procesarDetallesRendicion(
                            rendicion,
                            cobranzasPorNumero,
                            codigoProvincia,
                            resultado
                    );

                    percepcionesParaGuardar.addAll(percepciones);

                } catch (Exception e) {
                    log.error("Error al procesar rendición {}: {}",
                            rendicion.getNumero(), e.getMessage(), e);
                    resultado.incrementarErrores();
                }
            }

            // PASO 4: Guardar todas las percepciones en batch
            if (!percepcionesParaGuardar.isEmpty()) {
                log.info("Guardando {} percepciones en base de datos...",
                        percepcionesParaGuardar.size());
                percepcionRepository.saveAll(percepcionesParaGuardar);
                log.info("✓ Percepciones guardadas exitosamente");
            }

            // Finalizar resultado
            resultado.setFechaFin(new Date());
            resultado.setExitoso(true);

            // Resumen de ejecución
            imprimirResumen(resultado);

            return resultado;

        } catch (Exception e) {
            log.error("ERROR CRÍTICO en procesamiento de percepciones: {}", e.getMessage(), e);
            resultado.setFechaFin(new Date());
            resultado.setExitoso(false);
            resultado.setMensajeError(e.getMessage());
            throw new RuntimeException("Error al procesar percepciones: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - PROCESAMIENTO DETALLADO
    // ========================================================================

    /**
     * Procesa los detalles de una rendición específica.
     *
     * Por cada detalle:
     * 1. Verificar si ya existe percepción (evitar duplicados)
     * 2. Buscar cobranza correspondiente
     * 3. Crear percepción con estado apropiado
     * 4. Actualizar métricas
     *
     * @param rendicion Rendición a procesar
     * @param cobranzasPorNumero Mapa de cobranzas pre-cargadas
     * @param codigoProvincia Código de provincia
     * @param resultado Objeto para acumular métricas
     * @return Lista de percepciones creadas
     */
    private List<RendicionPercepcion> procesarDetallesRendicion(
            RendicionDTO rendicion,
            Map<String, Cobranza> cobranzasPorNumero,
            String codigoProvincia,
            ResultadoConciliacion resultado) {

        ConcesionConfiguracionConvenio concesionConfiguracionConvenio = concesionConfiguracionConvenioRepository.findById(800).orElse(null);

        List<RendicionPercepcion> percepciones = new ArrayList<>();

        // Validar que tenga detalles
        if (rendicion.getDetalles() == null || rendicion.getDetalles().isEmpty()) {
            log.warn("Rendición {} sin detalles, saltando", rendicion.getNumero());
            return percepciones;
        }

        log.debug("Procesando rendición #{} con {} detalles",
                rendicion.getNumero(), rendicion.getDetalles().size());

        // Procesar cada detalle (transacción individual)
        for (DetalleRendicionDTO detalle : rendicion.getDetalles()) {
            try {
                // Validar detalle
                if (!esDetalleValido(detalle)) {
                    log.warn("Detalle inválido en rendición {}, saltando", rendicion.getNumero());
                    resultado.incrementarErrores();
                    continue;
                }

                String numeroTxn = detalle.getCodigoUnicoTransaccion().toString();

                // Verificar duplicados
                if (percepcionRepository.existePercepcion(numeroTxn, rendicion.getNumero())) {
                    log.warn("⚠ Percepción DUPLICADA detectada: txn={}, rend={}",
                            numeroTxn, rendicion.getNumero());
                    resultado.incrementarDuplicadas();
                    continue;
                }

                // Buscar cobranza correspondiente
                Cobranza cobranza = cobranzasPorNumero.get(numeroTxn);

                // Crear percepción
                RendicionPercepcion percepcion = crearPercepcion(
                        rendicion,
                        detalle,
                        cobranza,
                        concesionConfiguracionConvenio,
                        codigoProvincia
                );

                percepciones.add(percepcion);

                // Actualizar métricas según resultado
                if (percepcion.estaConciliada()) {
                    resultado.incrementarConciliadas();
                } else if (percepcion.esHuerfana()) {
                    resultado.incrementarHuerfanas();
                }

                resultado.incrementarProcesadas();

            } catch (Exception e) {
                log.error("Error al procesar detalle en rendición {}: {}",
                        rendicion.getNumero(), e.getMessage());
                resultado.incrementarErrores();
            }
        }

        return percepciones;
    }

    /**
     * Crea una percepción desde un detalle de rendición.
     *
     * LÓGICA DE CONCILIACIÓN:
     * - Si existe cobranza → CONCILIADA (vincula cobranza)
     * - Si NO existe cobranza → HUERFANA (requiere investigación)
     * - Verifica diferencias de monto si hay cobranza
     *
     * @param rendicion Rendición contenedora
     * @param detalle Detalle específico de la transacción
     * @param cobranza Cobranza encontrada (puede ser null)
     * @param codigoProvincia Código de provincia
     * @return Percepción creada con estado apropiado
     */
    private RendicionPercepcion crearPercepcion(
            RendicionDTO rendicion,
            DetalleRendicionDTO detalle,
            Cobranza cobranza,
            ConcesionConfiguracionConvenio concesionConfiguracionConvenio,
            String codigoProvincia) {

        // Construir objeto base
        RendicionPercepcion percepcion = RendicionPercepcion.builder()
                .id_concesion(concesionConfiguracionConvenio.getId_concesion())
                .id_entidad_convenio(concesionConfiguracionConvenio.getId_entidad_convenio())
                .codigo_provincia(codigoProvincia)
                .procesador("e-Pagos")
                .depositable(detalle.getDepositable())
                .numero_transaccion(detalle.getCodigoUnicoTransaccion().toString())
                .numero_operacion(detalle.getNumeroOperacion())
                .numero_rendicion(rendicion.getNumero())
                .secuencia_rendicion(rendicion.getSecuencia())
                .estado_rendicion(rendicion.getEstado())
                .convenio(rendicion.getConvenio())
                .monto_detalle(detalle.getMonto() != null ? detalle.getMonto().toString() : null)
                .monto_rendicion(rendicion.getMonto() != null ? rendicion.getMonto().doubleValue() : null)
                .monto_depositado_rendicion(rendicion.getMontoDepositado() != null ? rendicion.getMontoDepositado().doubleValue() : null)
                .monto_comision_rendicion(rendicion.getMontoComision() != null ? rendicion.getMontoComision().doubleValue() : null)
                .monto_iva_rendicion(rendicion.getMontoIVA() != null ? rendicion.getMontoIVA().doubleValue() : null)
                .rend_desde(FechaUtil.convertirADate(rendicion.getFechaDesde()))
                .rend_hasta(FechaUtil.convertirADate(rendicion.getFechaHasta()))
                .fecha_deposito(FechaUtil.convertirADate(rendicion.getFechaDeposito()))
                .fecha_estimado_deposito(FechaUtil.convertirADate(rendicion.getFechaEstimadaDeposito()))
                .estado_conciliacion(ESTADO_PERCIBIDA) // Estado inicial
                .procesada(false)
                .build();

        // CONCILIACIÓN: ¿Existe la cobranza?
        if (cobranza != null) {
            // ✅ CONCILIADA - Se encontró la cobranza
            percepcion.marcarComoConciliada(cobranza.getId());

            // Verificar diferencias de monto
            verificarDiferenciasMonto(percepcion, detalle, cobranza);

            log.debug("✓ Conciliada: txn={} → cobranza={}",
                    percepcion.getNumero_transaccion(), cobranza.getId());

        } else {
            // ⚠ HUERFANA - NO existe cobranza
            percepcion.marcarComoHuerfana();

            log.warn("⚠ Huérfana detectada: txn={}, monto={}, rend={}",
                    percepcion.getNumero_transaccion(),
                    detalle.getMonto(),
                    rendicion.getNumero());
        }

        return percepcion;
    }

    /**
     * Verifica diferencias de monto entre percepción y cobranza.
     *
     * Si hay diferencias significativas (> tolerancia), las registra en observaciones.
     * Esto puede indicar:
     * - Errores de carga
     * - Ajustes o notas de crédito
     * - Problemas de sincronización
     *
     * @param percepcion Percepción creada
     * @param detalle Detalle de e-Pagos
     * @param cobranza Cobranza correspondiente
     */
    private void verificarDiferenciasMonto(
            RendicionPercepcion percepcion,
            DetalleRendicionDTO detalle,
            Cobranza cobranza) {

        if (detalle.getMonto() == null || cobranza.getImporte_pagado() == null) {
            return;
        }

        double montoEpagos = detalle.getMonto().doubleValue();
        double montoCobranza = cobranza.getImporte_pagado();
        double diferencia = Math.abs(montoEpagos - montoCobranza);

        if (diferencia > TOLERANCIA_MONTO) {
            percepcion.setDiferencia_monto(diferencia);

            String obs = String.format(
                    "%s | ⚠ DIFERENCIA MONTO: e-Pagos=$%.2f vs Cobranza=$%.2f (dif=$%.2f)",
                    percepcion.getObservacion() != null ? percepcion.getObservacion() : "",
                    montoEpagos,
                    montoCobranza,
                    diferencia
            );
            percepcion.setObservacion(obs);

            log.warn("DIFERENCIA DE MONTO: txn={}, e-Pagos=${}, Cobranza=${}, diferencia=${}",
                    percepcion.getNumero_transaccion(), montoEpagos, montoCobranza, diferencia);
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - UTILIDADES
    // ========================================================================

    /**
     * Extrae todos los números de transacción únicos de las rendiciones.
     *
     * @param rendiciones Lista de rendiciones
     * @return Set de números de transacción únicos
     */
    private Set<String> extraerNumerosTransaccion(List<RendicionDTO> rendiciones) {
        Set<String> numeros = new HashSet<>();

        for (RendicionDTO rendicion : rendiciones) {
            if (rendicion.getDetalles() == null) continue;

            for (DetalleRendicionDTO detalle : rendicion.getDetalles()) {
                if (detalle.getCodigoUnicoTransaccion() != null) {
                    numeros.add(detalle.getCodigoUnicoTransaccion().toString());
                }
            }
        }

        return numeros;
    }

    /**
     * Busca todas las cobranzas en un solo query y las organiza en un mapa.
     *
     * OPTIMIZACIÓN CRÍTICA:
     * En lugar de hacer N queries (una por transacción), hacemos 1 sola query
     * que trae todas las cobranzas necesarias. Esto reduce drásticamente el
     * tiempo de procesamiento cuando hay muchas transacciones.
     *
     * @param numerosTransaccion Set de números de transacción a buscar
     * @return Mapa {numeroTransaccion → Cobranza}
     */
    private Map<String, Cobranza> buscarCobranzasEnLote(Set<String> numerosTransaccion) {
        if (numerosTransaccion.isEmpty()) {
            return new HashMap<>();
        }

        List<String> lista = new ArrayList<>(numerosTransaccion);
        List<Cobranza> cobranzas = cobranzaRepository.buscarPorNumerosTransaccion(lista);

        return cobranzas.stream()
                .collect(Collectors.toMap(
                        Cobranza::getNumero_transaccion,
                        cobranza -> cobranza,
                        (existente, nuevo) -> {
                            log.warn("⚠ Cobranza duplicada detectada para txn: {}",
                                    existente.getNumero_transaccion());
                            // Mantener la más reciente (mayor ID)
                            return existente.getId() > nuevo.getId() ? existente : nuevo;
                        }
                ));
    }

    /**
     * Valida que una rendición sea procesable.
     *
     * @param rendicion Rendición a validar
     * @return true si es válida
     */
    private boolean esRendicionValida(RendicionDTO rendicion) {
        return rendicion != null
                && rendicion.getNumero() != null
                && rendicion.getEstado() != null
                && !rendicion.getEstado().trim().isEmpty();
    }

    /**
     * Valida que un detalle sea procesable.
     *
     * @param detalle Detalle a validar
     * @return true si es válido
     */
    private boolean esDetalleValido(DetalleRendicionDTO detalle) {
        return detalle != null
                && detalle.getCodigoUnicoTransaccion() != null;
    }

    /**
     * Imprime resumen detallado del procesamiento.
     *
     * @param resultado Resultado del procesamiento
     */
    private void imprimirResumen(ResultadoConciliacion resultado) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("RESUMEN DE PROCESAMIENTO DE PERCEPCIONES");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("✓ Estado: {}", resultado.isExitoso() ? "EXITOSO" : "CON ERRORES");
        log.info("  Procesadas:  {}", resultado.getProcesadas());
        log.info("  Conciliadas: {} (✓)", resultado.getConciliadas());
        log.info("  Huérfanas:   {} (⚠)", resultado.getHuerfanas());
        log.info("  Duplicadas:  {} (⚠)", resultado.getDuplicadas());
        log.info("  Errores:     {} (✗)", resultado.getErrores());
        log.info("  Duración:    {} ms", resultado.getDuracionMs());
        log.info("═══════════════════════════════════════════════════════════");

        if (resultado.getHuerfanas() > 0) {
            log.warn("⚠ ALERTA: {} transacciones huérfanas requieren investigación",
                    resultado.getHuerfanas());
        }

        if (resultado.getDuplicadas() > 0) {
            log.warn("⚠ ALERTA: {} transacciones duplicadas fueron omitidas",
                    resultado.getDuplicadas());
        }
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - CONSULTAS Y REPORTES
    // ========================================================================

    /**
     * Obtiene todas las percepciones huérfanas para investigación.
     *
     * @return Lista de percepciones huérfanas
     */
    public List<RendicionPercepcion> obtenerHuerfanas() {
        log.info("Consultando percepciones huérfanas...");
        List<RendicionPercepcion> huerfanas = percepcionRepository.findHuerfanas();
        log.info("✓ Encontradas {} percepciones huérfanas", huerfanas.size());
        return huerfanas;
    }

    /**
     * Obtiene estadísticas de conciliación.
     *
     * @return Mapa con contadores por estado
     */
    public Map<String, Long> obtenerEstadisticas() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("CONCILIADAS", percepcionRepository.countByEstadoConciliacion(ESTADO_CONCILIADA));
        stats.put("HUERFANAS", percepcionRepository.countByEstadoConciliacion(ESTADO_HUERFANA));
        stats.put("DUPLICADAS", percepcionRepository.countByEstadoConciliacion(ESTADO_DUPLICADA));
        stats.put("ERRORES", percepcionRepository.countByEstadoConciliacion(ESTADO_ERROR));
        stats.put("PENDIENTES", percepcionRepository.countByEstadoConciliacion(ESTADO_PERCIBIDA));
        return stats;
    }
}
