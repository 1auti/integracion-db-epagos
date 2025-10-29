package org.transito_seguro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.transito_seguro.entity.RendicionCobro;

@Repository
public interface RendicionCobroRepository extends JpaRepository<RendicionCobro,Integer> {
}
