package org.transito_seguro.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResultadoBusquedaMultiprovincial {

    private List<ResultadoBusquedaProvincia> resultadosPorProvincia;
    private int totalProvinciasConsultadas;
    private int provinciasExitosas;
    private int provinciasFallidas;
    private int totalRendicionesEncontradas;
    private int totalContracargosEncontrados;
    private int totalInfraccionesProcesadas;

    public void agregarResultadoProvincia(ResultadoBusquedaProvincia resultado) {
        this.resultadosPorProvincia.add(resultado);
        this.totalProvinciasConsultadas++;

        if (resultado.isExitoso()) {
            this.provinciasExitosas++;
            this.totalRendicionesEncontradas += resultado.getRendicionesEncontradas();
            this.totalContracargosEncontrados += resultado.getContracargosEncontrados();
            this.totalInfraccionesProcesadas += resultado.getInfraccionesActualizadas();
        } else {
            this.provinciasFallidas++;
        }
    }

    /**
     * Retorna un resumen legible del resultado consolidado.
     */
    public String obtenerResumen() {
        return String.format(
                "Resultado Búsqueda Multiprovincial: %d provincias consultadas " +
                        "(%d exitosas, %d fallidas), %d rendiciones, %d contracargos, " +
                        "%d infracciones procesadas",
                totalProvinciasConsultadas, provinciasExitosas, provinciasFallidas,
                totalRendicionesEncontradas, totalContracargosEncontrados,
                totalInfraccionesProcesadas
        );
    }

}
