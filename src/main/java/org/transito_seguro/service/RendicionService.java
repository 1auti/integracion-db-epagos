package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.rendiciones.DetalleRendicionDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.entity.Cobranza;
import org.transito_seguro.entity.RendicionCobro;
import org.transito_seguro.model.ResultadoProcesamiento;
import org.transito_seguro.repository.CobranzaRepository;
import org.transito_seguro.repository.RendicionCobroRepository;
import org.transito_seguro.util.FechaUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para el procesamiento de rendiciones desde e-pagos.
 *
 * Procesamiento optimizado simple:
 * 1. Extraer todos los números de transacción
 * 2. Buscar todas las cobranzas en 1 query
 * 3. Crear mapa para acceso rápido
 * 4. Procesar y actualizar
 */
@Service
@Slf4j
public class RendicionService {

    @Autowired
    private CobranzaRepository cobranzaRepository;

    @Autowired
    private RendicionCobroRepository rendicionCobroRepository;

    /**
     * Procesa una lista de rendiciones de e-Pagos.
     *
     * @param codigoProvincia Código de la provincia
     * @param rendiciones Lista de rendiciones a procesar
     * @return Cantidad de cobranzas actualizadas
     */
    @Transactional
    public int procesarRendiciones(String codigoProvincia, List<RendicionDTO> rendiciones) {

        log.info("Procesando {} rendiciones para provincia {}",
                rendiciones != null ? rendiciones.size() : 0, codigoProvincia);

        // Validación básica
        if (rendiciones == null || rendiciones.isEmpty()) {
            log.info("No hay rendiciones para procesar");
            return 0;
        }

        // PASO 1: Extraer todos los números de transacción
        Set<String> numerosTransaccion = extraerNumerosTransaccion(rendiciones);
        log.debug("Total de transacciones únicas: {}", numerosTransaccion.size());

        // PASO 2: Buscar TODAS las cobranzas en UN SOLO query
        Map<String, Cobranza> cobranzasPorNumero = buscarCobranzasEnLote(numerosTransaccion);
        log.info("Cobranzas encontradas: {} de {}",
                cobranzasPorNumero.size(), numerosTransaccion.size());

        // PASO 3: Procesar cada rendición
        int totalActualizadas = 0;
        List<Cobranza> cobranzasParaActualizar = new ArrayList<>();
        List<String> huerfanas = new ArrayList<>();
        Map<String, Integer> estadisticas = new HashMap<>();

        for (RendicionDTO rendicion : rendiciones) {
            try {
                // Validar rendición
                if (!esRendicionValida(rendicion)) {
                    log.warn("Rendición {} no válida, saltando", rendicion.getNumero());
                    continue;
                }

                // Procesar detalles
                ResultadoProcesamiento resultado = procesarDetallesRendicion(
                        rendicion,
                        cobranzasPorNumero
                );

                totalActualizadas += resultado.getCobranzas().size();
                cobranzasParaActualizar.addAll(resultado.getCobranzas());
                huerfanas.addAll(resultado.getHuerfanas());

                // Registrar rendición
                registrarRendicion(rendicion, codigoProvincia);

                // Estadísticas
                String estado = rendicion.getEstado();
                estadisticas.put(estado, estadisticas.getOrDefault(estado, 0) + 1);

            } catch (Exception e) {
                log.error("Error al procesar rendición {}: {}",
                        rendicion.getNumero(), e.getMessage(), e);
            }
        }

        // PASO 4: Guardar todas las cobranzas actualizadas
        if (!cobranzasParaActualizar.isEmpty()) {
            cobranzaRepository.saveAll(cobranzasParaActualizar);
        }

        // Resumen
        log.info("════════════════════════════════════════════════");
        log.info("✓ Procesamiento completado");
        log.info("  Rendiciones: {}", rendiciones.size());
        log.info("  Cobranzas actualizadas: {}", totalActualizadas);
        log.info("  Huérfanas: {}", huerfanas.size());
        log.info("  Por estado: {}", estadisticas);
        log.info("════════════════════════════════════════════════");

        if (!huerfanas.isEmpty()) {
            log.warn("⚠ {} transacciones huérfanas detectadas", huerfanas.size());
        }

        return totalActualizadas;
    }

    /**
     * Extrae todos los números de transacción de las rendiciones.
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
     */
    private Map<String, Cobranza> buscarCobranzasEnLote(Set<String> numerosTransaccion) {
        if (numerosTransaccion.isEmpty()) {
            return new HashMap<>();
        }

        List<String> lista = new ArrayList<>(numerosTransaccion);
        List<Cobranza> cobranzas = cobranzaRepository.buscarPorNumerosTransaccion(lista);

        // Convertir a mapa para acceso O(1)
        return cobranzas.stream()
                .collect(Collectors.toMap(
                        Cobranza::getNumero_transaccion,
                        cobranza -> cobranza
                ));
    }

    /**
     * Procesa los detalles de una rendición usando el mapa pre-cargado.
     */
    private ResultadoProcesamiento procesarDetallesRendicion(
            RendicionDTO rendicion,
            Map<String, Cobranza> cobranzasPorNumero) {

        ResultadoProcesamiento resultado = new ResultadoProcesamiento();

        if (rendicion.getDetalles() == null) {
            return resultado;
        }

        for (DetalleRendicionDTO detalle : rendicion.getDetalles()) {
            Long codigoUnico = detalle.getCodigoUnicoTransaccion();

            if (codigoUnico == null){
                log.warn("Codigo unico de transaccion vacio");
                continue;
            }

            String numeroTxn = codigoUnico.toString();
            Cobranza cobranza = cobranzasPorNumero.get(numeroTxn);

            if (cobranza != null) {
                // Actualizar cobranza
                actualizarCobranzaConRendicion(cobranza, rendicion, detalle);
                resultado.agregarCobranza(cobranza);

            } else {
                // Huérfana
                resultado.agregarHuerfana(numeroTxn);
                log.warn("⚠ Huérfana: txn={}, monto={}",
                        numeroTxn, detalle.getMonto());
            }
        }

        return resultado;
    }

    /**
     * Actualiza una cobranza con datos de la rendición.
     */
    private void actualizarCobranzaConRendicion(
            Cobranza cobranza,
            RendicionDTO rendicion,
            DetalleRendicionDTO detalle) {

        cobranza.setRend_nro(rendicion.getNumero());
        cobranza.setRend_secuencia(rendicion.getSecuencia());
        cobranza.setRend_estado(rendicion.getEstado());

        cobranza.setRend_fecha_deposito(FechaUtil.convertirADate(rendicion.getFechaDeposito()));
        cobranza.setRend_desde(FechaUtil.convertirADate(rendicion.getFechaDesde()));
        cobranza.setRend_hasta(FechaUtil.convertirADate(rendicion.getFechaHasta()));

        cobranza.setRend_medio_pago(rendicion.getConvenio());

        if (rendicion.getMonto() != null) {
            cobranza.setRend_monto(rendicion.getMonto().floatValue());
        }
        if (rendicion.getMontoDepositado() != null) {
            cobranza.setRend_monto_depositado(rendicion.getMontoDepositado().floatValue());
        }
        if (rendicion.getMontoComision() != null) {
            cobranza.setRend_comision(rendicion.getMontoComision().floatValue());
        }
        if (rendicion.getMontoIVA() != null) {
            cobranza.setRend_iva(rendicion.getMontoIVA().floatValue());
        }

        String obs = String.format("Rendida - Rend#%d Seq#%d %s",
                rendicion.getNumero(), rendicion.getSecuencia(), rendicion.getEstado());
        cobranza.setObservaciones(obs);
    }

    /**
     * Registra una rendición en tabla de auditoría.
     */
    private void registrarRendicion(RendicionDTO rendicion, String codigoProvincia) {
        try {
            RendicionCobro registro = new RendicionCobro();
            registro.setFecha_procesamiento(FechaUtil.convertirADate(LocalDate.now()));
            registro.setFecha_alta(new Date());
            registro.setProcesador("e-Pagos");

            String obs = String.format(
                    "Prov:%s Rend#%d Seq#%d %s Periodo:%s-%s Monto:%s Cant:%d",
                    codigoProvincia, rendicion.getNumero(), rendicion.getSecuencia(),
                    rendicion.getEstado(), rendicion.getFechaDesde(), rendicion.getFechaHasta(),
                    rendicion.getMonto(), rendicion.getCantidad()
            );
            registro.setObservacion(obs);
            registro.setStatus(1);

            rendicionCobroRepository.save(registro);

        } catch (Exception e) {
            log.error("Error al registrar rendición: {}", e.getMessage());
        }
    }

    /**
     * Valida que una rendición sea válida.
     */
    private boolean esRendicionValida(RendicionDTO rendicion) {
        return rendicion != null
                && rendicion.getNumero() != null
                && rendicion.getEstado() != null
                && !rendicion.getEstado().trim().isEmpty();
    }


}