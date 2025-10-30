package org.transito_seguro.dto.credenciales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CredencialesEpagosDTO {
    private String idOrganismo;
    private String usuario;
    private String clave;
    private String hash;
}
