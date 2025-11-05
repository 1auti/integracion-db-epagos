package org.transito_seguro.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Configuración de múltiples DataSources para el sistema de integración e-Pagos.
       BASES DE DATOS POSTGRESQL (REMOTAS) - PROVINCIALES
 *    - Provincias: PBA, MDA, Santa Rosa, Chaco, Entre Ríos, Formosa
 *    - Propósito: Datos maestros de infracciones por provincia
 *    - Acceso: Bajo demanda o sincronización programada
 */
@Configuration
@Slf4j
public class DataSourceConfig {


    // ========================================================================
    // BASES DE DATOS POSTGRESQL - PROVINCIALES
    // ========================================================================

//    @Bean(name = "pbaDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.pba")
//    public DataSource pbaDataSource() {
//        log.info("Configurando PostgreSQL DataSource: PBA (Provincia de Buenos Aires)");
//        return new HikariDataSource();
//    }
//
//    @Bean(name = "mdaDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.mda")
//    public DataSource mdaDataSource() {
//        log.info("Configurando PostgreSQL DataSource: MDA (Mar del Plata)");
//        return new HikariDataSource();
//    }
//
//    @Bean(name = "santaRosaDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.santa-rosa")
//    public DataSource santaRosaDataSource() {
//        log.info("Configurando PostgreSQL DataSource: Santa Rosa (La Pampa)");
//        return new HikariDataSource();
//    }
//
//    @Bean(name = "chacoDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.chaco")
//    public DataSource chacoDataSource() {
//        log.info("Configurando PostgreSQL DataSource: Chaco");
//        return new HikariDataSource();
//    }
//
//    @Bean(name = "entreRiosDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.entre-rios")
//    public DataSource entreRiosDataSource() {
//        log.info("Configurando PostgreSQL DataSource: Entre Ríos");
//        return new HikariDataSource();
//    }
//
//    @Bean(name = "formosaDataSource")
//    @ConfigurationProperties(prefix = "postgresql.datasources.formosa")
//    public DataSource formosaDataSource() {
//        log.info("Configurando PostgreSQL DataSource: Formosa");
//        return new HikariDataSource();
//    }

    @Bean(name = "testDataSource")
    @ConfigurationProperties(prefix = "postgresql.datasources.test")
    public DataSource testsDataSource(){
         log.info("Configurando Base de datos TEST");
         return new HikariDataSource();
     }







}