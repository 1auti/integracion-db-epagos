package org.transito_seguro.dto.contracargos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ContracargoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Número único del contracargo
     */
    @JsonProperty("Numero")
    private Integer numero;

    /**
     * Estado del contracargo:
     * - "Pendiente": Esperando respuesta del organismo
     * - "Respondido": Ya fue respondido por el organismo
     * - "Aceptado": Aceptado por el organismo o vencido sin respuesta
     * - "Resuelto": Solucionado ante el medio de pago
     */
    @JsonProperty("Estado")
    private String estado;

    /**
     * Nombre del medio de pago (ej: "Visa", "Mastercard")
     */
    @JsonProperty("Medio")
    private String medio;

    /**
     * Número de operación reclamada (código único de transacción en e-Pagos)
     */
    @JsonProperty("Transaccion")
    private Long transaccion;

    /**
     * Monto de la operación reclamada
     */
    @JsonProperty("Monto")
    private BigDecimal monto;

    /**
     * Número de tarjeta reclamada (parcialmente enmascarado)
     */
    @JsonProperty("Tarjeta")
    private String tarjeta;

    /**
     * Número de lote del medio de pago
     */
    @JsonProperty("Lote")
    private Integer lote;

    /**
     * Número de cupón del medio de pago
     */
    @JsonProperty("Cupon")
    private Integer cupon;

    /**
     * Contenido codificado en Base64 del comprobante de respuesta
     * (solo si el contracargo fue respondido)
     */
    @JsonProperty("Respuesta")
    private String respuesta;

    /**
     * Extensión del archivo del comprobante de respuesta (ej: "pdf", "jpg")
     */
    @JsonProperty("Respuesta_formato")
    private String respuestaFormato;

    /**
     * Contenido codificado en Base64 del comprobante del reclamo
     */
    @JsonProperty("Comprobante")
    private String comprobante;

    /**
     * Extensión del archivo del comprobante del reclamo (ej: "pdf", "jpg")
     */
    @JsonProperty("Comprobante_formato")
    private String comprobanteFormato;

    /**
     * Fecha de alta del contracargo
     */
    @JsonProperty("Fecha")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha;

    /**
     * Fecha de vencimiento del contracargo (límite para responder)
     */
    @JsonProperty("Fecha_vencimiento")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaVencimiento;

    /**
     * Fecha de resolución del contracargo (cuando fue respondido)
     */
    @JsonProperty("Fecha_resolucion")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaResolucion;

    /**
     * Fecha de confirmación del contracargo
     * (cuando no fue respondido antes del vencimiento o fue aceptado)
     */
    @JsonProperty("Fecha_confirmacion")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaConfirmacion;

    /**
     * Fecha de finalización del contracargo
     * (cuando fue solucionado ante el medio de pago)
     */
    @JsonProperty("Fecha_finalizacion")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaFinalizacion;

    /**
     * Verifica si el contracargo está pendiente de respuesta
     * @return true si el estado es "Pendiente"
     */
    public boolean isPendiente() {
        return "Pendiente".equalsIgnoreCase(estado);
    }

    /**
     * Verifica si el contracargo ya fue respondido
     * @return true si el estado es "Respondido"
     */
    public boolean isRespondido() {
        return "Respondido".equalsIgnoreCase(estado);
    }

    /**
     * Verifica si el contracargo fue aceptado
     * @return true si el estado es "Aceptado"
     */
    public boolean isAceptado() {
        return "Aceptado".equalsIgnoreCase(estado);
    }

    /**
     * Verifica si el contracargo está vencido (fecha actual > fecha vencimiento)
     * @return true si está vencido
     */
    public boolean isVencido() {
        return fechaVencimiento != null && LocalDateTime.now().isAfter(fechaVencimiento);
    }

    /**
     * Verifica si el contracargo requiere atención urgente
     * (pendiente y faltan menos de 2 días para el vencimiento)
     * @return true si requiere atención urgente
     */
    public boolean requiereAtencionUrgente() {
        if (!isPendiente() || fechaVencimiento == null) {
            return false;
        }
        LocalDateTime dosDiasAntes = fechaVencimiento.minusDays(2);
        return LocalDateTime.now().isAfter(dosDiasAntes);
    }

    @Override
    public String toString() {
        return "ContracargoDTO{" +
                "numero=" + numero +
                ", estado='" + estado + '\'' +
                ", medio='" + medio + '\'' +
                ", transaccion=" + transaccion +
                ", monto=" + monto +
                ", tarjeta='" + (tarjeta != null ? "****" : "null") + '\'' +
                ", fecha=" + fecha +
                ", fechaVencimiento=" + fechaVencimiento +
                '}';
    }
}

