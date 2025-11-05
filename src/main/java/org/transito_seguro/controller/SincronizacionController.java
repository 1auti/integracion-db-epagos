package org.transito_seguro.controller;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.transito_seguro.exception.EpagosException;
import org.transito_seguro.model.ResultadoSincronizacion;
import org.transito_seguro.service.SincronizacionService;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * REST Controller para sincronización de rendiciones de e-Pagos.
 *
 * RESPONSABILIDAD:
 * ================
 * - Exponer endpoint REST para sincronizar rendiciones de una provincia
 * - Coordinar llamada a SincronizacionService
 * - Validar parámetros de entrada
 * - Formatear respuestas HTTP estándar
 * - Manejo centralizado de errores
 *
 * ARQUITECTURA:
 * =============
 * Controller (REST API)
 *     ↓
 * SincronizacionService (coordinador estratégico)
 *     ↓
 * ├─ EpagosClientService (comunicación con e-Pagos)
 * ├─ RendicionService (procesamiento de rendiciones)
 * └─ ContracargoService (procesamiento de contracargos)
 *
 * ENDPOINT:
 * =========
 * POST /api/sincronizacion/rendiciones - Sincronizar rendiciones de una provincia
 *
 * FLUJO COMPLETO:
 * ===============
 * 1. Controller recibe request con código de provincia y días atrás
 * 2. Valida parámetros
 * 3. Llama a SincronizacionService.sincronizarProvincia()
 * 4. SincronizacionService:
 *    - Obtiene credenciales de la provincia desde CredencialesFactory
 *    - Consulta rendiciones en e-Pagos
 *    - Procesa y actualiza cobranzas en BD
 *    - Consulta contracargos en e-Pagos (opcional)
 *    - Registra contracargos en BD
 * 5. Retorna ResultadoSincronizacion con métricas
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
@RestController
@RequestMapping("/api/sincronizacion")
@Slf4j
public class SincronizacionController {

    // ========================================================================
    // DEPENDENCIAS
    // ========================================================================

    /**
     * Servicio coordinador de sincronización.
     * Gestiona todo el flujo: obtener credenciales, consultar e-Pagos,
     * procesar rendiciones y contracargos, actualizar BD.
     */
    @Autowired
    private SincronizacionService sincronizacionService;

    // ========================================================================
    // ENDPOINT - SINCRONIZAR RENDICIONES
    // ========================================================================

    /**
     * Sincroniza rendiciones y contracargos de una provincia.
     *
     * ENDPOINT:
     * POST /api/sincronizacion/rendiciones
     *
     * BODY (JSON):
     * {
     *   "codigoProvincia": "Buenos Aires",
     *   "diasAtras": 7
     * }
     *
     * PARÁMETROS:
     * - codigoProvincia: Código de la provincia a sincronizar (ej: "Buenos Aires", "Chaco")
     * - diasAtras: Días hacia atrás para consultar (1-90, default: 7)
     *
     * PROCESO COMPLETO QUE SE EJECUTA:
     * 1. SincronizacionService obtiene credenciales de la provincia desde BD
     * 2. Consulta rendiciones en e-Pagos para el rango de fechas
     * 3. Procesa rendiciones y actualiza cobranzas en BD provincial
     * 4. Consulta contracargos en e-Pagos (si está habilitado)
     * 5. Registra contracargos en BD
     * 6. Retorna métricas: rendiciones obtenidas, cobranzas actualizadas, contracargos, etc.
     *
     * EJEMPLO DE USO CON CURL:
     * curl -X POST "http://localhost:8081/api/sincronizacion/rendiciones" \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "codigoProvincia": "Buenos Aires",
     *     "diasAtras": 7
     *   }'
     *
     * RESPUESTA EXITOSA (200):
     * {
     *   "success": true,
     *   "message": "Sincronización completada exitosamente",
     *   "data": {
     *     "provincia": "Buenos Aires",
     *     "fechaInicio": "2024-01-20T10:00:00",
     *     "fechaFin": "2024-01-20T10:05:32",
     *     "duracionSegundos": 332,
     *     "rendicionesObtenidas": 150,
     *     "cobranzasActualizadas": 148,
     *     "contracargosObtenidos": 5,
     *     "contracargosRegistrados": 5,
     *     "exitoso": true,
     *     "errores": []
     *   },
     *   "timestamp": "2024-01-20T10:05:32"
     * }
     *
     * RESPUESTA CON ERRORES PARCIALES (206):
     * {
     *   "success": true,
     *   "message": "Sincronización completada con errores parciales",
     *   "data": {
     *     "provincia": "Buenos Aires",
     *     "rendicionesObtenidas": 150,
     *     "cobranzasActualizadas": 145,
     *     "exitoso": false,
     *     "errores": ["No se pudo actualizar cobranza INF-001", "Error en contracargo CON-002"]
     *   },
     *   "timestamp": "2024-01-20T10:05:32"
     * }
     *
     * RESPUESTA ERROR (400):
     * {
     *   "success": false,
     *   "message": "Parámetros inválidos: diasAtras debe estar entre 1 y 90",
     *   "timestamp": "2024-01-20T10:00:00"
     * }
     *
     * RESPUESTA ERROR (503):
     * {
     *   "success": false,
     *   "message": "Error de comunicación con e-Pagos: Connection timeout",
     *   "timestamp": "2024-01-20T10:00:00"
     * }
     *
     * @param request Request con código de provincia y días atrás
     * @return ResponseEntity con resultado de sincronización
     */
    @PostMapping("/rendiciones")
    public ResponseEntity<ResultadoSincronizacion> sincronizarRendiciones(
            @RequestBody SincronizarRendicionesRequest request) {

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔄 REQUEST: Sincronizar Rendiciones");
        log.info("   - Provincia: {}", request.getCodigoProvincia());
        log.info("   - Días atrás: {}", request.getDiasAtras());
        log.info("═══════════════════════════════════════════════════════════════");

        try {
            // VALIDACIÓN 1: Verificar parámetros requeridos
            validarParametrosRequest(request);

            // VALIDACIÓN 2: Verificar rango de días
            if (request.getDiasAtras() < 1 || request.getDiasAtras() > 90) {
                log.warn("❌ diasAtras fuera de rango: {}", request.getDiasAtras());
                return ResponseEntity.badRequest().build();
            }

            // EJECUTAR SINCRONIZACIÓN COMPLETA
            log.info("▶️  Iniciando sincronización completa...");
            log.info("   → El servicio obtendrá las credenciales automáticamente");
            log.info("   → Consultará rendiciones en e-Pagos");
            log.info("   → Actualizará cobranzas en BD");
            log.info("   → Consultará y registrará contracargos (si está habilitado)");

            ResultadoSincronizacion resultado = sincronizacionService.sincronizarProvincia(
                    request.getCodigoProvincia(),
                    request.getDiasAtras()
            );

            // EVALUAR RESULTADO
            if (resultado.isExitoso()) {
                log.info("✅ Sincronización exitosa");
                log.info("   ✓ Rendiciones obtenidas: {}", resultado.getRendicionesObtenidas());
                log.info("   ✓ Cobranzas actualizadas: {}", resultado.getCobranzasActualizadas());
                log.info("   ✓ Contracargos obtenidos: {}", resultado.getContracargosObtenidos());

                return ResponseEntity.ok(resultado);
            } else {
                log.warn("⚠️  Sincronización completada con errores parciales");
                log.warn("   - Errores: {}", resultado.getErrores());

                return ResponseEntity
                        .status(HttpStatus.PARTIAL_CONTENT)
                        .body(resultado);
            }

        } catch (IllegalArgumentException e) {
            log.error("❌ Parámetros inválidos: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .build();

        } catch (EpagosException e) {
            log.error("❌ Error de e-Pagos: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error inesperado: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - VALIDACIONES
    // ========================================================================

    /**
     * Valida que todos los parámetros requeridos estén presentes.
     *
     * @param request Request a validar
     * @throws IllegalArgumentException si algún parámetro es inválido
     */
    private void validarParametrosRequest(SincronizarRendicionesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El request no puede ser nulo");
        }

        if (request.getCodigoProvincia() == null || request.getCodigoProvincia().trim().isEmpty()) {
            throw new IllegalArgumentException("El código de provincia es requerido");
        }

        if (request.getDiasAtras() == null) {
            throw new IllegalArgumentException("El parámetro diasAtras es requerido");
        }
    }

    // ========================================================================
    // DTO INTERNO PARA REQUEST
    // ========================================================================

    /**
     * DTO para request de sincronización de rendiciones.
     */
    @Data
    public static class SincronizarRendicionesRequest {

        @NotBlank(message = "El código de provincia es requerido")
        private String codigoProvincia;

        @Min(value = 1, message = "diasAtras debe ser al menos 1")
        @Max(value = 90, message = "diasAtras no puede superar 90")
        private Integer diasAtras = 7;
    }
}
