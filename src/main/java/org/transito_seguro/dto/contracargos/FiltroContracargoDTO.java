package org.transito_seguro.dto.contracargos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO para los filtros de búsqueda de contracargos.
 */
public class FiltroContracargoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Número único del contracargo (opcional)
     */
    @JsonProperty("numero")
    private Integer numero;

    /**
     * Estado del contracargo (opcional):
     * - "Pendiente": Esperando respuesta del organismo
     * - "Respondido": Ya fue respondido por el organismo
     * - "Aceptado": Aceptado por el organismo o vencido sin respuesta
     * - "Resuelto": Solucionado ante el medio de pago
     */
    @JsonProperty("estado")
    private String estado;

    /**
     * Fecha inicial de carga del contracargo
     */
    @JsonProperty("fecha_desde")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDesde;

    /**
     * Fecha final de carga del contracargo
     */
    @JsonProperty("fecha_hasta")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaHasta;

    @Override
    public String toString() {
        return "FiltroContracargoDTO{" +
                "numero=" + numero +
                ", estado='" + estado + '\'' +
                ", fechaDesde=" + fechaDesde +
                ", fechaHasta=" + fechaHasta +
                '}';
    }
}
