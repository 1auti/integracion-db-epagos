package org.transito_seguro.dto.rendiciones.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.transito_seguro.dto.CredencialesDTO;
import org.transito_seguro.dto.rendiciones.FiltroRendicionDTO;

import java.io.Serializable;

/**
 * DTO para la solicitud de obtención de rendiciones.
 * Método SOAP: obtener_rendiciones
 *
 * Permite consultar las rendiciones diarias de los cobros procesados
 * en un rango de fechas específico.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObtenerRendicionesRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Versión del protocolo de e-Pagos
     * Valor fijo: "2.0" o "2.1"
     */
    @JsonProperty("version")
    private String version = "2.1";

    /**
     * Credenciales de autenticación
     */
    @JsonProperty("credenciales")
    private CredencialesDTO credenciales;

    /**
     * Filtros para la búsqueda de rendiciones
     */
    @JsonProperty("rendicion")
    private FiltroRendicionDTO rendicion;


}
