package org.transito_seguro.dto.token;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * DTO para la respuesta de obtención de token.
 */
public class ObtenerTokenResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Código de respuesta
     */
    @JsonProperty("id_resp")
    private String idResp;

    /**
     * Descripción de la respuesta
     */
    @JsonProperty("respuesta")
    private String respuesta;

    /**
     * Token generado (válido por 24 horas)
     */
    @JsonProperty("token")
    private String token;


    /**
     * Verifica si la respuesta fue exitosa
     * @return true si hay un token válido
     */
    public boolean isExitosa() {
        return token != null && !token.isEmpty();
    }

    @Override
    public String toString() {
        return "ObtenerTokenResponseDTO{" +
                "idResp='" + idResp + '\'' +
                ", respuesta='" + respuesta + '\'' +
                ", token='" + (token != null ? "****" : "null") + '\'' +
                '}';
    }
}
