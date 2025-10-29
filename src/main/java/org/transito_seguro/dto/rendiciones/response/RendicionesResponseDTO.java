package org.transito_seguro.dto.rendiciones.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.transito_seguro.dto.rendiciones.RendicionDTO;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para la respuesta del servicio de obtención de rendiciones.
 * Contiene el resultado de la operación y la lista de rendiciones.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RendicionesResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Código de respuesta
     * 05001: Rendiciones devueltas (éxito)
     * 05002: Error al validar el token
     * 05003: Error interno
     * 05004: Rango de fechas supera el límite
     * 05005: Error al validar parámetro
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
     * Lista de rendiciones encontradas
     */
    @JsonProperty("rendicion")
    private List<RendicionDTO> rendiciones;

}