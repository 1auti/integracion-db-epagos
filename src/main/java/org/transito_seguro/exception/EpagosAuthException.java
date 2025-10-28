package org.transito_seguro.exception;

import org.transito_seguro.service.EpagosClientService;

public  class EpagosAuthException extends EpagosException {
    public EpagosAuthException(String mensaje) {
        super(mensaje);
    }
}