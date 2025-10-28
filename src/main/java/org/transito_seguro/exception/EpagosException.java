package org.transito_seguro.exception;

public  class EpagosException extends Exception {
    public EpagosException(String mensaje) {
        super(mensaje);
    }

    public EpagosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
