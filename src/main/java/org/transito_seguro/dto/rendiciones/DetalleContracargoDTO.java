package org.transito_seguro.dto.rendiciones;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DetalleContracargoDTO {

    private Integer numeroTransaccion;
    private Double monto;
    private Integer numeroRendicion;

}
