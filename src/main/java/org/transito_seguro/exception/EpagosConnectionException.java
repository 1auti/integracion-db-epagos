package org.transito_seguro.exception;

/**
 * Excepción lanzada cuando hay un error de conexión con e-pagos.
 */
public  class EpagosConnectionException extends RuntimeException {
    public EpagosConnectionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
