package org.transito_seguro.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo para encapsular el resultado de una sincronización con e-Pagos.
 *
 * Esta clase actúa como Data Transfer Object (DTO) para consolidar
 * todas las métricas y resultados de un proceso de sincronización.
 *
 * RESPONSABILIDADES:
 * - Almacenar métricas de rendiciones (obtenidas, procesadas, actualizadas)
 * - Almacenar métricas de contracargos (obtenidos, procesados)
 * - Indicar estado de éxito/fallo de la operación
 * - Registrar mensajes de error si los hay
 * - Medir duración de la operación
 *
 * USO:
 * - Retorno de métodos de sincronización
 * - Logging de resultados
 * - Auditoría de operaciones
 * - Métricas para dashboards
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
@Data
public class ResultadoSincronizacion {

    // ========================================================================
    // INFORMACIÓN GENERAL
    // ========================================================================

    /**
     * Código de la provincia sincronizada (ej: "PBA", "MDA", "CHACO").
     */
    private String codigoProvincia;

    /**
     * Indica si la sincronización fue exitosa.
     * true = completada sin errores críticos
     * false = falló o tuvo errores que impidieron completar
     */
    private boolean exitoso;

    /**
     * Mensaje de error en caso de fallo.
     * Null si la operación fue exitosa.
     */
    private String mensajeError;

    /**
     * Lista de errores no críticos que no detuvieron la sincronización.
     * Útil para warnings o errores parciales.
     */
    private List<String> errores;

    /**
     * Duración total de la sincronización en milisegundos.
     */
    private long duracionMs;

    // ========================================================================
    // MÉTRICAS DE RENDICIONES
    // ========================================================================

    /**
     * Cantidad de rendiciones obtenidas desde e-Pagos.
     * Representa los reportes de pago recibidos.
     */
    private int rendicionesObtenidas;

    /**
     * Cantidad de cobranzas actualizadas en la base de datos.
     * Representa las infracciones que se marcaron como pagadas.
     */
    private int cobranzasActualizadas;

    // ========================================================================
    // MÉTRICAS DE CONTRACARGOS
    // ========================================================================

    /**
     * Cantidad de contracargos obtenidos desde e-Pagos.
     * Representa los reclamos de usuarios que desconocen pagos.
     */
    private int contracargosObtenidos;

    /**
     * Cantidad de contracargos procesados y registrados en BD.
     * Puede ser menor que contracargosObtenidos si algunos son duplicados.
     */
    private int contracargosProcesados;

    // ========================================================================
    // CONSTRUCTORES
    // ========================================================================

    /**
     * Constructor por defecto.
     * Inicializa la lista de errores vacía.
     */
    public ResultadoSincronizacion() {
        this.errores = new ArrayList<>();
        this.exitoso = false;
    }

    /**
     * Constructor con código de provincia.
     *
     * @param codigoProvincia Código de la provincia
     */
    public ResultadoSincronizacion(String codigoProvincia) {
        this();
        this.codigoProvincia = codigoProvincia;
    }

    // ========================================================================
    // MÉTODOS DE UTILIDAD
    // ========================================================================

    /**
     * Agrega un error no crítico a la lista.
     *
     * @param error Mensaje de error
     */
    public void agregarError(String error) {
        if (this.errores == null) {
            this.errores = new ArrayList<>();
        }
        this.errores.add(error);
    }

    /**
     * Verifica si hubo errores durante la sincronización.
     *
     * @return true si hay errores registrados
     */
    public boolean tieneErrores() {
        return !errores.isEmpty() || mensajeError != null;
    }

    /**
     * Obtiene el total de items procesados (rendiciones + contracargos).
     *
     * @return Total de items procesados
     */
    public int getTotalProcesado() {
        return cobranzasActualizadas + contracargosProcesados;
    }

    /**
     * Obtiene una representación textual resumida del resultado.
     *
     * Útil para logging y debugging.
     *
     * @return String con resumen del resultado
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ResultadoSincronizacion{");
        sb.append("provincia=").append(codigoProvincia);
        sb.append(", exitoso=").append(exitoso);
        sb.append(", rendiciones=").append(rendicionesObtenidas);
        sb.append(", cobranzas=").append(cobranzasActualizadas);
        sb.append(", contracargos=").append(contracargosObtenidos);
        sb.append(", duracion=").append(duracionMs).append("ms");

        if (mensajeError != null) {
            sb.append(", error='").append(mensajeError).append("'");
        }

        sb.append("}");
        return sb.toString();
    }
}