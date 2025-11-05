package org.transito_seguro.model;

import lombok.Getter;
import lombok.Setter;
import org.transito_seguro.entity.Cobranza;

import java.util.ArrayList;
import java.util.List;


import lombok.Data;
import org.transito_seguro.entity.Cobranza;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo que encapsula el resultado del procesamiento de rendiciones.
 *
 * PATRÓN DE DISEÑO: VALUE OBJECT + DTO
 * - Inmutable desde el exterior (después de construcción)
 * - Agrupa datos relacionados
 * - Facilita el paso de múltiples valores entre capas
 *
 * PROPÓSITO:
 * Evitar retornar múltiples valores o usar parámetros OUT.
 * Proporciona una estructura clara y tipada del resultado.
 *
 * USO:
 * ```java
 * ResultadoProcesamiento resultado = new ResultadoProcesamiento();
 * resultado.agregarCobranza(cobranza);
 * resultado.agregarHuerfana("TXN123");
 * ```
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
@Data
public class ResultadoProcesamiento {

    /**
     * Lista de cobranzas que fueron encontradas y actualizadas.
     * Estas serán persistidas en la base de datos.
     */
    private List<Cobranza> cobranzas;

    /**
     * Lista de números de transacción "huérfanas".
     * Huérfanas = reportadas por e-Pagos pero no existen en nuestra BD.
     *
     * CAUSA COMÚN:
     * - Transacciones aún no sincronizadas
     * - Errores en carga inicial
     * - Transacciones de otros organismos (config incorrecta)
     */
    private List<String> huerfanas;

    /**
     * Lista de errores ocurridos durante el procesamiento.
     * Permite continuar el proceso a pesar de errores individuales.
     */
    private List<String> errores;

    /**
     * Estadísticas agregadas por estado de rendición.
     * Key: Estado (ej: "DEPOSITADA", "PENDIENTE", "ANULADA")
     * Value: Cantidad de rendiciones en ese estado
     */
    private Map<String, Integer> estadisticas;

    /**
     * Constructor - Inicializa todas las colecciones vacías.
     *
     * PATRÓN: Null Object Pattern
     * Evita NullPointerException al garantizar que las colecciones
     * nunca sean null, sino listas vacías.
     */
    public ResultadoProcesamiento() {
        this.cobranzas = new ArrayList<>();
        this.huerfanas = new ArrayList<>();
        this.errores = new ArrayList<>();
        this.estadisticas = new HashMap<>();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS DE CONVENIENCIA (Fluent API)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Agrega una cobranza al resultado.
     *
     * @param cobranza Cobranza procesada y actualizada
     */
    public void agregarCobranza(Cobranza cobranza) {
        if (cobranza != null) {
            this.cobranzas.add(cobranza);
        }
    }

    /**
     * Agrega un número de transacción huérfana.
     *
     * @param numeroTransaccion Número de transacción sin cobranza asociada
     */
    public void agregarHuerfana(String numeroTransaccion) {
        if (numeroTransaccion != null && !numeroTransaccion.trim().isEmpty()) {
            this.huerfanas.add(numeroTransaccion);
        }
    }

    /**
     * Agrega un mensaje de error.
     *
     * @param error Descripción del error ocurrido
     */
    public void agregarError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            this.errores.add(error);
        }
    }

    /**
     * Verifica si el procesamiento fue exitoso.
     *
     * @return true si no hubo errores
     */
    public boolean esExitoso() {
        return this.errores.isEmpty();
    }

    /**
     * Obtiene un resumen del procesamiento en formato texto.
     *
     * @return String con métricas principales
     */
    public String getResumen() {
        return String.format(
                "Cobranzas: %d | Huérfanas: %d | Errores: %d",
                cobranzas.size(),
                huerfanas.size(),
                errores.size()
        );
    }
}
