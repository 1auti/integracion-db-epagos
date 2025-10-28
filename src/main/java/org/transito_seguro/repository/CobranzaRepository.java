package org.transito_seguro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.transito_seguro.entity.Cobranza;

@Repository
public interface CobranzaRepository extends JpaRepository<Integer, Cobranza> {
}
