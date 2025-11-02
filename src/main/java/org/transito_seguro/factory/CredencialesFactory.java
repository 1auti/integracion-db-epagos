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
import org.transito_seguro.dto.credenciales.CredencialesEpagosDTO;  // ← TU DTO EXISTENTE

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory para cargar credenciales de e-Pagos desde cada base de datos.
 * Usa CredencialesEpagosDTO (el mismo que usa EpagosClientService).
 */
@Component
@Slf4j
public class CredencialesFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProvinciaMapping provinciaMapping;

    @Autowired
    private ObjectMapper objectMapper;

    // Caché: provincia → CredencialesEpagosDTO
    private final Map<String, CredencialesEpagosDTO> credencialesCache = new ConcurrentHashMap<>();

    /**
     * Carga todas las credenciales al iniciar.
     */
    @PostConstruct
    public void inicializar() {
        log.info("🔧 Cargando credenciales e-Pagos...");

        Map<String, String> mapping = provinciaMapping.getMapping();
        if (mapping == null || mapping.isEmpty()) {
            log.error("❌ No hay provincias configuradas");
            return;
        }

        int exitosas = 0;

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String provincia = entry.getKey();
            String datasource = entry.getValue();

            try {
                CredencialesEpagosDTO creds = cargarCredenciales(provincia, datasource);

                if (creds != null && esValido(creds)) {
                    credencialesCache.put(provincia, creds);
                    exitosas++;
                    log.info("✓ {} - Org: {}", provincia, creds.getIdOrganismo());
                }

            } catch (Exception e) {
                log.error("✗ {} - Error: {}", provincia, e.getMessage());
            }
        }

        log.info("📦 Credenciales: {}/{}", exitosas, mapping.size());
    }

    /**
     * Carga credenciales de una provincia.
     */
    private CredencialesEpagosDTO cargarCredenciales(String provincia, String datasource) throws Exception {

        DataSource ds = applicationContext.getBean(datasource + "DataSource", DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        String sql = "SELECT id_concesion, variables FROM concesion_configuracion_convenio " +
                "WHERE is_activo = true AND variables IS NOT NULL LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        if (rows.isEmpty()) {
            throw new Exception("No hay configuración activa");
        }

        String json = (String) rows.get(0).get("variables");

        return parsearJSON(json, provincia);
    }

    /**
     * Parsea JSON a CredencialesEpagosDTO.
     */
    private CredencialesEpagosDTO parsearJSON(String json, String provincia) throws Exception {

        List<VariableConfigDTO> vars = objectMapper.readValue(
                json,
                new TypeReference<List<VariableConfigDTO>>() {}
        );

        Map<String, String> map = vars.stream()
                .collect(Collectors.toMap(
                        VariableConfigDTO::getKey,
                        VariableConfigDTO::getValue
                ));

        // Construir DTO con los campos que ya tiene
        return CredencialesEpagosDTO.builder()
                .idOrganismo(map.get("idOrganismo"))
                .usuario(map.get("idUsuario"))           // JSON: "idUsuario" → DTO: "usuario"
                .clave(map.get("password"))              // JSON: "password" → DTO: "clave"
                .hash(map.get("hash"))
                .urlServicio(map.get("URL_SERVICIO"))    // Si el DTO tiene este campo
                .implementador(map.get("implementador")) // Si el DTO tiene este campo
                .nombreProvincia(provincia)              // Agregar para identificación
                .build();
    }

    /**
     * Valida que las credenciales estén completas.
     */
    private boolean esValido(CredencialesEpagosDTO creds) {
        return creds != null &&
                creds.getIdOrganismo() != null && !creds.getIdOrganismo().trim().isEmpty() &&
                creds.getUsuario() != null && !creds.getUsuario().trim().isEmpty() &&
                creds.getClave() != null && !creds.getClave().trim().isEmpty() &&
                creds.getHash() != null && !creds.getHash().trim().isEmpty();
    }

    /**
     * Obtiene credenciales de una provincia.
     */
    public CredencialesEpagosDTO getCredenciales(String provincia) {
        return credencialesCache.get(provincia);
    }

    /**
     * Obtiene todas las provincias disponibles.
     */
    public Set<String> getProvinciasDisponibles() {
        return credencialesCache.keySet();
    }

    /**
     * Obtiene todas las credenciales.
     */
    public Map<String, CredencialesEpagosDTO> getTodasCredenciales() {
        return Collections.unmodifiableMap(credencialesCache);
    }
}