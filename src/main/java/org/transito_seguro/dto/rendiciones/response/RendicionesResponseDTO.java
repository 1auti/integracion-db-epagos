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

    /**
     * Código de respuesta del servicio.
     * Ejemplo: 5001 indica "Rendiciones devueltas"
     */
    @JsonProperty("id_resp")
    private Integer idRespuesta;

    /**
     * Mensaje descriptivo de la respuesta.
     * Ejemplo: "Rendiciones devueltas"
     */
    @JsonProperty("respuesta")
    private String respuesta;

    /**
     * Token de seguridad generado por e-pagos para la sesión.
     */
    @JsonProperty("token")
    private String token;

    /**
     * Identificador del organismo que realiza la consulta.
     */
    @JsonProperty("id_organismo")
    private Integer idOrganismo;

    /**
     * Lista de rendiciones obtenidas en el período consultado.
     * Cada rendición contiene información detallada de transacciones y contracargos.
     */
    @JsonProperty("rendicion")
    private List<RendicionDTO> rendiciones;

}