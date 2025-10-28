package org.transito_seguro.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "rendicion_cobro")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RendicionCobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer id_concesion;
    private Integer id_entidad_convenio;
    private Integer id_entidad_proceso;
    private Integer status;
    private Integer usuario_alta;
    private Integer usuario_mod;
    private Integer usuario_baja;

    private Date fecha_procesamiento;
    private Date fecha_alta;
    private Date fecha_mod;
    private Date fecha_baja;

    private String procesador;
    private String observacion;
    private String file_path;

}

