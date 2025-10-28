package org.transito_seguro.exception;

/**
 * Excepción lanzada cuando hay un timeout en la comunicación con e-pagos.
 */
public  class EpagosTimeoutException extends RuntimeException {
    public EpagosTimeoutException(String mensaje) {
        super(mensaje);
    }
}