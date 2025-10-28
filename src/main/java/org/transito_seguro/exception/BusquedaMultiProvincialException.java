package org.transito_seguro.exception;


/**
 * Excepción lanzada cuando ocurre un error general en la búsqueda multiprovincial.
 */

public  class BusquedaMultiProvincialException extends RuntimeException {
    public BusquedaMultiProvincialException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}