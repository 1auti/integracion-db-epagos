package org.transito_seguro.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import  org.transito_seguro.entity.ConcesionConfiguracionConvenio;

import org.springframework.stereotype.Repository;

@Repository
public interface ConcesionConfiguracionConvenioRepository extends JpaRepository<ConcesionConfiguracionConvenio,Integer> {
}
