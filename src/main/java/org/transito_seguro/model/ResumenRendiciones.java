package org.transito_seguro.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResumenRendiciones {

    private String codigoProvincia;
    private Date fechaDesde;
    private Date fechaHasta;
    private int cantidadRendiciones;
    private double montoTotal;
    private int cantidadHuerfanas;
    private Map<String, Long> rendicionesPorEstado;
}
