package org.transito_seguro;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Clase principal de la aplicación de integración con e-Pagos.
 *
 * Sistema de integración automatizada con e-Pagos para la gestión de:
 * - Rendiciones (pagos exitosos de infracciones)
 * - Contracargos (disputas de pagos)
 * - Sincronización multi-provincial
 * - Búsquedas optimizadas con caché H2
 *
 * Arquitectura:
 * - H2 (en memoria) como caché primario
 * - 6 bases de datos PostgreSQL provinciales
 * - Cliente SOAP para comunicación con e-Pagos
 * - Thread pools para procesamiento paralelo
 * - Tareas programadas para sincronización automática
 *
 * Provincias soportadas:
 * - PBA (Provincia de Buenos Aires)
 * - MDA (Mar del Plata)
 * - Santa Rosa (La Pampa)
 * - Chaco
 * - Entre Ríos
 * - Formosa
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 * @since 2024-10
 */
@SpringBootApplication
@EnableAsync        // Habilita procesamiento asíncrono (@Async)
@EnableScheduling   // Habilita tareas programadas (@Scheduled)
@Slf4j
public class App {

    /**
     * Método principal de la aplicación.
     *
     * Inicializa Spring Boot y muestra información de configuración en consola.
     *
     * Perfiles disponibles:
     * - dev: Desarrollo local con H2 y PostgreSQL locales
     * - prod: Producción con configuración segura (variables de entorno)
     *
     * Ejecución:
     * - Desarrollo: java -jar app.jar --spring.profiles.active=dev
     * - Producción: java -jar app.jar --spring.profiles.active=prod
     *
     * @param args Argumentos de línea de comandos
     */
    public static void main(String[] args) {
        // Iniciar aplicación Spring Boot
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);

        // Obtener información del entorno
        Environment env = context.getEnvironment();

        // Mostrar banner e información de inicio
        logApplicationStartup(env);
    }

    /**
     * Muestra información detallada del inicio de la aplicación en consola.
     *
     * Información mostrada:
     * - Nombre de la aplicación
     * - Perfil activo (dev/prod)
     * - URL de acceso local
     * - URL de acceso externo
     * - Puerto del servidor
     * - Context path
     * - Consola H2 (solo en desarrollo)
     *
     * @param env Entorno de Spring con las propiedades de configuración
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("No se pudo determinar la dirección del host", e);
        }

        String activeProfiles = env.getActiveProfiles().length == 0
                ? "default"
                : String.join(", ", env.getActiveProfiles());

        log.info("\n----------------------------------------------------------\n" +
                        "  ███████╗██████╗  █████╗  ██████╗  ██████╗ ███████╗\n" +
                        "  ██╔════╝██╔══██╗██╔══██╗██╔════╝ ██╔═══██╗██╔════╝\n" +
                        "  █████╗  ██████╔╝███████║██║  ███╗██║   ██║███████╗\n" +
                        "  ██╔══╝  ██╔═══╝ ██╔══██║██║   ██║██║   ██║╚════██║\n" +
                        "  ███████╗██║     ██║  ██║╚██████╔╝╚██████╔╝███████║\n" +
                        "  ╚══════╝╚═╝     ╚═╝  ╚═╝ ╚═════╝  ╚═════╝ ╚══════╝\n" +
                        "\n" +
                        "  Sistema de Integración con e-Pagos\n" +
                        "  Gestión de Infracciones de Tránsito\n" +
                        "----------------------------------------------------------\n" +
                        "  Aplicación:       {}\n" +
                        "  Perfil(es):       {}\n" +
                        "  Puerto:           {}\n" +
                        "  Context Path:     {}\n" +
                        "----------------------------------------------------------\n" +
                        "  Acceso Local:     {}://localhost:{}{}\n" +
                        "  Acceso Externo:   {}://{}:{}{}\n" +
                        "----------------------------------------------------------",
                env.getProperty("spring.application.name", "integracion-epagos"),
                activeProfiles,
                serverPort,
                contextPath,
                protocol,
                serverPort,
                contextPath,
                protocol,
                hostAddress,
                serverPort,
                contextPath
        );

        // Mostrar información específica según el perfil
        if ("dev".equals(activeProfiles)) {
            log.info("\n----------------------------------------------------------\n" +
                            "  🔧 MODO DESARROLLO\n" +
                            "----------------------------------------------------------\n" +
                            "  Consola H2:       {}://localhost:{}{}/h2-console\n" +
                            "  JDBC URL:         jdbc:h2:mem:infracciones_cache\n" +
                            "  Usuario:          sa\n" +
                            "  Password:         (vacío)\n" +
                            "----------------------------------------------------------\n" +
                            "  ⚠️  Las tareas programadas están DESHABILITADAS\n" +
                            "  ⚠️  SQL queries visibles en logs (debug)\n" +
                            "----------------------------------------------------------",
                    protocol,
                    serverPort,
                    contextPath
            );
        } else if ("prod".equals(activeProfiles)) {
            log.info("\n----------------------------------------------------------\n" +
                            "  🚀 MODO PRODUCCIÓN\n" +
                            "----------------------------------------------------------\n" +
                            "  Actuator:         {}://{}:{}{}/actuator\n" +
                            "  Health Check:     {}://{}:{}{}/actuator/health\n" +
                            "  Metrics:          {}://{}:{}{}/actuator/metrics\n" +
                            "----------------------------------------------------------\n" +
                            "  ✓ Tareas programadas HABILITADAS\n" +
                            "  ✓ Sincronización automática: Diaria 2:00 AM\n" +
                            "  ✓ Verificación contracargos: Diaria 12:00 PM\n" +
                            "----------------------------------------------------------",
                    protocol, hostAddress, serverPort, contextPath,
                    protocol, hostAddress, serverPort, contextPath,
                    protocol, hostAddress, serverPort, contextPath
            );
        }

        // Información de las bases de datos
        log.info("\n----------------------------------------------------------\n" +
                "  💾 BASES DE DATOS CONFIGURADAS\n" +
                "----------------------------------------------------------\n" +
                "  Caché Primario:   H2 (en memoria)\n" +
                "  Provincias:       6 PostgreSQL\n" +
                "    • PBA (Provincia de Buenos Aires)\n" +
                "    • MDA (Mar del Plata)\n" +
                "    • Santa Rosa (La Pampa)\n" +
                "    • Chaco\n" +
                "    • Entre Ríos\n" +
                "    • Formosa\n" +
                "----------------------------------------------------------"
        );

        // Información de e-Pagos
        String epagosUrl = env.getProperty("epagos.soap.url", "No configurado");
        String epagosUsuario = env.getProperty("epagos.soap.usuario", "No configurado");

        log.info("\n----------------------------------------------------------\n" +
                        "  🔌 INTEGRACIÓN E-PAGOS\n" +
                        "----------------------------------------------------------\n" +
                        "  Endpoint SOAP:    {}\n" +
                        "  Usuario:          {}\n" +
                        "  Estado:           Configurado\n" +
                        "----------------------------------------------------------",
                epagosUrl,
                epagosUsuario
        );

        log.info("\n----------------------------------------------------------\n" +
                "  ✅ APLICACIÓN INICIADA CORRECTAMENTE\n" +
                "----------------------------------------------------------\n"
        );
    }
}