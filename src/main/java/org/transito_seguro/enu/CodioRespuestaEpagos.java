package org.transito_seguro.enu;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CodioRespuestaEpagos {
    // Respuestas de Rendiciones
    RENDICIONES_OK("05001", "Rendiciones devueltas"),
    RENDICIONES_TOKEN_INVALIDO("05002", "Error al validar el token"),
    RENDICIONES_ERROR_INTERNO("05003", "Error interno al intentar devolver las rendiciones"),
    RENDICIONES_RANGO_EXCEDIDO("05004", "El rango de fechas supera el límite permitido"),
    RENDICIONES_PARAMETRO_INVALIDO("05005", "Error al validar el parámetro"),

    // Respuestas de Contracargos
    CONTRACARGOS_OK("06001", "Contracargos devueltos"),
    CONTRACARGOS_TOKEN_INVALIDO("06002", "Error al validar el token"),
    CONTRACARGOS_ERROR_INTERNO("06003", "Error interno al intentar devolver los contracargos"),
    CONTRACARGOS_RANGO_INCORRECTO("06004", "El rango de fechas no es correcto"),
    CONTRACARGOS_PARAMETRO_INVALIDO("06005", "Error al validar el parámetro"),
    CONTRACARGOS_VERSION_INVALIDA("06006", "Versión inválida del protocolo");

    private final String codigo;
    private final String descripcion;

}
