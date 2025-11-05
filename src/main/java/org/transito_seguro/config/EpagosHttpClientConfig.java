package org.transito_seguro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuración del cliente HTTP para integración JSON con e-Pagos.
 *
 * Este configurador gestiona la comunicación HTTP POST + JSON con la API de e-Pagos para:
 * 1. Obtener token de autenticación
 * 2. Consultar rendiciones (pagos exitosos)
 * 3. Consultar contracargos (disputas de pago)
 *
 * IMPORTANTE: e-Pagos usa JSON sobre HTTP, NO SOAP.
 *
 * Características del cliente:
 * - Serialización/Deserialización JSON con Jackson
 * - Timeouts configurables para evitar bloqueos
 * - Pool de conexiones para reutilización
 * - Retry automático en caso de errores temporales (implementado en Service)
 */
@Configuration
@Slf4j
public class EpagosHttpClientConfig {

    // ========================================================================
    // PROPIEDADES DE CONFIGURACIÓN (desde application.yml)
    // ========================================================================

    /**
     * URL base de la API de e-Pagos.
     * Ejemplo: https://www.epagos.com/svc/wsespeciales.asmx
     */
    @Value("${epagos.url}")
    private String apiUrl;

    /**
     * Timeout de conexión en milisegundos.
     * Tiempo máximo para establecer conexión con el servidor.
     */
    @Value("${epagos.soap.connection-timeout:30000}")
    private int connectionTimeout;

    /**
     * Timeout de lectura en milisegundos.
     * Tiempo máximo para recibir respuesta del servidor.
     */
    @Value("${epagos.soap.read-timeout:60000}")
    private int readTimeout;

    /**
     * Máximo de conexiones totales en el pool.
     */
    @Value("${epagos.http.max-connections:50}")
    private int maxConnections;

    /**
     * Máximo de conexiones por ruta (por host).
     */
    @Value("${epagos.http.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    // ========================================================================
    // BEAN: HTTP CLIENT (APACHE HTTP CLIENT)
    // ========================================================================

    /**
     * Cliente HTTP configurado con timeouts y pool de conexiones.
     *
     * Apache HttpClient proporciona:
     * - Control fino de timeouts
     * - Manejo de conexiones keep-alive
     * - Pool de conexiones para reutilización
     * - Thread-safe
     *
     * Configuración de timeouts:
     * - Connection Timeout: Tiempo para establecer conexión
     * - Socket Timeout (Read): Tiempo para recibir datos
     * - Connection Request Timeout: Tiempo para obtener conexión del pool
     *
     * @return HttpClient configurado y listo para usar
     */
    @Bean
    public CloseableHttpClient httpClient() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("⚙️  CONFIGURANDO HTTP CLIENT PARA E-PAGOS");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("   URL Base:              {}", apiUrl);
        log.info("   Connection Timeout:    {} ms", connectionTimeout);
        log.info("   Read Timeout:          {} ms", readTimeout);
        log.info("   Max Connections:       {}", maxConnections);
        log.info("   Max Per Route:         {}", maxConnectionsPerRoute);
        log.info("═══════════════════════════════════════════════════════════");

        // Configuración de timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)           // Timeout para establecer conexión
                .setSocketTimeout(readTimeout)                   // Timeout para recibir datos
                .setConnectionRequestTimeout(connectionTimeout)  // Timeout para obtener del pool
                .build();

        // Construir cliente HTTP con pool de conexiones
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(maxConnections)                    // Pool total
                .setMaxConnPerRoute(maxConnectionsPerRoute)         // Pool por host
                .build();

        log.info("✓ HTTP Client configurado exitosamente");

        return client;
    }

    // ========================================================================
    // BEAN: OBJECT MAPPER (JACKSON JSON)
    // ========================================================================

    /**
     * ObjectMapper de Jackson para serialización/deserialización JSON.
     *
     * Configuración:
     * - Soporte para Java 8+ Time API (LocalDate, LocalDateTime)
     * - Ignora propiedades desconocidas (tolerancia a cambios en API)
     * - Maneja nulls correctamente
     * - Formatos de fecha configurables
     *
     * @return ObjectMapper configurado
     */
    @Bean
    public ObjectMapper objectMapper() {
        log.info("Configurando Jackson ObjectMapper para JSON");

        ObjectMapper mapper = new ObjectMapper();

        // Registrar módulo para Java 8+ Time API (LocalDate, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Configuraciones de deserialización
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);  // Ignorar propiedades desconocidas
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);  // "" → null

        // Configuraciones de serialización
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);  // ISO-8601 format

        log.info("✓ ObjectMapper configurado");

        return mapper;
    }
}