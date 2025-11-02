package org.transito_seguro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO auxiliar para parsear el JSON de la columna 'variables'.
 *
 * JSON esperado:
 * [
 *   {"key": "URL_SERVICIO", "value": "http://..."},
 *   {"key": "idOrganismo", "value": "172"}
 * ]
 *
 * Esta clase SOLO se usa para deserializar el JSON.
 * Después se convierte a Map y luego a CredencialesEpagosDTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariableConfigDTO implements Serializable {

    /**
     * Nombre de la variable.
     * Ejemplos: "URL_SERVICIO", "idOrganismo", "password"
     */
    @JsonProperty("key")
    private String key;

    /**
     * Valor de la variable.
     * Siempre es String en el JSON.
     */
    @JsonProperty("value")
    private String value;
}