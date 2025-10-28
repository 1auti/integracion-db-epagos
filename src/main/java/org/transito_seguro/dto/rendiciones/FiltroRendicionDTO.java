package org.transito_seguro.dto.rendiciones;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO para los filtros de búsqueda de rendiciones.
 * Permite filtrar por número, secuencia o rangos de fechas.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FiltroRendicionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Número único de rendición (opcional)
     * Si se especifica, se devuelve solo esa rendición
     */
    @JsonProperty("numero")
    private Integer numero;

    /**
     * Número secuencial de rendición para el organismo (opcional)
     */
    @JsonProperty("secuencia")
    private Integer secuencia;

    /**
     * Fecha inicial del periodo de la rendición (formato: AAAA-MM-DD)
     */
    @JsonProperty("fecha_desde")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDesde;

    /**
     * Fecha final del periodo de la rendición (formato: AAAA-MM-DD)
     */
    @JsonProperty("fecha_hasta")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaHasta;

    /**
     * Fecha inicial del depósito (formato: AAAA-MM-DD)
     */
    @JsonProperty("fecha_deposito_desde")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDepositoDesde;

    /**
     * Fecha final del depósito (formato: AAAA-MM-DD)
     */
    @JsonProperty("fecha_deposito_hasta")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDepositoHasta;

    @Override
    public String toString() {
        return "FiltroRendicionDTO{" +
                "numero=" + numero +
                ", secuencia=" + secuencia +
                ", fechaDesde=" + fechaDesde +
                ", fechaHasta=" + fechaHasta +
                ", fechaDepositoDesde=" + fechaDepositoDesde +
                ", fechaDepositoHasta=" + fechaDepositoHasta +
                '}';
    }
}
