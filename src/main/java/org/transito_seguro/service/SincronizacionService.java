package org.transito_seguro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transito_seguro.dto.contracargos.ContracargoDTO;
import org.transito_seguro.dto.rendiciones.RendicionDTO;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.model.ResultadoSincronizacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Servicio coordinador principal para la sincronización con e-Pagos.
 *
 * PATRÓN DE DISEÑO: FACADE + STRATEGY
 * - Facade: Simplifica la interacción con múltiples servicios (EpagosClient, Rendicion, Contracargo)
 * - Strategy: Permite diferentes estrategias de sincronización (solo rendiciones, solo contracargos, ambos)
 *
 * RESPONSABILIDADES:
 * 1. Coordinar la sincronización completa de rendiciones y contracargos
 * 2. Gestionar el flujo de datos entre e-Pagos y la base de datos local
 * 3. Orquestar llamadas a servicios especializados en el orden correcto
 * 4. Consolidar resultados y métricas de sincronización
 * 5. Manejar errores de forma centralizada con rollback si es necesario
 *
 * FLUJO DE SINCRONIZACIÓN:
 * 1. Validar parámetros de entrada (provincia, fechas)
 * 2. Obtener datos de e-Pagos (rendiciones y/o contracargos) vía EpagosClientService
 * 3. Procesar datos obtenidos (actualizar BD) vía servicios especializados
 * 4. Consolidar resultados y métricas
 * 5. Retornar resultado estructurado
 *
 * ARQUITECTURA:
 *
 *  SincronizacionScheduler (Trigger automático)
 *           ↓
 *  SincronizacionService (Coordinador - ESTE SERVICIO)
 *           ↓
 *    ┌──────┴──────┐
 *    ↓             ↓
 * EpagosClientService (Consulta API externa)
 *    │             │
 *    │  rendiciones│  contracargos
 *    ↓             ↓
 * RendicionService  ContracargoService (Procesamiento y BD)
 *
 */
@Service
@Slf4j
public class SincronizacionService {



    /**
     * Cliente para comunicación con API de e-Pagos.
     * Responsabilidad: Obtener datos desde sistema externo (rendiciones, contracargos, token)
     */
    @Autowired
    private EpagosClientService epagosClientService;

    /**
     * Servicio de procesamiento de rendiciones.
     * Responsabilidad: Actualizar cobranzas en BD con datos de rendiciones
     */
    @Autowired
    private RendicionService rendicionService;

    /**
     * Servicio de procesamiento de contracargos.
     * Responsabilidad: Registrar y gestionar contracargos en BD
     */
    @Autowired
    private ContracargoService contracargoService;

    // ========================================================================
    // CONFIGURACIÓN
    // ========================================================================

    /**
     * Días hacia atrás para consultar por defecto (configurable desde application.yml).
     * Valor por defecto: 7 días (1 semana)
     */
    @Value("${sincronizacion.rendiciones.dias-atras:7}")
    private int diasAtras;

    /**
     * Flag para habilitar/deshabilitar sincronización de contracargos.
     * Por defecto deshabilitado hasta que se implemente ContracargoService
     */
    @Value("${sincronizacion.contracargos.enabled:false}")
    private boolean contracargoEnabled;

    // ========================================================================
    // MÉTODOS PÚBLICOS - SINCRONIZACIÓN COMPLETA
    // ========================================================================

    /**
     * Sincroniza RENDICIONES Y CONTRACARGOS de una provincia para los últimos N días.
     *
     * Este es el método principal para sincronización completa.
     * Coordina todo el flujo: consulta + procesamiento para ambos tipos de datos.
     *
     * FLUJO COMPLETO:
     * 1. Validar parámetros
     * 2. Calcular rango de fechas
     * 3. Sincronizar rendiciones (consulta e-Pagos → procesar BD)
     * 4. Sincronizar contracargos (consulta e-Pagos → procesar BD) - si está habilitado
     * 5. Consolidar métricas
     *
     * TRANSACCIONALIDAD:
     * - @Transactional: Si alguna operación falla, se hace rollback de toda la transacción
     * - Garantiza consistencia de datos (todo o nada)
     *
     * CASOS DE USO:
     * - Llamado desde SincronizacionScheduler (cron diario)
     * - Llamado desde API REST para sincronización manual
     * - Reproceso de datos de una provincia
     *
     * @param codigoProvincia Código de la provincia (ej: "PBA", "MDA", "CHACO")
     * @param diasAtras Cantidad de días hacia atrás para consultar (1-90)
     * @return ResultadoSincronizacion con métricas consolidadas
     * @throws EpagosException si hay error de comunicación con e-Pagos
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    @Transactional
    public ResultadoSincronizacion sincronizarProvincia(
            String codigoProvincia,
            int diasAtras) throws EpagosException {

        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN COMPLETA - INICIO                            ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincia:      {}                                          ║", codigoProvincia);
        log.info("║  Días atrás:     {}                                          ║", diasAtras);
        log.info("║  Contracargos:   {}                                          ║",
                contracargoEnabled ? "HABILITADO" : "DESHABILITADO");
        log.info("╚═══════════════════════════════════════════════════════════════╝");

        // Validar parámetros de entrada
        validarParametros(codigoProvincia, diasAtras);

        // Calcular rango de fechas
        LocalDate fechaHasta = LocalDate.now();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);

        log.info("→ Rango de fechas: {} a {}", fechaDesde, fechaHasta);

        // Inicializar resultado
        ResultadoSincronizacion resultado = new ResultadoSincronizacion(codigoProvincia);
        long tiempoInicio = System.currentTimeMillis();

        try {
            // ================================================================
            // PASO 1: SINCRONIZAR RENDICIONES
            // ================================================================
            log.info("─────────────────────────────────────────────────────────────");
            log.info("▶ PASO 1/2: Sincronizando RENDICIONES");
            log.info("─────────────────────────────────────────────────────────────");

            int cobranzasActualizadas = sincronizarRendiciones(
                    codigoProvincia,
                    fechaDesde,
                    fechaHasta,
                    resultado
            );

            resultado.setCobranzasActualizadas(cobranzasActualizadas);
            log.info("✓ Rendiciones completadas: {} cobranzas actualizadas", cobranzasActualizadas);

            // ================================================================
            // PASO 2: SINCRONIZAR CONTRACARGOS (si está habilitado)
            // ================================================================
            if (contracargoEnabled) {
                log.info("─────────────────────────────────────────────────────────────");
                log.info("▶ PASO 2/2: Sincronizando CONTRACARGOS");
                log.info("─────────────────────────────────────────────────────────────");

                int contracargosProcesados = sincronizarContracargos(
                        codigoProvincia,
                        fechaDesde,
                        fechaHasta,
                        resultado
                );

                resultado.setContracargosProcesados(contracargosProcesados);
                log.info("✓ Contracargos completados: {} procesados", contracargosProcesados);
            } else {
                log.debug("⊗ Contracargos deshabilitados en configuración");
            }

            // ================================================================
            // FINALIZACIÓN
            // ================================================================
            long duracion = System.currentTimeMillis() - tiempoInicio;
            resultado.setDuracionMs(duracion);
            resultado.setExitoso(true);

            logResumenFinal(resultado, duracion);

            return resultado;

        } catch (EpagosException e) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  ERROR EN SINCRONIZACIÓN CON E-PAGOS                         ║");
            log.error("╠═══════════════════════════════════════════════════════════════╣");
            log.error("║  Provincia: {}                                               ║", codigoProvincia);
            log.error("║  Error: {}                                                   ║", e.getMessage());
            log.error("╚═══════════════════════════════════════════════════════════════╝");

            resultado.setExitoso(false);
            resultado.setMensajeError("Error e-Pagos: " + e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  ERROR CRÍTICO EN SINCRONIZACIÓN                             ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            log.error("Error inesperado al sincronizar provincia {}: {}",
                    codigoProvincia, e.getMessage(), e);

            resultado.setExitoso(false);
            resultado.setMensajeError("Error interno: " + e.getMessage());

            throw new EpagosException(
                    "Error crítico al sincronizar provincia " + codigoProvincia,
                    e
            );
        }
    }

    /**
     * Sobrecarga del método principal usando el valor por defecto de días.
     *
     * Útil para llamadas simples sin especificar días.
     *
     * @param codigoProvincia Código de la provincia
     * @return ResultadoSincronizacion con métricas
     * @throws EpagosException si hay error
     */
    @Transactional
    public ResultadoSincronizacion sincronizarProvincia(String codigoProvincia)
            throws EpagosException {
        return sincronizarProvincia(codigoProvincia, this.diasAtras);
    }



    // ========================================================================
    // MÉTODOS PRIVADOS - SINCRONIZACIÓN ESPECIALIZADA
    // ========================================================================

    /**
     * Sincroniza SOLO rendiciones para una provincia.
     *
     * TEMPLATE METHOD PATTERN:
     * Este método define el flujo estándar de sincronización:
     * 1. Consultar datos desde e-Pagos (EpagosClientService)
     * 2. Validar datos obtenidos
     * 3. Procesar y actualizar BD (RendicionService)
     * 4. Actualizar métricas del resultado
     *
     * @param codigoProvincia Código de provincia
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @param resultado Objeto para acumular métricas
     * @return Cantidad de cobranzas actualizadas
     * @throws EpagosException si hay error
     */
    private int sincronizarRendiciones(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            ResultadoSincronizacion resultado) throws EpagosException {

        try {
            // PASO 1: CONSULTAR RENDICIONES desde e-Pagos
            log.debug("  1. Consultando rendiciones en e-Pagos...");

            List<RendicionDTO> rendiciones = epagosClientService.obtenerRendiciones(
                    codigoProvincia,
                    convertirADate(fechaDesde),
                    convertirADate(fechaHasta)
            );

            // PASO 2: VALIDAR respuesta
            if (rendiciones == null) {
                rendiciones = new ArrayList<>();
            }

            int cantidadRendiciones = rendiciones.size();
            resultado.setRendicionesObtenidas(cantidadRendiciones);

            log.info("  ✓ Rendiciones obtenidas: {}", cantidadRendiciones);

            // Si no hay rendiciones, retornar sin procesar
            if (cantidadRendiciones == 0) {
                log.info("  ⊗ No hay rendiciones para procesar");
                return 0;
            }

            // PASO 3: PROCESAR RENDICIONES y actualizar BD
            log.debug("  2. Procesando rendiciones y actualizando BD...");

            int cobranzasActualizadas = rendicionService.procesarRendiciones(
                    codigoProvincia,
                    rendiciones
            );

            log.info("  ✓ Cobranzas actualizadas: {}", cobranzasActualizadas);

            return cobranzasActualizadas;

        } catch (EpagosException e) {
            log.error("  ✗ Error al sincronizar rendiciones: {}", e.getMessage());
            resultado.agregarError("Rendiciones: " + e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("  ✗ Error inesperado en rendiciones: {}", e.getMessage(), e);
            resultado.agregarError("Rendiciones: Error inesperado");
            throw new EpagosException("Error al procesar rendiciones", e);
        }
    }

    /**
     * Sincroniza SOLO contracargos para una provincia.
     *
     * Similar a sincronizarRendiciones pero para contracargos.
     * Actualmente en implementación básica.
     *
     * FLUJO:
     * 1. Consultar contracargos desde e-Pagos (EpagosClientService)
     * 2. Validar datos obtenidos
     * 3. Procesar y registrar en BD (ContracargoService)
     * 4. Actualizar métricas
     *
     * @param codigoProvincia Código de provincia
     * @param fechaDesde Fecha inicial
     * @param fechaHasta Fecha final
     * @param resultado Objeto para acumular métricas
     * @return Cantidad de contracargos procesados
     * @throws EpagosException si hay error
     */
    private int sincronizarContracargos(
            String codigoProvincia,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            ResultadoSincronizacion resultado) throws EpagosException {

        try {
            // PASO 1: CONSULTAR CONTRACARGOS desde e-Pagos
            log.debug("  1. Consultando contracargos en e-Pagos...");

            List<ContracargoDTO> contracargos = epagosClientService.obtenerContracargos(
                    codigoProvincia,
                    convertirADate(fechaDesde),
                    convertirADate(fechaHasta)
            );

            // PASO 2: VALIDAR respuesta
            if (contracargos == null) {
                contracargos = new ArrayList<>();
            }

            int cantidadContracargos = contracargos.size();
            resultado.setContracargosObtenidos(cantidadContracargos);

            log.info("  ✓ Contracargos obtenidos: {}", cantidadContracargos);

            // Si no hay contracargos, retornar sin procesar
            if (cantidadContracargos == 0) {
                log.info("  ⊗ No hay contracargos para procesar");
                return 0;
            }

            // PASO 3: PROCESAR CONTRACARGOS y registrar en BD
            log.debug("  2. Procesando contracargos y actualizando BD...");

            // TODO: Implementar cuando ContracargoService esté completo
            // int contracargosProcesados = contracargoService.procesarContracargos(
            //         codigoProvincia,
            //         contracargos
            // );

            // Implementación temporal - solo logging
            log.warn("  ⚠ ContracargoService aún no implementado - solo se registran en log");
            int contracargosProcesados = contracargos.size();

            log.info("  ✓ Contracargos procesados: {}", contracargosProcesados);

            return contracargosProcesados;

        } catch (EpagosException e) {
            log.error("  ✗ Error al sincronizar contracargos: {}", e.getMessage());
            resultado.agregarError("Contracargos: " + e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("  ✗ Error inesperado en contracargos: {}", e.getMessage(), e);
            resultado.agregarError("Contracargos: Error inesperado");
            throw new EpagosException("Error al procesar contracargos", e);
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - VALIDACIONES
    // ========================================================================

    /**
     * Valida los parámetros de entrada para sincronización.
     *
     * VALIDACIONES:
     * - Código de provincia no nulo ni vacío
     * - Días atrás entre 1 y 90 (límite de e-Pagos)
     *
     * @param codigoProvincia Código de provincia
     * @param diasAtras Días hacia atrás
     * @throws IllegalArgumentException si algún parámetro es inválido
     */
    private void validarParametros(String codigoProvincia, int diasAtras) {
        if (codigoProvincia == null || codigoProvincia.trim().isEmpty()) {
            throw new IllegalArgumentException("Código de provincia no puede ser nulo o vacío");
        }

        if (diasAtras < 1) {
            throw new IllegalArgumentException("Días atrás debe ser al menos 1");
        }

        if (diasAtras > 90) {
            throw new IllegalArgumentException(
                    "Días atrás no puede ser mayor a 90 (límite de e-Pagos)");
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - UTILIDADES
    // ========================================================================

    /**
     * Convierte LocalDate a Date para compatibilidad con código legacy.
     *
     * Java 8 introduce java.time.LocalDate pero muchas APIs legacy usan java.util.Date.
     * Este método facilita la conversión.
     *
     * @param localDate Fecha en formato LocalDate
     * @return Fecha en formato Date
     */
    private Date convertirADate(LocalDate localDate) {
        return java.sql.Date.valueOf(localDate);
    }

    /**
     * Log de resumen final de sincronización.
     *
     * @param resultado Resultado de la sincronización
     * @param duracion Duración en milisegundos
     */
    private void logResumenFinal(ResultadoSincronizacion resultado, long duracion) {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║  SINCRONIZACIÓN COMPLETA - FINALIZADA                         ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Provincia:                {}                                 ║", resultado.getCodigoProvincia());
        log.info("║  Estado:                   {}                                 ║",
                resultado.isExitoso() ? "EXITOSO" : "FALLIDO");
        log.info("║  Duración:                 {} ms ({} seg)                     ║",
                duracion, duracion / 1000);
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  RENDICIONES:                                                 ║");
        log.info("║    • Obtenidas:            {}                                 ║",
                resultado.getRendicionesObtenidas());
        log.info("║    • Cobranzas actualizadas: {}                               ║",
                resultado.getCobranzasActualizadas());

        if (contracargoEnabled) {
            log.info("╠═══════════════════════════════════════════════════════════════╣");
            log.info("║  CONTRACARGOS:                                               ║");
            log.info("║    • Obtenidos:            {}                                ║",
                    resultado.getContracargosObtenidos());
            log.info("║    • Procesados:           {}                                ║",
                    resultado.getContracargosProcesados());
        }

        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - UTILIDADES Y HEALTH CHECKS
    // ========================================================================

    /**
     * Verifica la conectividad con e-Pagos.
     *
     * Útil para:
     * - Health checks del sistema
     * - Validación antes de operaciones masivas
     * - Diagnóstico de problemas
     *
     * @return true si e-Pagos está disponible y responde correctamente
     */
    public boolean verificarConectividad() {
        try {
            log.debug("Verificando conectividad con e-Pagos...");

            // Intentar obtener un token válido
            String token = epagosClientService.obtenerTokenValido();

            boolean conectado = (token != null && !token.isEmpty());

            if (conectado) {
                log.debug("✓ e-Pagos está disponible");
            } else {
                log.warn("✗ e-Pagos no está disponible o no responde");
            }

            return conectado;

        } catch (Exception e) {
            log.error("Error al verificar conectividad con e-Pagos: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el estado del token de autenticación.
     *
     * @return Información sobre el token actual (validez, expiración)
     */
    public String obtenerEstadoToken() {
        try {
            boolean tieneTokenValido = epagosClientService.tieneTokenValido();

            if (tieneTokenValido) {
                return "Token válido hasta: " + epagosClientService.getTokenExpiracion();
            } else {
                return "Token inválido o expirado";
            }

        } catch (Exception e) {
            return "Error al verificar token: " + e.getMessage();
        }
    }
}