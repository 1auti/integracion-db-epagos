package org.transito_seguro.dto.token;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * DTO para la solicitud de obtención de token.
 * El token es necesario para todas las operaciones con e-Pagos.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TokenRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Versión del protocolo de e-Pagos
     */
    @JsonProperty("version")
    private String version = "2.1";

    /**
     * Usuario proporcionado por e-Pagos
     */
    @JsonProperty("usuario")
    private String usuario;

    /**
     * Clave proporcionada por e-Pagos
     */
    @JsonProperty("clave")
    private String clave;



    @Override
    public String toString() {
        return "ObtenerTokenRequestDTO{" +
                "version='" + version + '\'' +
                ", usuario='" + usuario + '\'' +
                ", clave='****'" +
                '}';
    }
}