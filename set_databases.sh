#!/bin/bash

# ============================================================================
# CONFIGURACIÓN DE VARIABLES DE ENTORNO PARA PRODUCCIÓN
# ============================================================================

# BASES DE DATOS POSTGRESQL:
# ---------------------------

# Base de datos PBA
export DB_PBA_URL="jdbc:postgresql://192.168.50.122:5432/pba"
export DB_PBA_USERNAME="usuario_pba"
export DB_PBA_PASSWORD="UlbEXeTpiVKX#"

# Base de datos MDA
export DB_MDA_URL="jdbc:postgresql://192.168.50.122:5432/avellaneda"
export DB_MDA_USERNAME="usuario_avelleneda"
export DB_MDA_PASSWORD="TyOy5UQhFCug#"

# Base de datos Santa Rosa
export DB_SANTAROSA_URL="jdbc:postgresql://192.168.50.122:5432/lapampa"
export DB_SANTAROSA_USERNAME="usuario_lapampa"
export DB_SANTAROSA_PASSWORD="nv1VLeexAOZS#"

# Base de datos Chaco
export DB_CHACO_URL="jdbc:postgresql://192.168.50.122:5432/chaco"
export DB_CHACO_USERNAME="usuario_chaco"
export DB_CHACO_PASSWORD="iBqIyh5PPRH7#"

# Base de datos Entre Ríos
export DB_ENTRERIOS_URL="jdbc:postgresql://192.168.50.122:5432/entrerios"
export DB_ENTRERIOS_USERNAME="usuario_entrerios"
export DB_ENTRERIOS_PASSWORD="Z4V7KwE7fByw#"

# Base de datos Formosa
export DB_FORMOSA_URL="jdbc:postgresql://192.168.50.122:5432/formosa"
export DB_FORMOSA_USERNAME="usuario_formosa"
export DB_FORMOSA_PASSWORD="bA5Z2Mc48m#"

# E-PAGOS:
# --------
export EPAGOS_SOAP_URL="https://www.epagos.com/svc/wsespeciales.asmx"
export EPAGOS_USUARIO="usuario_epagos_produccion"
export EPAGOS_CLAVE="clave_epagos_produccion"

# ============================================================================
# VERIFICACIÓN DE VARIABLES SETEADAS
# ============================================================================

echo "✅ Variables de entorno configuradas correctamente"
echo ""
echo "Variables de base de datos configuradas:"
echo "----------------------------------------"
env | grep -E "^(DB_|EPAGOS_)" | sort