#!/bin/bash

# Script para configurar variables de entorno de bases de datos
echo "🔧 Configurando variables de entorno para bases de datos..."

# Variables para entorno de TESTING
export TEST_DB_URL="jdbc:postgresql://192.168.50.226:5432/saijz"
export TEST_DB_USERNAME="postgres"
export TEST_DB_PASSWORD="MultasTest@2024"
export EPAGOS_URL="http://192.168.50.202/epagos/prod/"
export SPRING_PROFILES_ACTIVE=test

echo "✅ Variables de entorno configuradas correctamente"

echo ""
echo "Variables de base de datos configuradas:"
echo "----------------------------------------"
echo "TEST_DB_URL=$TEST_DB_URL"
echo "TEST_DB_USERNAME=$TEST_DB_USERNAME"
echo "EPAGOS_URL=$EPAGOS_URL"