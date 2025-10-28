package org.transito_seguro.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "cobranza")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Cobranza {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer id_concesion;
    private Integer id_entidad_pago;
    private Integer id_proceso;
    private Integer id_imputacion;
    private Integer id_cliente;
    private Integer id_destino_fondos;
    private Integer id_usuario_conciliacion;
    private Integer id_infraccion;
    private Integer id_convenio_detalle;
    private Integer nro_cuota;
    private Integer cuotas;
    private Integer rend_nro;
    private Integer rend_secuencia;

    private String cod_convenio;
    private String medio_pago;
    private String referencia;
    private String numero_transaccion;
    private String observaciones;
    private String operacion;
    private String numero_cuenta;
    private String codigo_barra;
    private String numero_recibo;
    private String detalle;
    private String comprobante;
    private String rend_estado;

    private Date fecha_pago;
    private Date fecha_proceso;
    private Date fecha_imputacion;
    private Date fecha_alta;
    private Date fecha_vto;
    private Date rend_fecha_deposito;
    private Date rend_desde;
    private Date rend_hasta;

    private float importe_pagado;
    private float importe_neto;
    private float comision_entidad;
    private float rend_monto_depositado;
    private float rend_comision;
    private float rend_iva;
    private float rend_monto;
    private float rend_medio_pago;




}
