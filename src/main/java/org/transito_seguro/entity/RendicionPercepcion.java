package org.transito_seguro.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "rendicion_percepcion")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RendicionPercepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer id_concesion;
    private Integer id_entidad_convenio;
    private Integer id_procesamiento;
    private Integer id_cobranza_vinculada;
    private Integer id_proceso;
    private Integer status;
    private Integer usuario_alta;
    private Integer usuario_mod;
    private Integer usuario_baja;

    private String procesador;
    private String observacion;
    private String file_path;
    private String estado_rendicion;
    private String convenio;
    private String codigo_provincia;
    private String numero_transaccion;
    private String numero_operacion;
    private String monto_detalle;
    /**
     * Estado del proceso de conciliación:
     * - PERCIBIDA: Recién creada, sin procesar
     * - CONCILIADA: Se encontró la cobranza correspondiente
     * - HUERFANA: NO existe cobranza para esta transacción
     * - DUPLICADA: Ya existe otra percepción para esta transacción
     * - ERROR: Error al procesar
     */
    private String estado_conciliacion;

    private Boolean depositable;
    private Boolean procesada;

    private Date fecha_mod;
    private Date fecha_baja;
    private Date fecha_procesamiento;
    private Date fecha_alta;
    private Date rend_desde;
    private Date rend_hasta;
    private Date fecha_deposito;
    private Date fecha_estimado_deposito;

    private Long numero_rendicion;
    private Long secuencia_rendicion;

    private Double monto_rendicion;
    private Double monto_depositado_rendicion;
    private Double monto_comision_rendicion;
    private Double monto_iva_rendicion;
    private Double diferencia_monto;

    @PrePersist
    protected void onCreate() {
        if (fecha_alta == null) fecha_alta = new Date();
        if (status == null) status = 1;
        if (procesada == null) procesada = false;
        if (estado_conciliacion == null) estado_conciliacion = "PERCIBIDA";
        if (procesador == null) procesador = "e-Pagos";
    }

    @PreUpdate
    protected void onUpdate() {
        this.fecha_mod = new Date();
    }


    public boolean estaConciliada() {
        return "CONCILIADA".equals(estado_conciliacion);
    }

    public boolean esHuerfana() {
        return "HUERFANA".equals(estado_conciliacion);
    }

    public void marcarComoConciliada(Integer idCobranza) {
        this.estado_conciliacion = "CONCILIADA";
        this.id_cobranza_vinculada = idCobranza;
        this.procesada = true;
        this.fecha_procesamiento = new Date();
        this.observacion = String.format(
                "Conciliada OK - Cobranza #%d vinculada", idCobranza
        );
    }

    public void marcarComoHuerfana() {
        this.estado_conciliacion = "HUERFANA";
        this.procesada = true;
        this.fecha_procesamiento = new Date();
        this.observacion = String.format(
                "Huérfana - No existe cobranza para txn:%s (Rend#%d Op:%s)",
                numero_transaccion, numero_rendicion, numero_operacion
        );
    }


}
