package org.transito_seguro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.transito_seguro.entity.Cobranza;
import java.util.List;

@Repository
public interface CobranzaRepository extends JpaRepository<Cobranza,Integer> {

    /**
     * Busca cobranzas por múltiples números de transacción
     * @param numerosTransaccion Lista de números de transacción a buscar
     * @return Lista de cobranzas encontradas
     */
    @Query("SELECT c FROM Cobranza c WHERE c.numeroTransaccion IN :numerosTransaccion")
    List<Cobranza> buscarPorNumerosTransaccion(
            @Param("numerosTransaccion") List<String> numerosTransaccion
    );


}
