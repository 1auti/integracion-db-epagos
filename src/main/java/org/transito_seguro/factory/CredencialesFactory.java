package org.transito_seguro.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.transito_seguro.config.ProvinciaMapping;
import org.transito_seguro.dto.VariableConfigDTO;
import org.transito_seguro.dto.rendiciones.request.RendicionesRequestDTO;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory para cargar credenciales de e-Pagos desde cada base de datos.
 *
 * REFACTORIZADO para trabajar con RendicionesRequestDTO como estructura
 * de credenciales, eliminando CredencialesDTO.
 *
 * Cambios principales:
 * ✅ Caché por provincia con credenciales base (sin fechas)
 * ✅ Parseo JSON actualizado para nueva estructura de campos
 * ✅ Validaciones ajustadas a los campos del nuevo DTO
 *
 * Responsabilidades:
 * - Carga inicial de credenciales desde base de datos al iniciar la aplicación
 * - Caché en memoria de credenciales por provincia
 * - Parseo de JSON desde campo `variables`y convenio en tabla `concesion_configuracion_convenio`
 * - Validación de integridad de credenciales
 * - Provisión de credenciales a servicios que consultan e-Pagos
 *
 * Nota: Las credenciales base NO incluyen fechas
 *       Esos campos se deben completar al momento de hacer la consulta.
 */
@Component
@Slf4j
public class CredencialesFactory {

    // ========================================================================
    // INYECCIÓN DE DEPENDENCIAS
    // ========================================================================

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProvinciaMapping provinciaMapping;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================================================
    // CACHÉ DE CREDENCIALES
    // ========================================================================

    /**
     * Caché de credenciales por provincia.
     * Key: nombre de provincia (ej: "buenosAires")
     * Value: RendicionesRequestDTO con credenciales base (sin fechas ni convenios)
     *
     * Thread-safe con ConcurrentHashMap para soportar acceso concurrente.
     */
    private final Map<String, RendicionesRequestDTO> credencialesCache = new ConcurrentHashMap<>();

    // ========================================================================
    // INICIALIZACIÓN
    // ========================================================================

    /**
     * Carga todas las credenciales al iniciar la aplicación.
     *
     * Proceso:
     * 1. Obtiene el mapping de provincias configuradas
     * 2. Para cada provincia, intenta cargar sus credenciales desde su BD
     * 3. Valida las credenciales cargadas
     * 4. Almacena en caché solo las credenciales válidas
     * 5. Loguea resumen de credenciales cargadas exitosamente
     *
     * Se ejecuta automáticamente después de la construcción del bean (@PostConstruct).
     */
    @PostConstruct
    public void inicializar() {
        log.info("🔧 Inicializando carga de credenciales e-Pagos...");

        Map<String, String> mapping = provinciaMapping.getMapping();
        if (mapping == null || mapping.isEmpty()) {
            log.error("❌ No hay provincias configuradas en ProvinciaMapping");
            return;
        }

        int exitosas = 0;
        int fallidas = 0;

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String provincia = entry.getKey();
            String datasource = entry.getValue();

            try {
                RendicionesRequestDTO creds = cargarCredenciales(provincia, datasource);

                if (creds != null && esValido(creds)) {
                    credencialesCache.put(provincia, creds);
                    exitosas++;
                    log.info("✓ {} - Organismo: {}, Usuario: {}",
                            provincia, creds.getIdOrganismo(), creds.getIdUsuario());
                } else {
                    fallidas++;
                    log.warn("✗ {} - Credenciales inválidas o incompletas", provincia);
                }

            } catch (Exception e) {
                fallidas++;
                log.error("✗ {} - Error al cargar credenciales: {}", provincia, e.getMessage());
            }
        }

        log.info("📦 Credenciales cargadas: {} exitosas, {} fallidas de {} totales",
                exitosas, fallidas, mapping.size());

        if (exitosas == 0) {
            log.error("⚠️ ADVERTENCIA: No se pudo cargar ninguna credencial. El sistema no podrá consultar e-Pagos.");
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - CARGA DE CREDENCIALES
    // ========================================================================

    /**
     * Carga credenciales de una provincia desde su base de datos.
     *
     * Proceso:
     * 1. Obtiene el DataSource de la provincia desde el ApplicationContext
     * 2. Consulta la tabla `concesion_configuracion_convenio`
     * 3. Extrae el campo JSON `variables`
     * 4. Parsea el JSON a RendicionesRequestDTO
     *
     * @param provincia Nombre de la provincia
     * @param datasource Nombre del bean DataSource
     * @return RendicionesRequestDTO con credenciales base
     * @throws Exception si no se puede cargar o parsear
     */
    private RendicionesRequestDTO cargarCredenciales(String provincia, String datasource) throws Exception {

        log.debug("Cargando credenciales para provincia: {} desde datasource: {}", provincia, datasource);

        // Obtener DataSource del contexto de Spring
        DataSource ds = applicationContext.getBean(datasource + "DataSource", DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // Query para obtener configuración activa
        String sql = "SELECT id_concesion, variables, convenio FROM concesion_configuracion_convenio " +
                "WHERE is_activo = true " +
                "AND variables IS NOT NULL " +
                "AND convenio IS NOT NULL " +
                "LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        if (rows.isEmpty()) {
            throw new Exception("No hay configuración activa en la base de datos");
        }

        Map<String,Object> row = rows.get(0);

        // Extraer JSON del campo 'variables'
        String json = (String) row.get("variables");
        String convenio = (String) row.get("convenio");

        if (json == null || json.trim().isEmpty()) {
            throw new Exception("Campo 'variables' está vacío");
        }

        log.debug("JSON de variables para {}: {}", provincia,
                json.length() > 100 ? json.substring(0, 100) + "..." : json);

        // Parsear JSON a RendicionesRequestDTO
        return parsearJSON(json,convenio, provincia);
    }

    /**
     * Parsea el JSON de variables a RendicionesRequestDTO.
     *
     * Estructura esperada del JSON:
     * [
     *   {"key": "idOrganismo", "value": "4704"},
     *   {"key": "idUsuario", "value": "273617"},
     *   {"key": "password", "value": "77c66cd245fb4205715e72b8cf1ef092"},
     *   {"key": "hash", "value": "8f4b115baea5805eb02fc471cb5e629e"},
     *   {"key": "convenios", "value": "14704,24704,34704,44704,64704"}
     * ]
     *
     * NOTA: Las fechas (fechaDesde, fechaHasta) NO se obtienen de la configuración,
     *       se deben establecer al momento de hacer la consulta.
     *
     * @param json String JSON con array de variables
     * @param provincia Nombre de la provincia (para logging)
     * @return RendicionesRequestDTO con credenciales base
     * @throws Exception si el JSON es inválido o faltan campos requeridos
     */
    private RendicionesRequestDTO parsearJSON(String json, String convenio,  String provincia) throws Exception {

        // Parsear JSON a lista de VariableConfigDTO
        List<VariableConfigDTO> vars = objectMapper.readValue(
                json,
                new TypeReference<List<VariableConfigDTO>>() {}
        );

        // Convertir lista a mapa para fácil acceso
        Map<String, String> map = vars.stream()
                .collect(Collectors.toMap(
                        VariableConfigDTO::getKey,
                        VariableConfigDTO::getValue,
                        (v1, v2) -> v2 // En caso de keys duplicados, tomar el último valor
                ));

        // Validar que existan los campos mínimos requeridos
        validarCamposRequeridos(map, provincia);

        // Construir RendicionesRequestDTO con los campos del JSON
        RendicionesRequestDTO request = new RendicionesRequestDTO();
        request.setIdOrganismo(map.get("idOrganismo"));
        request.setIdUsuario(map.get("idUsuario"));
        request.setPassword(map.get("password"));
        request.setHash(map.get("hash"));

        // Convenios es opcional en la configuración, se puede pasar null
        // y establecerse luego al momento de la consulta
        request.setConvenios(map.getOrDefault("convenio", convenio));

        // Las fechas NO se cargan de la configuración
        // Se establecerán dinámicamente al hacer cada consulta
        request.setFechaDesde(null);
        request.setFechaHasta(null);

        log.debug("Credenciales parseadas para {}: organismo={}, usuario={}, convenios={}",
                provincia, request.getIdOrganismo(), request.getIdUsuario(), request.getConvenios());

        return request;
    }

    /**
     * Valida que el mapa contenga todos los campos requeridos.
     *
     * Campos obligatorios:
     * - idOrganismo
     * - idUsuario
     * - password
     * - hash
     *
     * @param map Mapa con las variables parseadas del JSON
     * @param provincia Nombre de la provincia (para mensaje de error)
     * @throws Exception si falta algún campo requerido
     */
    private void validarCamposRequeridos(Map<String, String> map, String provincia) throws Exception {
        List<String> camposFaltantes = new ArrayList<>();

        if (!map.containsKey("idOrganismo") || map.get("idOrganismo").trim().isEmpty()) {
            camposFaltantes.add("idOrganismo");
        }
        if (!map.containsKey("idUsuario") || map.get("idUsuario").trim().isEmpty()) {
            camposFaltantes.add("idUsuario");
        }
        if (!map.containsKey("password") || map.get("password").trim().isEmpty()) {
            camposFaltantes.add("password");
        }
        if (!map.containsKey("hash") || map.get("hash").trim().isEmpty()) {
            camposFaltantes.add("hash");
        }

        if (!camposFaltantes.isEmpty()) {
            throw new Exception(
                    String.format("Faltan campos requeridos en configuración de %s: %s",
                            provincia, String.join(", ", camposFaltantes))
            );
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS - VALIDACIÓN
    // ========================================================================

    /**
     * Valida que las credenciales estén completas y sean válidas.
     *
     * Validaciones:
     * - DTO no nulo
     * - Todos los campos de credenciales no nulos y no vacíos
     *
     * NOTA: No valida fechas ni convenios porque son opcionales en credenciales base.
     *
     * @param creds RendicionesRequestDTO a validar
     * @return true si las credenciales son válidas
     */
    private boolean esValido(RendicionesRequestDTO creds) {
        if (creds == null) {
            return false;
        }

        // Validar campos obligatorios no nulos y no vacíos
        return creds.getIdOrganismo() != null && !creds.getIdOrganismo().trim().isEmpty() &&
                creds.getIdUsuario() != null && !creds.getIdUsuario().trim().isEmpty() &&
                creds.getPassword() != null && !creds.getPassword().trim().isEmpty() &&
                creds.getHash() != null && !creds.getHash().trim().isEmpty();
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - ACCESO A CREDENCIALES
    // ========================================================================

    /**
     * Obtiene las credenciales base de una provincia.
     *
     * IMPORTANTE: Las credenciales retornadas NO incluyen fechas
     *            Estos campos deben ser establecidos antes de usar el DTO para consultar.
     *
     * @param provincia Nombre de la provincia
     * @return RendicionesRequestDTO con credenciales base, o null si no existe
     */
    public RendicionesRequestDTO getCredenciales(String provincia) {
        RendicionesRequestDTO creds = credencialesCache.get(provincia);

        if (creds == null) {
            log.warn("No se encontraron credenciales para provincia: {}", provincia);
        }

        return creds;
    }

    /**
     * Obtiene un clon de las credenciales para uso seguro.
     *
     * Retorna una copia de las credenciales para evitar modificar el caché.
     * Es el método recomendado cuando se van a establecer fechas/convenios dinámicamente.
     *
     * @param provincia Nombre de la provincia
     * @return Clon de RendicionesRequestDTO, o null si no existe
     */
    public RendicionesRequestDTO getCredencialesClon(String provincia) {
        RendicionesRequestDTO original = credencialesCache.get(provincia);

        if (original == null) {
            log.warn("No se encontraron credenciales para provincia: {}", provincia);
            return null;
        }

        // Crear copia para no modificar el caché
        RendicionesRequestDTO clon = new RendicionesRequestDTO();
        clon.setIdOrganismo(original.getIdOrganismo());
        clon.setIdUsuario(original.getIdUsuario());
        clon.setPassword(original.getPassword());
        clon.setHash(original.getHash());
        clon.setConvenios(original.getConvenios());
        // Fechas en null, se establecerán dinámicamente
        clon.setFechaDesde(null);
        clon.setFechaHasta(null);

        return clon;
    }

    /**
     * Obtiene todas las provincias que tienen credenciales configuradas.
     *
     * @return Set con nombres de provincias disponibles
     */
    public Set<String> getProvinciasDisponibles() {
        return new HashSet<>(credencialesCache.keySet());
    }

    /**
     * Obtiene todas las credenciales cargadas.
     *
     * ADVERTENCIA: El mapa retornado es de solo lectura.
     *              No intente modificar las credenciales directamente.
     *              Use getCredencialesClon() si necesita modificarlas.
     *
     * @return Mapa inmutable con todas las credenciales por provincia
     */
    public Map<String, RendicionesRequestDTO> getTodasCredenciales() {
        return Collections.unmodifiableMap(credencialesCache);
    }

    /**
     * Verifica si una provincia tiene credenciales configuradas.
     *
     * @param provincia Nombre de la provincia
     * @return true si la provincia tiene credenciales válidas
     */
    public boolean tieneCredenciales(String provincia) {
        return credencialesCache.containsKey(provincia) &&
                credencialesCache.get(provincia) != null;
    }

    /**
     * Obtiene el total de provincias con credenciales cargadas.
     *
     * @return Cantidad de provincias configuradas
     */
    public int getTotalProvinciasConfiguradas() {
        return credencialesCache.size();
    }

    // ========================================================================
    // MÉTODOS PÚBLICOS - UTILIDADES
    // ========================================================================

    /**
     * Recarga las credenciales de una provincia específica.
     *
     * Útil para refrescar credenciales sin reiniciar la aplicación.
     *
     * @param provincia Nombre de la provincia a recargar
     * @return true si se recargó exitosamente
     */
    public boolean recargarCredenciales(String provincia) {
        log.info("Recargando credenciales para provincia: {}", provincia);

        String datasource = provinciaMapping.getMapping().get(provincia);
        if (datasource == null) {
            log.error("Provincia {} no está configurada en ProvinciaMapping", provincia);
            return false;
        }

        try {
            RendicionesRequestDTO creds = cargarCredenciales(provincia, datasource);

            if (creds != null && esValido(creds)) {
                credencialesCache.put(provincia, creds);
                log.info("✓ Credenciales recargadas exitosamente para {}", provincia);
                return true;
            } else {
                log.error("✗ Credenciales inválidas para {}", provincia);
                return false;
            }

        } catch (Exception e) {
            log.error("✗ Error al recargar credenciales para {}: {}", provincia, e.getMessage());
            return false;
        }
    }

    /**
     * Recarga todas las credenciales.
     *
     * @return Cantidad de credenciales recargadas exitosamente
     */
    public int recargarTodasCredenciales() {
        log.info("Recargando todas las credenciales...");

        credencialesCache.clear();
        inicializar();

        return credencialesCache.size();
    }
}