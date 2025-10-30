package org.transito_seguro.dto.rendiciones;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.transito_seguro.dto.contracargos.ContracargoDTO;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO que representa una rendición individual.
 * Contiene toda la información financiera y operativa de la rendición.
 *
 * @author Tránsito Seguro
 * @version 1.0
 * @since 2025-01-27
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RendicionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Número único de rendición
     */
    @JsonProperty("Numero")
    private Integer numero;

    /**
     * Número secuencial de rendición para el organismo
     */
    @JsonProperty("Secuencia")
    private Integer secuencia;

    /**
     * Número de convenio al que pertenece
     */
    @JsonProperty("Convenio")
    private Integer convenio;

    /**
     * Estado de la rendición:
     * - "Activa": Aún no depositada
     * - "Depositada": Ya fue depositada
     */
    @JsonProperty("Estado")
    private String estado;

    /**
     * Fecha desde donde se incluye la rendición
     */
    @JsonProperty("Fecha_desde")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDesde;

    /**
     * Fecha hasta donde se incluye la rendición
     */
    @JsonProperty("Fecha_hasta")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaHasta;

    /**
     * Fecha estimada en que deberá ser depositada
     */
    @JsonProperty("Fecha_estimada_deposito")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaEstimadaDeposito;

    /**
     * Fecha real en que se realizó el depósito
     */
    @JsonProperty("Fecha_deposito")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDeposito;

    /**
     * Monto bruto de la rendición (antes de comisiones)
     */
    @JsonProperty("Monto")
    private BigDecimal monto;

    /**
     * Monto neto depositado (después de deducciones)
     */
    @JsonProperty("Monto_depositado")
    private BigDecimal montoDepositado;

    /**
     * Monto de comisión cobrada por e-Pagos
     */
    @JsonProperty("Monto_comision")
    private BigDecimal montoComision;

    /**
     * Monto del IVA sobre la comisión
     */
    @JsonProperty("Monto_IVA")
    private BigDecimal montoIVA;

    /**
     * Monto de Ingresos Brutos sobre la comisión
     */
    @JsonProperty("Monto_IIBB")
    private BigDecimal montoIIBB;

    /**
     * Monto de contracargos (devoluciones/reclamos)
     */
    @JsonProperty("Monto_CC")
    private BigDecimal montoCC;

    /**
     * Monto no depositable (ej: Transferencias 3.0)
     */
    @JsonProperty("Monto_ND")
    private BigDecimal montoND;

    /**
     * Cantidad de transacciones incluidas en la rendición
     */
    @JsonProperty("Cantidad")
    private Integer cantidad;

    /**
     * Detalles de las transacciones incluidas en la rendición
     */
    @JsonProperty("Detalles")
    private List<DetalleRendicionDTO> detalles;

    @JsonProperty("Contracargos")
    private List<ContracargoDTO> contracargos;


    @Override
    public String toString() {
        return "RendicionDTO{" +
                "numero=" + numero +
                ", secuencia=" + secuencia +
                ", convenio=" + convenio +
                ", estado='" + estado + '\'' +
                ", fechaDesde=" + fechaDesde +
                ", fechaHasta=" + fechaHasta +
                ", monto=" + monto +
                ", montoDepositado=" + montoDepositado +
                ", cantidad=" + cantidad +
                '}';
    }
}
