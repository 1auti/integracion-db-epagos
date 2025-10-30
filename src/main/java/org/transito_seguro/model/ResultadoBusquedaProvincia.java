package org.transito_seguro.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Resultado de búsqueda para una provincia individual.
 */
@Getter
@Setter
public class ResultadoBusquedaProvincia {
    private String codigoProvincia;
    private boolean exitoso;

    private int rendicionesEncontradas;
    private int contracargosEncontrados;

    private int rendicioncesProcesados;
    private int contracargosProcesados;

    private int infraccionesActualizadas;
    private String mensajeError;

    public ResultadoBusquedaProvincia(String codigoProvincia) {
        this.codigoProvincia = codigoProvincia;
    }



}
