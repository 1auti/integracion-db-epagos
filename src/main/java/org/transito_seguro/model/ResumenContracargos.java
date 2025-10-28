package org.transito_seguro.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.transito_seguro.util.FechaUtil;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResumenContracargos {


    private String codigoProvincia;
    private Date fechaDesde;
    private Date fechaHasta;
    private int cantidadTotal;
    private int cantidadEnAnalisis;
    private int cantidadAceptados;
    private double montoTotalDisputa;
    private double montoAceptados;
    private Map<String, Long> contracargosPorEstado;
    private Map<String, Double> montosPorEstado;

    /**
     * Calcula el porcentaje de contracargos aceptados sobre el total.
     *
     * @return porcentaje de aceptación (0-100)
     */
    public double calcularPorcentajeAceptacion() {
        if (cantidadTotal == 0) {
            return 0.0;
        }
        return (cantidadAceptados * 100.0) / cantidadTotal;
    }

    /**
     * Calcula el impacto financiero porcentual de los contracargos aceptados.
     *
     * @return porcentaje del monto en disputa que fue aceptado (0-100)
     */
    public double calcularImpactoFinanciero() {
        if (montoTotalDisputa == 0) {
            return 0.0;
        }
        return (montoAceptados * 100.0) / montoTotalDisputa;
    }

    /**
     * Genera un resumen textual del resultado.
     *
     * @return String con el resumen
     */
    public String generarResumen() {
        return String.format(
                "Resumen Contracargos - Provincia: %s, Período: %s a %s | " +
                        "Total: %d | En Análisis: %d | Aceptados: %d (%.2f%%) | " +
                        "Monto Total Disputa: $%.2f | Monto Aceptados: $%.2f (%.2f%% de impacto)",
                codigoProvincia != null ? codigoProvincia : "TODAS",
                FechaUtil.formatearFechaParaEpagos(fechaDesde),
                FechaUtil.formatearFechaParaEpagos(fechaHasta),
                cantidadTotal,
                cantidadEnAnalisis,
                cantidadAceptados,
                calcularPorcentajeAceptacion(),
                montoTotalDisputa,
                montoAceptados,
                calcularImpactoFinanciero()
        );
    }

}
