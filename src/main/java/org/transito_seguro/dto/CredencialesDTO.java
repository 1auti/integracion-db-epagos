package org.transito_seguro.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


/**
 * DTO para las credenciales de autenticación con e-Pagos.
 * Utilizado en todas las peticiones SOAP.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CredencialesDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Código de organismo proporcionado por e-Pagos
     */
    @JsonProperty("id_organismo")
    private Integer idOrganismo;

    /**
     * Token de autenticación obtenido mediante el método obtener_token
     * El token tiene una validez de 24 horas
     */
    @JsonProperty("token")
    private String token;


    @Override
    public String toString() {
        return "CredencialesDTO{" +
                "idOrganismo=" + idOrganismo +
                ", token='" + (token != null ? "****" : "null") + '\'' +
                '}';
    }
}