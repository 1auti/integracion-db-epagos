package org.transito_seguro.model;

import lombok.Getter;
import lombok.Setter;
import org.transito_seguro.entity.Cobranza;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ResultadoProcesamiento {

    private boolean exitoso;
    private boolean requiereAtencion;
    private String mensajeError;

    private final List<Cobranza> cobranzas = new ArrayList<>();
    private final List<String> huerfanas = new ArrayList<>();

    public void agregarCobranza(Cobranza c) { cobranzas.add(c); }
    public void agregarHuerfana(String h) { huerfanas.add(h); }

}
