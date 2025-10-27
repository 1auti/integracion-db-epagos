package org.transito_seguro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Configuración de tareas programadas (Scheduled Tasks) para el sistema.
 *
 * Este configurador gestiona la ejecución automática de:
 * 1. Sincronización diaria con e-Pagos (rendiciones y contracargos)
 * 2. Limpieza de caché de transacciones
 * 3. Generación de reportes periódicos
 * 4. Monitoreo de transacciones huérfanas
 *
 * Características:
 * - Pool dedicado para tareas programadas
 * - Ejecución en paralelo de múltiples scheduled tasks
 * - Manejo de errores sin afectar otras tareas
 * - Configuración centralizada de horarios
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfig.class);

    // ========================================================================
    // CONSTANTES DE CONFIGURACIÓN
    // ========================================================================

    /**
     * Tamaño del pool de threads para tareas programadas.
     *
     * Dimensionamiento:
     * - Pool Size: 3 threads
     *   - Thread 1: Sincronización principal (diaria)
     *   - Thread 2: Verificación de contracargos (diaria)
     *   - Thread 3: Tareas de mantenimiento (semanal/mensual)
     */
    private static final int POOL_SIZE = 3;

    /**
     * Prefijo para los nombres de threads del scheduler.
     * Útil para debugging y monitoreo en logs.
     */
    private static final String THREAD_NAME_PREFIX = "Scheduled-";

    /**
     * Tiempo de espera al apagar (en segundos).
     * El sistema esperará este tiempo para que las tareas en curso terminen.
     */
    private static final int AWAIT_TERMINATION_SECONDS = 60;

    // ========================================================================
    // BEAN: TASK SCHEDULER
    // ========================================================================

    /**
     * TaskScheduler principal del sistema.
     *
     * Este scheduler maneja todas las tareas programadas mediante anotaciones
     * @Scheduled en los servicios.
     *
     * Configuración:
     * - ThreadPoolTaskScheduler: Implementación de Spring con pool de threads
     * - Pool Size: 3 threads dedicados
     * - Graceful Shutdown: Espera finalización de tareas al cerrar
     * - Thread Naming: Prefijo "Scheduled-" para identificación
     * @return TaskScheduler configurado
     */
    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        logger.info("Configurando Task Scheduler para tareas programadas");

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // Configuración del pool
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);

        // Shutdown graceful: espera que las tareas terminen
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);

        // Configurar manejo de errores
        scheduler.setErrorHandler(new org.springframework.util.ErrorHandler() {
            @Override
            public void handleError(Throwable throwable) {
                logger.error("Error no capturado en tarea programada: {}",
                        throwable.getMessage(), throwable);
            }
        });

        // Inicializar el scheduler
        scheduler.initialize();

        logger.info("Task Scheduler configurado: poolSize={}, threadPrefix={}",
                POOL_SIZE, THREAD_NAME_PREFIX);

        return scheduler;
    }

    // ========================================================================
    // CONFIGURACIÓN DEL SCHEDULER
    // ========================================================================

    /**
     * Configura el registrador de tareas programadas.
     *
     * Este método se invoca automáticamente por Spring para configurar
     * el TaskScheduler que se usará para todas las anotaciones @Scheduled.
     *
     * @param taskRegistrar Registrador de tareas de Spring
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        logger.info("Configurando ScheduledTaskRegistrar con taskScheduler personalizado");
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

}