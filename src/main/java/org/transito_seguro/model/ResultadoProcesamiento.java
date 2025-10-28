package org.transito_seguro.model;

public class ResultadoProcesamiento {

    private boolean exitoso;
    private boolean requiereAtencion;
    private String mensajeError;

    // Getters y Setters
    public boolean isExitoso() { return exitoso; }
    public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

    public boolean isRequiereAtencion() { return requiereAtencion; }
    public void setRequiereAtencion(boolean requiereAtencion) {
        this.requiereAtencion = requiereAtencion;
    }

    public String getMensajeError() { return mensajeError; }
    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }
}
