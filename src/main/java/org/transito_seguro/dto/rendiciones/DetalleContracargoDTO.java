package org.transito_seguro.dto.rendiciones;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DetalleContracargoDTO {

    private static final long serialVersion = 1L;

    /**
     * Código único del contracargo.
     */
    @JsonProperty("Codigo_unico_contracargo")
    private Long codigoUnicoContracargo;

    /**
     * Código único de la transacción original relacionada.
     */
    @JsonProperty("Codigo_unico_transaccion")
    private Long codigoUnicoTransaccion;

    /**
     * Monto del contracargo (en centavos).
     */
    @JsonProperty("Monto")
    private Long monto;

    /**
     * Motivo del contracargo.
     */
    @JsonProperty("Motivo")
    private String motivo;

    /**
     * Fecha del contracargo (formato: yyyy-MM-dd).
     */
    @JsonProperty("Fecha")
    private String fecha;

    /**
     * Número de operación del contracargo.
     */
    @JsonProperty("Numero_operacion")
    private String numeroOperacion;

    @JsonProperty("Numero_rendicion")
    private Long numeroRendicion;
}
