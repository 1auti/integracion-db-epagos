package org.transito_seguro.enu;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EstadoContracargo {

    // Hay mas estado para estso son los que nos importan
    P("PENDIENTE"),
    F("FINALIZADO"),
    FNF("FINALIZADO NO FAVORABLE");
    private final String valor;


    /**
     * Obtiene el enum desde su valor de texto
     * @param valor El valor de texto del estado
     * @return El enum correspondiente o null si no existe
     */
    public static EstadoContracargo fromValor(String valor) {
        for (EstadoContracargo estado : values()) {
            if (estado.valor.equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        return null;
    }
}
