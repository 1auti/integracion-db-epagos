package org.transito_seguro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuración de Thread Pools para ejecución asíncrona y paralela.
 *
 * Este configurador gestiona pools de threads para:
 * 1. Búsquedas multi-provinciales en paralelo
 * 2. Sincronización asíncrona con e-Pagos
 * 3. Procesamiento de lotes de transacciones
 * 4. Tareas de actualización distribuida
 *
 * Se utilizan dos pools especializados:
 * - searchExecutor: Para búsquedas rápidas y concurrentes
 * - syncExecutor: Para sincronizaciones de larga duración
 */
@Configuration
public class ExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorConfig.class);

    // ========================================================================
    // CONSTANTES DE CONFIGURACIÓN - SEARCH EXECUTOR
    // ========================================================================

    /**
     * Pool para búsquedas multi-provinciales.
     *
     * Dimensionamiento:
     * - Core Pool Size: 4 (una por provincia)
     * - Max Pool Size: 8 (permite picos de carga)
     * - Queue Capacity: 100 (cola de espera razonable)
     */
    private static final int SEARCH_CORE_POOL_SIZE = 4;
    private static final int SEARCH_MAX_POOL_SIZE = 8;
    private static final int SEARCH_QUEUE_CAPACITY = 100;
    private static final int SEARCH_KEEP_ALIVE_SECONDS = 60;
    private static final String SEARCH_THREAD_NAME_PREFIX = "Search-";

    // ========================================================================
    // CONSTANTES DE CONFIGURACIÓN - SYNC EXECUTOR
    // ========================================================================

    /**
     * Pool para sincronización con e-Pagos (operaciones de larga duración).
     *
     * Escenario típico:
     * - Sincronización diaria automática
     * - Sincronización manual por rango de fechas
     * - Procesamiento de contracargos
     */
    private static final int SYNC_CORE_POOL_SIZE = 2;
    private static final int SYNC_MAX_POOL_SIZE = 4;
    private static final int SYNC_QUEUE_CAPACITY = 50;
    private static final int SYNC_KEEP_ALIVE_SECONDS = 120;
    private static final String SYNC_THREAD_NAME_PREFIX = "Sync-";

    // ========================================================================
    // BEAN: SEARCH EXECUTOR (Búsquedas Multi-Provinciales)
    // ========================================================================

    /**
     * Executor para búsquedas concurrentes en múltiples provincias.
     * Configuración:
     * - ThreadPoolTaskExecutor: Implementación de Spring
     * - CallerRunsPolicy: Si el pool está lleno, ejecuta en thread del caller
     * - Await Termination: Espera 60 segundos al cerrar
     *
     * @return Executor configurado para búsquedas
     */
    @Bean(name = "searchExecutor")
    public Executor searchExecutor() {
        logger.info("Configurando Search Executor - Pool para búsquedas multi-provinciales");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configuración del pool
        executor.setCorePoolSize(SEARCH_CORE_POOL_SIZE);
        executor.setMaxPoolSize(SEARCH_MAX_POOL_SIZE);
        executor.setQueueCapacity(SEARCH_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(SEARCH_KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(SEARCH_THREAD_NAME_PREFIX);

        // Política de rechazo: CallerRunsPolicy
        // Si el pool está saturado, ejecuta la tarea en el thread que la envió
        // Esto previene pérdida de tareas y aplica backpressure natural
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Esperar finalización de tareas al cerrar
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Inicializar el executor
        executor.initialize();

        logger.info("Search Executor configurado: core={}, max={}, queue={}",
                SEARCH_CORE_POOL_SIZE, SEARCH_MAX_POOL_SIZE, SEARCH_QUEUE_CAPACITY);

        return executor;
    }

    // ========================================================================
    // BEAN: SYNC EXECUTOR (Sincronización con e-Pagos)
    // ========================================================================

    /**
     * Executor para sincronizaciones con e-Pagos (operaciones largas).
     *
     * Uso principal:
     * - SincronizacionService: Consulta de rendiciones y contracargos
     * - ActualizacionService: Actualización masiva de infracciones
     * - Procesos scheduled que pueden tardar varios minutos
     * Configuración:
     * - Pool más pequeño (sincronizaciones menos frecuentes)
     * - Keep alive mayor (120s) para operaciones largas
     * - CallerRunsPolicy: Backpressure si hay saturación
     *
     * @return Executor configurado para sincronizaciones
     */
    @Bean(name = "syncExecutor")
    public Executor syncExecutor() {
        logger.info("Configurando Sync Executor - Pool para sincronizaciones e-Pagos");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configuración del pool (más conservador que search)
        executor.setCorePoolSize(SYNC_CORE_POOL_SIZE);
        executor.setMaxPoolSize(SYNC_MAX_POOL_SIZE);
        executor.setQueueCapacity(SYNC_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(SYNC_KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(SYNC_THREAD_NAME_PREFIX);

        // Política de rechazo: CallerRunsPolicy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Esperar finalización de tareas al cerrar (importante para sincronizaciones)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // 2 minutos para operaciones largas

        // Inicializar el executor
        executor.initialize();

        logger.info("Sync Executor configurado: core={}, max={}, queue={}",
                SYNC_CORE_POOL_SIZE, SYNC_MAX_POOL_SIZE, SYNC_QUEUE_CAPACITY);

        return executor;
    }

}