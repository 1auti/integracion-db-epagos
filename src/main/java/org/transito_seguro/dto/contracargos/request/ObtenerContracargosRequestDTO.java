package org.transito_seguro.dto.contracargos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.transito_seguro.dto.CredencialesDTO;

import java.io.Serializable;

/**
 * DTO para la solicitud de obtención de contracargos.
 * Método SOAP: obtener_contracargos
 *
 * Los contracargos son reclamos de usuarios por pagos no reconocidos.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObtenerContracargosRequestDTO implements Serializable {

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
     * Filtros para la búsqueda de contracargos
     */
    @JsonProperty("datos_contracargos")
    private FiltroContracargoDTO datosContracargos;


    @Override
    public String toString() {
        return "ObtenerContracargosRequestDTO{" +
                "version='" + version + '\'' +
                ", credenciales=" + credenciales +
                ", datosContracargos=" + datosContracargos +
                '}';
    }
}
