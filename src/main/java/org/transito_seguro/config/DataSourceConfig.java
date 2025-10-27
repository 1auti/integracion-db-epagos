package org.transito_seguro.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Configuración de múltiples DataSources para el sistema de integración e-Pagos.
 * 1. BASE DE DATOS H2 (EN MEMORIA) - PRIMARIA
 *    - Propósito: Caché de infracciones para búsquedas ultra-rápidas
 *    - Ventajas:
 *      • Búsquedas en < 10ms vs 100-500ms en PostgreSQL remoto
 *      • Reduce carga en bases provinciales
 *      • Sincronización controlada
 *    - Contiene: Tabla 'infracciones' con datos sincronizados
 *
 * 2. BASES DE DATOS POSTGRESQL (REMOTAS) - PROVINCIALES
 *    - Provincias: PBA, MDA, Santa Rosa, Chaco, Entre Ríos, Formosa
 *    - Propósito: Datos maestros de infracciones por provincia
 *    - Acceso: Bajo demanda o sincronización programada
 *
 * FLUJO DE BÚSQUEDA:
 * ==================
 * 1. Buscar primero en H2 (caché)
 * 2. Si no existe, buscar en PostgreSQL provincial
 * 3. Si se encuentra, guardar en H2 para futuras búsquedas
 * 4. Sincronización automática cada X horas
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "org.transito_seguro.repository",
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)
@Slf4j
public class DataSourceConfig {

    // ========================================================================
    // BASE DE DATOS H2 - PRIMARIA (CACHÉ EN MEMORIA)
    // ========================================================================

    /**
     * DataSource primario H2 para caché de infracciones.
     *
     * H2 es una base de datos en memoria extremadamente rápida:
     * - Búsquedas: < 10ms
     * - Sin latencia de red
     * - Ideal para caché temporal
     * @return DataSource H2 configurado
     */
    @Primary
    @Bean(name = "primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource primaryDataSource() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("Configurando H2 DataSource PRIMARIO (Caché en memoria)");
        log.info("═══════════════════════════════════════════════════════");
        return new HikariDataSource();
    }

    /**
     * EntityManagerFactory para el DataSource primario (H2).
     *
     * Gestiona las entidades JPA del caché:
     * - Infracciones
     * - TransaccionMapping (mapeo código e-Pagos ↔ número operación)
     * - HistoricoContracargo
     * @param primaryDataSource DataSource H2
     * @return EntityManagerFactory configurado
     */
    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            DataSource primaryDataSource) {

        log.info("Configurando EntityManagerFactory primario para H2");

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(primaryDataSource);
        em.setPackagesToScan("org.transito_seguro.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(h2HibernateProperties());

        return em;
    }

    /**
     * TransactionManager para el DataSource primario (H2).
     *
     * @param primaryEntityManagerFactory EntityManagerFactory H2
     * @return TransactionManager configurado
     */
    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            EntityManagerFactory primaryEntityManagerFactory) {
        return new JpaTransactionManager(primaryEntityManagerFactory);
    }

    /**
     * Propiedades Hibernate específicas para H2.
     *
     * - ddl-auto: create-drop → Crea tablas al iniciar, elimina al cerrar
     * - dialect: H2Dialect → Optimizado para H2
     * - show_sql: false → No mostrar SQL (mejora performance)
     *
     * @return Properties de Hibernate para H2
     */
    private Properties h2HibernateProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");

        // Optimizaciones para H2
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");

        return properties;
    }

    // ========================================================================
    // BASES DE DATOS POSTGRESQL - PROVINCIALES
    // ========================================================================

    @Bean(name = "pbaDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.pba")
    public DataSource pbaDataSource() {
        log.info("Configurando PostgreSQL DataSource: PBA (Provincia de Buenos Aires)");
        return new HikariDataSource();
    }

    @Bean(name = "mdaDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.mda")
    public DataSource mdaDataSource() {
        log.info("Configurando PostgreSQL DataSource: MDA (Mar del Plata)");
        return new HikariDataSource();
    }

    @Bean(name = "santaRosaDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.santa-rosa")
    public DataSource santaRosaDataSource() {
        log.info("Configurando PostgreSQL DataSource: Santa Rosa (La Pampa)");
        return new HikariDataSource();
    }

    @Bean(name = "chacoDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.chaco")
    public DataSource chacoDataSource() {
        log.info("Configurando PostgreSQL DataSource: Chaco");
        return new HikariDataSource();
    }

    @Bean(name = "entreRiosDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.entre-rios")
    public DataSource entreRiosDataSource() {
        log.info("Configurando PostgreSQL DataSource: Entre Ríos");
        return new HikariDataSource();
    }

    @Bean(name = "formosaDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.formosa")
    public DataSource formosaDataSource() {
        log.info("Configurando PostgreSQL DataSource: Formosa");
        return new HikariDataSource();
    }


}