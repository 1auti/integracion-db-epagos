package org.transito_seguro.dto.rendiciones.request;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.io.Serializable;

/**
 DTO para solicitar las rendiciones al Servicio Diego
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RendicionesRequestDTO implements Serializable {

    private String idOrganismo;

    private String idUsuario;

    private String password;

    private String hash;

    private String fechaDesde;

    private String fechaHasta;

    private String convenios;

}
