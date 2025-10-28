package org.transito_seguro.dto.rendiciones;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO que representa el detalle de una transacción dentro de una rendición.
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetalleRendicionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identificador único de la operación en e-Pagos
     */
    @JsonProperty("Codigo_unico_transaccion")
    private Long codigoUnicoTransaccion;

    /**
     * Importe de la operación
     */
    @JsonProperty("Monto")
    private BigDecimal monto;

    /**
     * Identificación externa del cliente (número de infracción)
     */
    @JsonProperty("Numero_operacion")
    private String numeroOperacion;

    /**
     * Determina si la operación se depositará
     * false para Transferencias 3.0 que no son depositables
     */
    @JsonProperty("Depositable")
    private Boolean depositable;



    @Override
    public String toString() {
        return "DetalleRendicionDTO{" +
                "codigoUnicoTransaccion=" + codigoUnicoTransaccion +
                ", monto=" + monto +
                ", numeroOperacion='" + numeroOperacion + '\'' +
                ", depositable=" + depositable +
                '}';
    }
}
