package org.transito_seguro.dto.credenciales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Esto son los datos que le pasamos al E-PAGOS para obtener los datos
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CredencialesEpagosDTO implements Serializable {
    private String idOrganismo;
    private String usuario;
    private String clave;
    private String hash;


    private String urlServicio;
    private String implementador;
    private String nombreProvincia;  // Para identificar
}
