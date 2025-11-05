package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.rendiciones.DetalleRendicionDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.entity.Cobranza;
import org.transito_seguro.entity.ConcesionConfiguracionConvenio;
import org.transito_seguro.entity.RendicionCobro;
import org.transito_seguro.model.ResultadoProcesamiento;
import org.transito_seguro.model.ResultadoConciliacion;
import org.transito_seguro.repository.CobranzaRepository;
import org.transito_seguro.repository.ConcesionConfiguracionConvenioRepository;
import org.transito_seguro.repository.RendicionCobroRepository;
import org.transito_seguro.util.FechaUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para el procesamiento de rendiciones desde e-pagos.
 *
 * PATRÓN DE DISEÑO: TEMPLATE METHOD + CHAIN OF RESPONSIBILITY
 * - Template Method: Define el flujo estándar de procesamiento
 * - Chain of Responsibility: Cada paso procesa y pasa al siguiente
 *
 * RESPONSABILIDADES PRINCIPALES:
 * 1. Procesar rendiciones de e-Pagos
 * 2. Actualizar cobranzas existentes
 * 3. Generar percepciones para conciliación (NUEVA INTEGRACIÓN)
 * 4. Registrar auditoría del proceso
 */
@Service
@Slf4j
public class RendicionService {

    // ═══════════════════════════════════════════════════════════════════════
    // INYECCIÓN DE DEPENDENCIAS
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private CobranzaRepository cobranzaRepository;
    @Autowired
    private RendicionCobroRepository rendicionCobroRepository;
    @Autowired
    private RendicionPercepcionService percepcionService;
    @Autowired
    private ConcesionConfiguracionConvenioRepository concesionConfiguracionConvenioRepository;


    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODO PRINCIPAL - TEMPLATE METHOD PATTERN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Procesa una lista de rendiciones de e-Pagos de forma INTEGRAL.
     *
     * TEMPLATE METHOD: Define el esqueleto del algoritmo de procesamiento.
     * Los pasos específicos están delegados a métodos privados especializados.
     *
     * FLUJO COMPLETO:
     * 1. Validación inicial
     * 2. Extracción de números de transacción (optimización)
     * 3. Carga en lote de cobranzas (evita N+1 queries)
     * 4. Procesamiento y actualización de cobranzas
     * 5. Generación de percepciones para conciliación (NUEVO)
     * 6. Registro de auditoría
     * 7. Persistencia batch
     */
    @Transactional
    public int procesarRendiciones(String codigoProvincia, List<RendicionDTO> rendiciones) {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  PROCESAMIENTO INTEGRAL DE RENDICIONES - INICIO               ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincia:        {}                                         ║", codigoProvincia);
        log.info("║  Rendiciones:      {}                                         ║",
                rendiciones != null ? rendiciones.size() : 0);
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // ───────────────────────────────────────────────────────────────────
        // PASO 1: VALIDACIÓN INICIAL
        // ───────────────────────────────────────────────────────────────────
        if (!validarEntrada(rendiciones)) {
            log.info("No hay rendiciones válidas para procesar");
            return 0;
        }

        long tiempoInicio = System.currentTimeMillis();

        // ───────────────────────────────────────────────────────────────────
        // PASO 2: EXTRACCIÓN DE TRANSACCIONES (Optimización)
        // ───────────────────────────────────────────────────────────────────
        Set<String> numerosTransaccion = extraerNumerosTransaccion(rendiciones);
        log.info("→ Transacciones únicas detectadas: {}", numerosTransaccion.size());

        // ───────────────────────────────────────────────────────────────────
        // PASO 3: CARGA EN LOTE DE COBRANZAS (Evita N+1)
        // ───────────────────────────────────────────────────────────────────
        Map<String, Cobranza> cobranzasPorNumero = buscarCobranzasEnLote(numerosTransaccion);
        log.info("→ Cobranzas encontradas en BD: {} de {}",
                cobranzasPorNumero.size(), numerosTransaccion.size());

        // ───────────────────────────────────────────────────────────────────
        // PASO 4: PROCESAMIENTO DE RENDICIONES Y COBRANZAS
        // ───────────────────────────────────────────────────────────────────
        ResultadoProcesamiento resultado = procesarRendicionesYCobranzas(
                rendiciones,
                cobranzasPorNumero,
                codigoProvincia
        );

        // ───────────────────────────────────────────────────────────────────
        // PASO 5: GENERACIÓN DE PERCEPCIONES (INTEGRACIÓN NUEVA)
        // ───────────────────────────────────────────────────────────────────
        ResultadoConciliacion resultadoConciliacion = generarPercepciones(
                rendiciones,
                codigoProvincia
        );

        // ───────────────────────────────────────────────────────────────────
        // PASO 6: PERSISTENCIA EN LOTE (Batch Save)
        // ───────────────────────────────────────────────────────────────────
        int totalActualizadas = persistirCobranzas(resultado.getCobranzas());

        // ───────────────────────────────────────────────────────────────────
        // PASO 7: RESUMEN Y MÉTRICAS
        // ───────────────────────────────────────────────────────────────────
        long duracion = System.currentTimeMillis() - tiempoInicio;
        imprimirResumenFinal(
                resultado,
                resultadoConciliacion,
                totalActualizadas,
                duracion
        );

        return totalActualizadas;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS PRIVADOS - CHAIN OF RESPONSIBILITY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Valida la entrada de datos.
     *
     * PATRÓN: Guard Clause - valida y retorna temprano si hay errores.
     *
     * @param rendiciones Lista a validar
     * @return true si es válida para procesar
     */
    private boolean validarEntrada(List<RendicionDTO> rendiciones) {
        return rendiciones != null && !rendiciones.isEmpty();
    }

    /**
     * Extrae todos los números de transacción únicos de las rendiciones.
     *
     * OPTIMIZACIÓN: Recolecta todos los números ANTES de ir a BD.
     * Esto permite hacer UNA sola query en lugar de N queries.
     *
     * Complejidad: O(n*m) donde n=rendiciones, m=detalles promedio
     *
     * @param rendiciones Lista de rendiciones
     * @return Set de números únicos (sin duplicados)
     */
    private Set<String> extraerNumerosTransaccion(List<RendicionDTO> rendiciones) {
        Set<String> numeros = new HashSet<>();

        for (RendicionDTO rendicion : rendiciones) {
            if (rendicion.getDetalles() == null) {
                continue;
            }

            for (DetalleRendicionDTO detalle : rendicion.getDetalles()) {
                if (detalle.getCodigoUnicoTransaccion() != null) {
                    numeros.add(detalle.getCodigoUnicoTransaccion().toString());
                }
            }
        }

        return numeros;
    }

    /**
     * Busca todas las cobranzas en UN SOLO QUERY y las organiza en mapa.
     *
     * PATRÓN: Repository + DTO Assembler
     * OPTIMIZACIÓN CRÍTICA: Evita el problema N+1 queries.
     *
     * En lugar de:
     *   for (txn : transacciones) {
     *     cobranza = repository.findByNumero(txn); // N queries!
     *   }
     *
     * Hacemos:
     *   cobranzas = repository.findAllByNumeros(transacciones); // 1 query!
     *
     * @param numerosTransaccion Set de números a buscar
     * @return Mapa {numeroTransaccion -> Cobranza} para acceso O(1)
     */
    private Map<String, Cobranza> buscarCobranzasEnLote(Set<String> numerosTransaccion) {
        if (numerosTransaccion.isEmpty()) {
            return new HashMap<>();
        }

        // Convertir Set a List (requerido por el repositorio)
        List<String> lista = new ArrayList<>(numerosTransaccion);

        // UNA SOLA QUERY a la base de datos
        List<Cobranza> cobranzas = cobranzaRepository.buscarPorNumerosTransaccion(lista);

        // Convertir a Map para acceso rápido O(1)
        return cobranzas.stream()
                .collect(Collectors.toMap(
                        Cobranza::getNumero_transaccion,
                        cobranza -> cobranza,
                        (existente, nuevo) -> resolverDuplicado(existente, nuevo)
                ));
    }

    /**
     * Resuelve conflicto cuando hay múltiples cobranzas con mismo número.
     *
     * ESTRATEGIA: Mantener la más reciente (mayor ID).
     * Esto asume que IDs son auto-incrementales.
     *
     * @param existente Cobranza ya en el mapa
     * @param nuevo Cobranza duplicada encontrada
     * @return La cobranza que debe mantenerse
     */
    private Cobranza resolverDuplicado(Cobranza existente, Cobranza nuevo) {
        log.warn("⚠ Cobranza duplicada detectada para txn: {}",
                existente.getNumero_transaccion());

        // Mantener la más reciente
        return existente.getId() > nuevo.getId() ? existente : nuevo;
    }

    /**
     * Procesa cada rendición y actualiza las cobranzas correspondientes.
     *
     * PATRÓN: Visitor Pattern modificado
     * Visita cada rendición y aplica transformaciones.
     *
     * MANEJO DE ERRORES:
     * - Errores en una rendición NO detienen el proceso completo
     * - Se registra el error y se continúa con la siguiente
     * - Al final se reportan todas las rendiciones con error
     *
     * @param rendiciones Lista de rendiciones
     * @param cobranzasPorNumero Mapa pre-cargado de cobranzas
     * @param codigoProvincia Código de provincia
     * @return Resultado con cobranzas actualizadas y métricas
     */
    private ResultadoProcesamiento procesarRendicionesYCobranzas(
            List<RendicionDTO> rendiciones,
            Map<String, Cobranza> cobranzasPorNumero,
            String codigoProvincia) {

        ResultadoProcesamiento resultado = new ResultadoProcesamiento();
        Map<String, Integer> estadisticas = new HashMap<>();

        log.info("─────────────────────────────────────────────────────────────");
        log.info("PROCESANDO RENDICIONES Y ACTUALIZANDO COBRANZAS");
        log.info("─────────────────────────────────────────────────────────────");

        for (RendicionDTO rendicion : rendiciones) {
            try {
                // Validar rendición individual
                if (!esRendicionValida(rendicion)) {
                    log.warn("⊗ Rendición {} no válida, saltando", rendicion.getNumero());
                    continue;
                }

                // Procesar detalles de la rendición
                procesarDetallesRendicion(
                        rendicion,
                        cobranzasPorNumero,
                        resultado);

                // Registrar rendición en auditoría
                registrarRendicion(rendicion, codigoProvincia);

                // Actualizar estadísticas por estado
                String estado = rendicion.getEstado();
                estadisticas.merge(estado, 1, Integer::sum);

                log.debug("✓ Rendición {} procesada", rendicion.getNumero());

            } catch (Exception e) {
                log.error("✗ Error en rendición {}: {}",
                        rendicion.getNumero(), e.getMessage(), e);
                resultado.agregarError(String.format(
                        "Rendición %d: %s",
                        rendicion.getNumero(),
                        e.getMessage()
                ));
            }
        }

        resultado.setEstadisticas(estadisticas);

        log.info("✓ Procesamiento de rendiciones completado");
        log.info("  → Cobranzas para actualizar: {}", resultado.getCobranzas().size());
        log.info("  → Transacciones huérfanas: {}", resultado.getHuerfanas().size());

        return resultado;
    }

    /**
     * Procesa los detalles (transacciones) de una rendición específica.
     *
     * Cada detalle representa una transacción individual.
     * Se busca la cobranza correspondiente y se actualiza.
     *
     * CASOS:
     * 1. Cobranza encontrada → Se actualiza con datos de rendición
     * 2. Cobranza NO encontrada → Se marca como "huérfana" para investigación
     *
     * @param rendicion Rendición contenedora
     * @param cobranzasPorNumero Mapa de cobranzas
     * @param resultado Acumulador de resultados
     */
    private void procesarDetallesRendicion(
            RendicionDTO rendicion,
            Map<String, Cobranza> cobranzasPorNumero,
            ResultadoProcesamiento resultado) {

        if (rendicion.getDetalles() == null) {
            return;
        }

        for (DetalleRendicionDTO detalle : rendicion.getDetalles()) {
            Long codigoUnico = detalle.getCodigoUnicoTransaccion();

            // Validar código único
            if (codigoUnico == null) {
                log.warn("⊗ Detalle sin código único en rendición {}",
                        rendicion.getNumero());
                continue;
            }

            String numeroTxn = codigoUnico.toString();
            Cobranza cobranza = cobranzasPorNumero.get(numeroTxn);

            if (cobranza != null) {
                // ✅ CASO 1: Cobranza encontrada - Actualizar
                actualizarCobranzaConRendicion(cobranza, rendicion, detalle);
                resultado.agregarCobranza(cobranza);

                log.debug("  ✓ Cobranza {} actualizada con rendición {}",
                        cobranza.getId(), rendicion.getNumero());

            } else {
                // ⚠ CASO 2: Cobranza NO encontrada - Huérfana
                resultado.agregarHuerfana(numeroTxn);

                log.warn("  ⚠ Transacción HUÉRFANA: txn={}, monto={}, rend={}",
                        numeroTxn, detalle.getMonto(), rendicion.getNumero());
            }
        }
    }

    /**
     * Actualiza una cobranza existente con datos de la rendición.
     *
     * CAMPOS ACTUALIZADOS:
     * - Información de la rendición (número, secuencia, estado)
     * - Fechas (depósito, período)
     * - Montos (bruto, depositado, comisiones, IVA)
     * - Observaciones para trazabilidad
     *
     * IMPORTANTE: Esta actualización NO persiste inmediatamente.
     * Se acumula en memoria y se persiste en lote al final (batch).
     *
     * @param cobranza Cobranza a actualizar (modificación in-place)
     * @param rendicion Rendición fuente de datos
     * @param detalle Detalle específico de la transacción
     */
    private void actualizarCobranzaConRendicion(
            Cobranza cobranza,
            RendicionDTO rendicion,
            DetalleRendicionDTO detalle) {


        // Información básica de rendición
        cobranza.setRend_nro(rendicion.getNumero().intValue());
        cobranza.setRend_secuencia(rendicion.getSecuencia().intValue());
        cobranza.setRend_estado(rendicion.getEstado());
        cobranza.setRend_medio_pago(Integer.parseInt(rendicion.getConvenio()));

        // Fechas
        cobranza.setRend_fecha_deposito(FechaUtil.convertirADate(rendicion.getFechaDeposito()));
        cobranza.setRend_desde(FechaUtil.convertirADate(rendicion.getFechaDesde()));
        cobranza.setRend_hasta(FechaUtil.convertirADate(rendicion.getFechaHasta()));

        // Montos (con validación null-safe)
        if (rendicion.getMonto() != null) {
            cobranza.setRend_monto(rendicion.getMonto().doubleValue());
        }
        if (rendicion.getMontoDepositado() != null) {
            cobranza.setRend_monto_depositado(rendicion.getMontoDepositado().doubleValue());
        }
        if (rendicion.getMontoComision() != null) {
            cobranza.setRend_comision(rendicion.getMontoComision().doubleValue());
        }
        if (rendicion.getMontoIVA() != null) {
            cobranza.setRend_iva(rendicion.getMontoIVA().doubleValue());
        }

        // Observaciones para trazabilidad
        String obs = String.format(
                "Rendida | Rend#%d Seq#%d %s | Actualizado: %s",
                rendicion.getNumero(),
                rendicion.getSecuencia(),
                rendicion.getEstado(),
                LocalDate.now()
        );
        cobranza.setObservaciones(obs);
    }

    /**
     * Genera percepciones para todas las rendiciones.
     *
     * INTEGRACIÓN CLAVE CON RendicionPercepcionService.
     *
     * Las percepciones permiten:
     * - Conciliación automática de pagos
     * - Detección de discrepancias
     * - Identificación de transacciones huérfanas
     * - Auditoría completa del proceso
     *
     * Este método delega la complejidad al servicio especializado.
     *
     * @param rendiciones Lista de rendiciones
     * @param codigoProvincia Código de provincia
     * @return Resultado de la conciliación
     */
    private ResultadoConciliacion generarPercepciones(
            List<RendicionDTO> rendiciones,
            String codigoProvincia) {

        log.info("─────────────────────────────────────────────────────────────");
        log.info("GENERANDO PERCEPCIONES PARA CONCILIACIÓN");
        log.info("─────────────────────────────────────────────────────────────");

        try {
            // Delegar al servicio especializado
            ResultadoConciliacion resultado = percepcionService
                    .procesarYConciliarRendiciones(rendiciones, codigoProvincia);

            log.info("✓ Percepciones generadas exitosamente");
            log.info("  → Conciliadas: {}", resultado.getConciliadas());
            log.info("  → Huérfanas: {}", resultado.getHuerfanas());
            log.info("  → Duplicadas: {}", resultado.getDuplicadas());

            return resultado;

        } catch (Exception e) {
            log.error("✗ Error al generar percepciones: {}", e.getMessage(), e);

            // Crear resultado vacío en caso de error
            ResultadoConciliacion resultadoError = new ResultadoConciliacion();
            resultadoError.setExitoso(false);
            resultadoError.setMensajeError(e.getMessage());

            return resultadoError;
        }
    }

    /**
     * Persiste todas las cobranzas actualizadas en un solo lote (batch).
     *
     * PATRÓN: Unit of Work
     * Acumula cambios en memoria y persiste todo junto.
     *
     * VENTAJAS:
     * - Reduce round-trips a la BD
     * - Aprovecha batch inserts/updates
     * - Más rápido que persistir una por una
     * - Transaccional: todo o nada
     *
     * @param cobranzas Lista de cobranzas actualizadas
     * @return Cantidad de cobranzas persistidas
     */
    private int persistirCobranzas(List<Cobranza> cobranzas) {
        if (cobranzas.isEmpty()) {
            log.info("⊗ No hay cobranzas para persistir");
            return 0;
        }

        log.info("💾 Persistiendo {} cobranzas en lote...", cobranzas.size());

        try {
            cobranzaRepository.saveAll(cobranzas);
            log.info("✓ Cobranzas persistidas exitosamente");
            return cobranzas.size();

        } catch (Exception e) {
            log.error("✗ Error al persistir cobranzas: {}", e.getMessage(), e);
            throw new RuntimeException("Error en persistencia de cobranzas", e);
        }
    }

    /**
     * Registra una rendición en la tabla de auditoría.
     *
     * Mantiene historial de todas las rendiciones procesadas para:
     * - Auditorías internas/externas
     * - Debugging de problemas
     * - Trazabilidad de operaciones
     * - Reportes gerenciales
     *
     * @param rendicion Rendición a registrar
     * @param codigoProvincia Código de provincia
     */
    private void registrarRendicion(RendicionDTO rendicion, String codigoProvincia) {
        try {
            RendicionCobro registro = new RendicionCobro();
            registro.setFecha_procesamiento(FechaUtil.convertirADate(LocalDate.now()));
            registro.setFecha_alta(new Date());
            registro.setProcesador("e-Pagos");

            String obs = String.format(
                    "Prov:%s | Rend#%d Seq#%d %s | Periodo:%s-%s | Monto:%s | Cant:%d",
                    codigoProvincia,
                    rendicion.getNumero(),
                    rendicion.getSecuencia(),
                    rendicion.getEstado(),
                    rendicion.getFechaDesde(),
                    rendicion.getFechaHasta(),
                    rendicion.getMonto(),
                    rendicion.getCantidad()
            );
            registro.setObservacion(obs);
            registro.setStatus(1);

            rendicionCobroRepository.save(registro);

            log.debug("  ✓ Auditoría registrada para rendición {}", rendicion.getNumero());

        } catch (Exception e) {
            log.error("⚠ Error al registrar auditoría (no crítico): {}", e.getMessage());
            // No lanzar excepción - la auditoría es importante pero no crítica
        }
    }

    /**
     * Valida que una rendición sea procesable.
     *
     * CRITERIOS DE VALIDACIÓN:
     * - No nula
     * - Número presente
     * - Estado presente y no vacío
     *
     * @param rendicion Rendición a validar
     * @return true si cumple todos los criterios
     */
    private boolean esRendicionValida(RendicionDTO rendicion) {
        return rendicion != null
                && rendicion.getNumero() != null
                && rendicion.getEstado() != null
                && !rendicion.getEstado().trim().isEmpty();
    }

    /**
     * Imprime resumen final detallado del procesamiento.
     *
     * Consolida métricas de:
     * - Procesamiento de rendiciones
     * - Actualización de cobranzas
     * - Generación de percepciones
     * - Performance (tiempo de ejecución)
     *
     * @param resultado Resultado del procesamiento de rendiciones
     * @param resultadoConciliacion Resultado de la conciliación
     * @param totalActualizadas Total de cobranzas actualizadas
     * @param duracion Duración en millisegundos
     */
    private void imprimirResumenFinal(
            ResultadoProcesamiento resultado,
            ResultadoConciliacion resultadoConciliacion,
            int totalActualizadas,
            long duracion) {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  PROCESAMIENTO INTEGRAL - RESUMEN FINAL                       ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  RENDICIONES Y COBRANZAS:                                     ║");
        log.info("║    • Cobranzas actualizadas:  {}                              ║", totalActualizadas);
        log.info("║    • Transacciones huérfanas: {}                              ║",
                resultado.getHuerfanas().size());
        log.info("║    • Errores:                 {}                              ║",
                resultado.getErrores().size());
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  PERCEPCIONES Y CONCILIACIÓN:                                 ║");
        log.info("║    • Total procesadas:        {}                              ║",
                resultadoConciliacion.getProcesadas());
        log.info("║    • Conciliadas:             {}                              ║",
                resultadoConciliacion.getConciliadas());
        log.info("║    • Huérfanas:               {}                              ║",
                resultadoConciliacion.getHuerfanas());
        log.info("║    • Duplicadas:              {}                              ║",
                resultadoConciliacion.getDuplicadas());
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  ESTADÍSTICAS POR ESTADO:                                     ║");

        if (resultado.getEstadisticas() != null) {
            resultado.getEstadisticas().forEach((estado, cantidad) ->
                    log.info("║    • {}: {}                                              ║",
                            estado, cantidad)
            );
        }

        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  PERFORMANCE:                                                 ║");
        log.info("║    • Duración total:          {} ms ({} seg)                  ║",
                duracion, duracion / 1000);
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // Alertas si hay problemas
        if (!resultado.getHuerfanas().isEmpty()) {
            log.warn("⚠ ALERTA: {} transacciones huérfanas detectadas en cobranzas",
                    resultado.getHuerfanas().size());
        }

        if (resultadoConciliacion.getHuerfanas() > 0) {
            log.warn("⚠ ALERTA: {} percepciones huérfanas requieren investigación",
                    resultadoConciliacion.getHuerfanas());
        }

        if (!resultado.getErrores().isEmpty()) {
            log.error("✗ ERRORES DETECTADOS:");
            resultado.getErrores().forEach(error ->
                    log.error("  • {}", error)
            );
        }
    }
}