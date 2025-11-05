package org.transito_seguro.model;



import lombok.Data;

import java.util.Date;

/**
 * Modelo que encapsula el resultado del proceso de conciliación de percepciones.
 *
 * PROPÓSITO:
 * Consolidar métricas y estadísticas del procesamiento de rendiciones,
 * facilitando el monitoreo, logging y reporting del sistema.
 *
 * CONTEXTO:
 * Cuando se procesan rendiciones desde e-Pagos, es crucial tener trazabilidad
 * de cuántas transacciones fueron:
 * - Conciliadas exitosamente con cobranzas
 * - Identificadas como huérfanas (sin cobranza)
 * - Detectadas como duplicadas
 * - Fallaron por errores
 *
 * Este modelo actúa como DTO para transportar esta información entre capas.
 */
@Data
public class ResultadoConciliacion {

    /**
     * Indica si el proceso completó exitosamente.
     * true = sin errores críticos
     * false = hubo errores que impidieron el proceso
     */
    private boolean exitoso;

    /**
     * Fecha y hora de inicio del procesamiento.
     */
    private Date fechaInicio;

    /**
     * Fecha y hora de fin del procesamiento.
     */
    private Date fechaFin;

    /**
     * Total de percepciones procesadas (intentadas).
     */
    private int procesadas;

    /**
     * Percepciones conciliadas exitosamente con cobranzas.
     * Estado: CONCILIADA
     */
    private int conciliadas;

    /**
     * Percepciones sin cobranza correspondiente.
     * Estado: HUERFANA
     * Requieren investigación.
     */
    private int huerfanas;

    /**
     * Percepciones duplicadas (ya existían en BD).
     * Estado: DUPLICADA
     * Se omitieron durante el proceso.
     */
    private int duplicadas;

    /**
     * Percepciones con errores durante el procesamiento.
     * Estado: ERROR
     */
    private int errores;

    /**
     * Mensaje de error si el proceso falló.
     */
    private String mensajeError;

    /**
     * Constructor por defecto que inicializa contadores en cero.
     */
    public ResultadoConciliacion() {
        this.procesadas = 0;
        this.conciliadas = 0;
        this.huerfanas = 0;
        this.duplicadas = 0;
        this.errores = 0;
        this.exitoso = false;
    }

    // ========================================================================
    // MÉTODOS DE INCREMENTO (Thread-safe si se usa synchronized externamente)
    // ========================================================================

    /**
     * Incrementa contador de percepciones procesadas.
     */
    public void incrementarProcesadas() {
        this.procesadas++;
    }

    /**
     * Incrementa contador de percepciones conciliadas.
     */
    public void incrementarConciliadas() {
        this.conciliadas++;
    }

    /**
     * Incrementa contador de percepciones huérfanas.
     */
    public void incrementarHuerfanas() {
        this.huerfanas++;
    }

    /**
     * Incrementa contador de percepciones duplicadas.
     */
    public void incrementarDuplicadas() {
        this.duplicadas++;
    }

    /**
     * Incrementa contador de errores.
     */
    public void incrementarErrores() {
        this.errores++;
    }

    // ========================================================================
    // MÉTODOS CALCULADOS
    // ========================================================================

    /**
     * Calcula la duración del procesamiento en milisegundos.
     *
     * @return Duración en ms, o 0 si no hay fechas
     */
    public long getDuracionMs() {
        if (fechaInicio == null || fechaFin == null) {
            return 0;
        }
        return fechaFin.getTime() - fechaInicio.getTime();
    }

    /**
     * Calcula el porcentaje de conciliación exitosa.
     *
     * @return Porcentaje (0-100) de percepciones conciliadas
     */
    public double getPorcentajeConciliacion() {
        if (procesadas == 0) {
            return 0.0;
        }
        return (conciliadas * 100.0) / procesadas;
    }

    /**
     * Calcula el porcentaje de percepciones huérfanas.
     *
     * @return Porcentaje (0-100) de percepciones huérfanas
     */
    public double getPorcentajeHuerfanas() {
        if (procesadas == 0) {
            return 0.0;
        }
        return (huerfanas * 100.0) / procesadas;
    }

    /**
     * Verifica si hay problemas que requieren atención.
     *
     * @return true si hay huérfanas, duplicadas o errores
     */
    public boolean requiereAtencion() {
        return huerfanas > 0 || duplicadas > 0 || errores > 0;
    }

    /**
     * Genera un resumen en texto del resultado.
     *
     * @return String con resumen legible
     */
    public String obtenerResumen() {
        return String.format(
                "Conciliación: %s | Procesadas: %d | Conciliadas: %d (%.1f%%) | " +
                        "Huérfanas: %d (%.1f%%) | Duplicadas: %d | Errores: %d | Duración: %dms",
                exitoso ? "EXITOSA" : "CON ERRORES",
                procesadas,
                conciliadas,
                getPorcentajeConciliacion(),
                huerfanas,
                getPorcentajeHuerfanas(),
                duplicadas,
                errores,
                getDuracionMs()
        );
    }

    /**
     * Genera un resumen corto para logs.
     *
     * @return String con resumen compacto
     */
    public String obtenerResumenCorto() {
        return String.format(
                "[OK:%d | Huérfanas:%d | Duplicadas:%d | Errores:%d]",
                conciliadas, huerfanas, duplicadas, errores
        );
    }
}
