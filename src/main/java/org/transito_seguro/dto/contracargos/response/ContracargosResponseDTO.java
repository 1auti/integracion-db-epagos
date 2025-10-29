package org.transito_seguro.dto.contracargos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.transito_seguro.dto.contracargos.ContracargoDTO;

import java.io.Serializable;
import java.util.List;

@Getter
public class ContracargosResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Código de respuesta
     * 06001: Contracargos devueltos (éxito)
     * 06002: Error al validar el token
     * 06003: Error interno
     * 06004: Rango de fechas no es correcto
     * 06005: Error al validar parámetro
     * 06006: Versión inválida del protocolo
     */
    @JsonProperty("id_resp")
    private String idResp;

    /**
     * Descripción de la respuesta
     */
    @JsonProperty("respuesta")
    private String respuesta;

    /**
     * Token utilizado en la consulta
     */
    @JsonProperty("token")
    private String token;

    /**
     * Código del organismo
     */
    @JsonProperty("id_organismo")
    private Integer idOrganismo;

    /**
     * Lista de contracargos encontrados
     */
    @JsonProperty("contracargos")
    private List<ContracargoDTO> contracargos;


    /**
     * Verifica si la respuesta fue exitosa
     * @return true si el código es 06001 (éxito)
     */
    public boolean isExitosa() {
        return "06001".equals(idResp);
    }

    @Override
    public String toString() {
        return "ObtenerContracargosResponseDTO{" +
                "idResp='" + idResp + '\'' +
                ", respuesta='" + respuesta + '\'' +
                ", token='" + (token != null ? "****" : "null") + '\'' +
                ", idOrganismo=" + idOrganismo +
                ", contracargos=" + (contracargos != null ? contracargos.size() + " items" : "null") +
                '}';
    }
}