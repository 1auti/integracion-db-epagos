package org.transito_seguro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.transito_seguro.entity.RendicionPercepcion;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface RendicionPercepcionRepository extends JpaRepository<RendicionPercepcion, Integer> {

    /**
     * Busca una percepción por su número de transacción.
     */
    @Query("SELECT r FROM RendicionPercepcion r WHERE r.numero_transaccion = :numeroTransaccion")
    Optional<RendicionPercepcion> findByNumeroTransaccion(@Param("numeroTransaccion") String numeroTransaccion);

    /**
     * Busca percepciones por número de rendición.
     */
    @Query("SELECT r FROM RendicionPercepcion r WHERE r.numero_rendicion = :numeroRendicion")
    List<RendicionPercepcion> findByNumeroRendicion(@Param("numeroRendicion") Long numeroRendicion);

    /**
     * Busca percepciones por estado de conciliación.
     */
    @Query("SELECT r FROM RendicionPercepcion r WHERE r.estado_conciliacion = :estadoConciliacion")
    List<RendicionPercepcion> findByEstadoConciliacion(@Param("estadoConciliacion") String estadoConciliacion);

    /**
     * Busca percepciones huérfanas (sin cobranza vinculada).
     */
    @Query("SELECT rp FROM RendicionPercepcion rp WHERE rp.estado_conciliacion = 'HUERFANA'")
    List<RendicionPercepcion> findHuerfanas();

    /**
     * Busca percepciones conciliadas por rango de fechas de procesamiento.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.estado_conciliacion = 'CONCILIADA' " +
            "AND rp.fecha_procesamiento BETWEEN :fechaDesde AND :fechaHasta")
    List<RendicionPercepcion> findConciliadasEnRango(
            @Param("fechaDesde") Date fechaDesde,
            @Param("fechaHasta") Date fechaHasta);

    /**
     * Cuenta percepciones por estado de conciliación.
     */
    @Query("SELECT COUNT(rp) FROM RendicionPercepcion rp WHERE rp.estado_conciliacion = :estadoConciliacion")
    Long countByEstadoConciliacion(@Param("estadoConciliacion") String estadoConciliacion);

    /**
     * Busca percepciones pendientes de procesar.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.procesada = false " +
            "AND rp.estado_conciliacion = 'PERCIBIDA'")
    List<RendicionPercepcion> findPendientesProcesar();

    /**
     * Busca percepciones por ID de cobranza vinculada.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp WHERE rp.id_cobranza_vinculada = :idCobranzaVinculada")
    List<RendicionPercepcion> findByIdCobranzaVinculada(@Param("idCobranzaVinculada") Integer idCobranzaVinculada);

    /**
     * Busca percepciones por código de provincia y rango de fechas.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.codigo_provincia = :codigoProvincia " +
            "AND rp.fecha_alta BETWEEN :fechaDesde AND :fechaHasta")
    List<RendicionPercepcion> findByProvinciaYRangoFechas(
            @Param("codigoProvincia") String codigoProvincia,
            @Param("fechaDesde") Date fechaDesde,
            @Param("fechaHasta") Date fechaHasta);

    /**
     * Verifica si ya existe una percepción para una transacción y rendición específica.
     */
    @Query("SELECT CASE WHEN COUNT(rp) > 0 THEN true ELSE false END " +
            "FROM RendicionPercepcion rp " +
            "WHERE rp.numero_transaccion = :numeroTransaccion " +
            "AND rp.numero_rendicion = :numeroRendicion")
    boolean existePercepcion(
            @Param("numeroTransaccion") String numeroTransaccion,
            @Param("numeroRendicion") Long numeroRendicion);

    /**
     * Busca percepciones por rango de fechas de rendición.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.rend_desde BETWEEN :fechaDesde AND :fechaHasta " +
            "OR rp.rend_hasta BETWEEN :fechaDesde AND :fechaHasta")
    List<RendicionPercepcion> findByRangoFechasRendicion(
            @Param("fechaDesde") Date fechaDesde,
            @Param("fechaHasta") Date fechaHasta);

    /**
     * Busca percepciones por concesión y estado.
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.id_concesion = :idConcesion " +
            "AND rp.estado_conciliacion = :estadoConciliacion")
    List<RendicionPercepcion> findByConcesionAndEstado(
            @Param("idConcesion") Integer idConcesion,
            @Param("estadoConciliacion") String estadoConciliacion);

    /**
     * Busca percepciones duplicadas (misma transacción, diferentes IDs).
     */
    @Query("SELECT rp FROM RendicionPercepcion rp " +
            "WHERE rp.numero_transaccion IN (" +
            "    SELECT rp2.numero_transaccion FROM RendicionPercepcion rp2 " +
            "    WHERE rp2.id != rp.id " +
            "    GROUP BY rp2.numero_transaccion " +
            "    HAVING COUNT(rp2.numero_transaccion) > 1" +
            ")")
    List<RendicionPercepcion> findDuplicadas();
}