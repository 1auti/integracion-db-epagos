package org.transito_seguro.enu;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EstadoRendicion {
    PENDIENTE("Pendiente"),
    DEPOSITADA("Depositada"),
    CANCELADA("Cancelada");

    private final String valor;

    /**
     * Obtiene el enum desde su valor de texto
     * @param valor El valor de texto del estado
     * @return El enum correspondiente o null si no existe
     */
    public static EstadoRendicion fromValor(String valor) {
        for (EstadoRendicion estado : values()) {
            if (estado.valor.equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        return null;
    }
}
