package org.transito_seguro.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "concesion_configuracion_convenio")
@AllArgsConstructor
@Getter
public class ConcesionConfiguracionConvenio {

    @Id
    private Integer id;
    private Integer id_concesion;
    private Integer id_documentacion;
    private String numero;
    private Integer id_entidad_convenio;
    private Boolean is_activo;
    private Date fecha_alta;
    private String variables;
    private Date fecha_mod;
    private Date fecha_baja;
    private String codigo_acceso_cobro;

}
