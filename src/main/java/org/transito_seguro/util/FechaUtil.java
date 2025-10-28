package org.transito_seguro.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

/**
 * Clase utilitaria para manejo y conversión de fechas en el sistema.
 *
 * Proporciona métodos para:
 * - Conversión entre java.util.Date, java.time.LocalDate y java.time.LocalDateTime
 * - Formateo de fechas según diferentes patrones
 * - Validación de rangos de fechas
 * - Cálculos de diferencias temporales
 * - Conversión de fechas al formato esperado por e-pagos
 *
 * Esta clase está optimizada para Java 8 y utiliza la API java.time
 * en lugar de las clases legacy de java.util.
 *
 * @author Sistema Tránsito Seguro
 * @version 1.0
 */
public final class FechaUtil {

    /**
     * Zona horaria de Argentina (UTC-3).
     */
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Formatter para formato ISO estándar: yyyy-MM-dd.
     */
    private static final DateTimeFormatter FORMATTER_ISO =
            DateTimeFormatter.ofPattern(Constants.FORMATO_FECHA_ISO);

    /**
     * Formatter para formato ISO con hora: yyyy-MM-dd'T'HH:mm:ss.
     */
    private static final DateTimeFormatter FORMATTER_ISO_HORA =
            DateTimeFormatter.ofPattern(Constants.FORMATO_FECHA_HORA_ISO);

    /**
     * Formatter para formato de e-pagos: dd/MM/yyyy.
     */
    private static final DateTimeFormatter FORMATTER_EPAGOS =
            DateTimeFormatter.ofPattern(Constants.FORMATO_FECHA_EPAGOS);

    /**
     * Formatter para formato de e-pagos con hora: dd/MM/yyyy HH:mm:ss.
     */
    private static final DateTimeFormatter FORMATTER_EPAGOS_HORA =
            DateTimeFormatter.ofPattern(Constants.FORMATO_FECHA_HORA_EPAGOS);

    /**
     * Formatter para timestamp de archivos: yyyyMMdd_HHmmss.
     */
    private static final DateTimeFormatter FORMATTER_TIMESTAMP_ARCHIVO =
            DateTimeFormatter.ofPattern(Constants.FORMATO_TIMESTAMP_ARCHIVO);

    /**
     * Constructor privado para prevenir instanciación.
     */
    private FechaUtil() {
        throw new AssertionError("No se puede instanciar la clase FechaUtil");
    }

    // ==================== CONVERSIÓN ENTRE TIPOS ====================

    /**
     * Convierte un LocalDate a java.util.Date.
     *
     * La fecha resultante se establece a las 00:00:00 en la zona horaria de Argentina.
     *
     * @param localDate la fecha LocalDate a convertir (no puede ser null)
     * @return Date correspondiente, o null si el parámetro es null
     * @throws NullPointerException si localDate es null
     */
    public static Date convertirADate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(localDate.atStartOfDay(ZONA_ARGENTINA).toInstant());
    }

    /**
     * Convierte un LocalDateTime a java.util.Date.
     *
     * @param localDateTime el LocalDateTime a convertir (no puede ser null)
     * @return Date correspondiente, o null si el parámetro es null
     */
    public static Date convertirADate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZONA_ARGENTINA).toInstant());
    }

    /**
     * Convierte un java.util.Date a LocalDate.
     *
     * La fecha se convierte considerando la zona horaria de Argentina.
     *
     * @param date el Date a convertir
     * @return LocalDate correspondiente, o null si el parámetro es null
     */
    public static LocalDate convertirALocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(ZONA_ARGENTINA)
                .toLocalDate();
    }

    /**
     * Convierte un java.util.Date a LocalDateTime.
     *
     * @param date el Date a convertir
     * @return LocalDateTime correspondiente, o null si el parámetro es null
     */
    public static LocalDateTime convertirALocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(ZONA_ARGENTINA)
                .toLocalDateTime();
    }

    /**
     * Convierte un String a LocalDate utilizando formato ISO (yyyy-MM-dd).
     *
     * @param fechaStr la fecha en formato String
     * @return LocalDate correspondiente, o null si el String es null o vacío
     * @throws DateTimeParseException si el formato del String es inválido
     */
    public static LocalDate parsearFechaISO(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(fechaStr.trim(), FORMATTER_ISO);
    }

    /**
     * Convierte un String a LocalDateTime utilizando formato ISO con hora.
     *
     * @param fechaHoraStr la fecha y hora en formato String
     * @return LocalDateTime correspondiente, o null si el String es null o vacío
     * @throws DateTimeParseException si el formato del String es inválido
     */
    public static LocalDateTime parsearFechaHoraISO(String fechaHoraStr) {
        if (fechaHoraStr == null || fechaHoraStr.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(fechaHoraStr.trim(), FORMATTER_ISO_HORA);
    }

    /**
     * Convierte un String a LocalDate utilizando formato de e-pagos (dd/MM/yyyy).
     *
     * @param fechaStr la fecha en formato String de e-pagos
     * @return LocalDate correspondiente, o null si el String es null o vacío
     * @throws DateTimeParseException si el formato del String es inválido
     */
    public static LocalDate parsearFechaEpagos(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(fechaStr.trim(), FORMATTER_EPAGOS);
    }

    /**
     * Convierte un String a LocalDateTime utilizando formato de e-pagos con hora.
     *
     * @param fechaHoraStr la fecha y hora en formato String de e-pagos
     * @return LocalDateTime correspondiente, o null si el String es null o vacío
     * @throws DateTimeParseException si el formato del String es inválido
     */
    public static LocalDateTime parsearFechaHoraEpagos(String fechaHoraStr) {
        if (fechaHoraStr == null || fechaHoraStr.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(fechaHoraStr.trim(), FORMATTER_EPAGOS_HORA);
    }

    // ==================== FORMATEO DE FECHAS ====================

    /**
     * Formatea un LocalDate al formato ISO estándar (yyyy-MM-dd).
     *
     * @param fecha la fecha a formatear
     * @return String con la fecha formateada, o null si el parámetro es null
     */
    public static String formatearFechaISO(LocalDate fecha) {
        if (fecha == null) {
            return null;
        }
        return fecha.format(FORMATTER_ISO);
    }

    /**
     * Formatea un LocalDateTime al formato ISO con hora (yyyy-MM-dd'T'HH:mm:ss).
     *
     * @param fechaHora la fecha y hora a formatear
     * @return String con la fecha y hora formateada, o null si el parámetro es null
     */
    public static String formatearFechaHoraISO(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return null;
        }
        return fechaHora.format(FORMATTER_ISO_HORA);
    }

    /**
     * Formatea un LocalDate al formato esperado por e-pagos (dd/MM/yyyy).
     *
     * Este método debe utilizarse al enviar fechas a la API de e-pagos.
     *
     * @param fecha la fecha a formatear
     * @return String con la fecha formateada para e-pagos, o null si el parámetro es null
     */
    public static String formatearFechaParaEpagos(LocalDate fecha) {
        if (fecha == null) {
            return null;
        }
        return fecha.format(FORMATTER_EPAGOS);
    }

    /**
     * Formatea un Date al formato esperado por e-pagos (dd/MM/yyyy).
     *
     * @param fecha la fecha a formatear
     * @return String con la fecha formateada para e-pagos, o null si el parámetro es null
     */
    public static String formatearFechaParaEpagos(Date fecha) {
        if (fecha == null) {
            return null;
        }
        LocalDate localDate = convertirALocalDate(fecha);
        return formatearFechaParaEpagos(localDate);
    }

    /**
     * Formatea un LocalDateTime al formato esperado por e-pagos con hora.
     *
     * @param fechaHora la fecha y hora a formatear
     * @return String con la fecha y hora formateada, o null si el parámetro es null
     */
    public static String formatearFechaHoraParaEpagos(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return null;
        }
        return fechaHora.format(FORMATTER_EPAGOS_HORA);
    }

    /**
     * Genera un timestamp en formato archivo (yyyyMMdd_HHmmss).
     *
     * Útil para nombrar archivos de log o reportes con timestamp.
     *
     * @return String con el timestamp actual
     */
    public static String generarTimestampArchivo() {
        return LocalDateTime.now(ZONA_ARGENTINA).format(FORMATTER_TIMESTAMP_ARCHIVO);
    }

    /**
     * Genera un timestamp en formato archivo para una fecha específica.
     *
     * @param fechaHora la fecha y hora para generar el timestamp
     * @return String con el timestamp, o null si el parámetro es null
     */
    public static String generarTimestampArchivo(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return null;
        }
        return fechaHora.format(FORMATTER_TIMESTAMP_ARCHIVO);
    }

    // ==================== UTILIDADES DE FECHA ====================

    /**
     * Obtiene la fecha actual en la zona horaria de Argentina.
     *
     * @return LocalDate con la fecha actual
     */
    public static LocalDate obtenerFechaActual() {
        return LocalDate.now(ZONA_ARGENTINA);
    }

    /**
     * Obtiene la fecha y hora actual en la zona horaria de Argentina.
     *
     * @return LocalDateTime con la fecha y hora actual
     */
    public static LocalDateTime obtenerFechaHoraActual() {
        return LocalDateTime.now(ZONA_ARGENTINA);
    }

    /**
     * Obtiene la fecha de inicio del día (00:00:00).
     *
     * @param fecha la fecha base
     * @return LocalDateTime con la hora en 00:00:00
     */
    public static LocalDateTime obtenerInicioDia(LocalDate fecha) {
        if (fecha == null) {
            return null;
        }
        return fecha.atStartOfDay();
    }

    /**
     * Obtiene la fecha de fin del día (23:59:59).
     *
     * @param fecha la fecha base
     * @return LocalDateTime con la hora en 23:59:59
     */
    public static LocalDateTime obtenerFinDia(LocalDate fecha) {
        if (fecha == null) {
            return null;
        }
        return fecha.atTime(23, 59, 59);
    }

    /**
     * Resta días a una fecha.
     *
     * @param fecha la fecha base
     * @param dias cantidad de días a restar
     * @return LocalDate con los días restados
     */
    public static LocalDate restarDias(LocalDate fecha, int dias) {
        if (fecha == null) {
            return null;
        }
        return fecha.minusDays(dias);
    }

    /**
     * Suma días a una fecha.
     *
     * @param fecha la fecha base
     * @param dias cantidad de días a sumar
     * @return LocalDate con los días sumados
     */
    public static LocalDate sumarDias(LocalDate fecha, int dias) {
        if (fecha == null) {
            return null;
        }
        return fecha.plusDays(dias);
    }

    /**
     * Resta semanas a una fecha.
     *
     * @param fecha la fecha base
     * @param semanas cantidad de semanas a restar
     * @return LocalDate con las semanas restadas
     */
    public static LocalDate restarSemanas(LocalDate fecha, int semanas) {
        if (fecha == null) {
            return null;
        }
        return fecha.minusWeeks(semanas);
    }

    /**
     * Obtiene el rango de fechas para búsqueda retrospectiva.
     *
     * Retorna un array con dos elementos:
     * - Índice 0: fecha desde (fecha actual - días)
     * - Índice 1: fecha hasta (fecha actual)
     *
     * @param diasAtras cantidad de días hacia atrás desde hoy
     * @return Array de LocalDate con [fechaDesde, fechaHasta]
     */
    public static LocalDate[] obtenerRangoRetrospectivo(int diasAtras) {
        LocalDate fechaHasta = obtenerFechaActual();
        LocalDate fechaDesde = fechaHasta.minusDays(diasAtras);
        return new LocalDate[]{fechaDesde, fechaHasta};
    }

    /**
     * Obtiene el rango de la última semana (7 días).
     *
     * @return Array de LocalDate con [fechaDesde, fechaHasta]
     */
    public static LocalDate[] obtenerRangoUltimaSemana() {
        return obtenerRangoRetrospectivo(Constants.DIAS_BUSQUEDA_RETROSPECTIVA_DEFAULT);
    }

    /**
     * Obtiene el rango del mes actual.
     *
     * @return Array de LocalDate con [primerDiaMes, ultimoDiaMes]
     */
    public static LocalDate[] obtenerRangoMesActual() {
        LocalDate hoy = obtenerFechaActual();
        LocalDate primerDia = hoy.withDayOfMonth(1);
        LocalDate ultimoDia = hoy.withDayOfMonth(hoy.lengthOfMonth());
        return new LocalDate[]{primerDia, ultimoDia};
    }

    /**
     * Obtiene el rango del mes anterior.
     *
     * @return Array de LocalDate con [primerDiaMesAnterior, ultimoDiaMesAnterior]
     */
    public static LocalDate[] obtenerRangoMesAnterior() {
        LocalDate hoy = obtenerFechaActual();
        LocalDate mesAnterior = hoy.minusMonths(1);
        LocalDate primerDia = mesAnterior.withDayOfMonth(1);
        LocalDate ultimoDia = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
        return new LocalDate[]{primerDia, ultimoDia};
    }

    // ==================== VALIDACIONES ====================

    /**
     * Valida que una fecha no sea nula.
     *
     * @param fecha la fecha a validar
     * @return true si la fecha no es nula, false en caso contrario
     */
    public static boolean esValida(LocalDate fecha) {
        return fecha != null;
    }

    /**
     * Valida que una fecha esté dentro de un rango.
     *
     * @param fecha la fecha a validar
     * @param desde fecha inicial del rango (inclusiva)
     * @param hasta fecha final del rango (inclusiva)
     * @return true si la fecha está en el rango, false en caso contrario
     */
    public static boolean estaEnRango(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null || desde == null || hasta == null) {
            return false;
        }
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /**
     * Valida que la fecha desde sea anterior o igual a la fecha hasta.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return true si el rango es válido, false en caso contrario
     */
    public static boolean esRangoValido(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return false;
        }
        return !desde.isAfter(hasta);
    }

    /**
     * Valida que una fecha no sea futura.
     *
     * @param fecha la fecha a validar
     * @return true si la fecha no es futura, false en caso contrario
     */
    public static boolean noEsFutura(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        return !fecha.isAfter(obtenerFechaActual());
    }

    /**
     * Valida que una fecha esté dentro de los últimos N días.
     *
     * @param fecha la fecha a validar
     * @param dias cantidad de días máximos hacia atrás
     * @return true si la fecha está dentro del rango, false en caso contrario
     */
    public static boolean estaEnUltimosDias(LocalDate fecha, int dias) {
        if (fecha == null || dias < 0) {
            return false;
        }
        LocalDate fechaLimite = obtenerFechaActual().minusDays(dias);
        return !fecha.isBefore(fechaLimite) && noEsFutura(fecha);
    }

    /**
     * Valida que el rango de fechas no exceda el máximo de días permitidos.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param maxDias cantidad máxima de días permitidos
     * @return true si el rango no excede el máximo, false en caso contrario
     */
    public static boolean rangoNoExcedeMaximo(LocalDate desde, LocalDate hasta, int maxDias) {
        if (!esRangoValido(desde, hasta)) {
            return false;
        }
        long diasDiferencia = ChronoUnit.DAYS.between(desde, hasta);
        return diasDiferencia <= maxDias;
    }

    // ==================== COMPARACIONES ====================

    /**
     * Verifica si una fecha es anterior a otra.
     *
     * @param fecha1 primera fecha
     * @param fecha2 segunda fecha
     * @return true si fecha1 es anterior a fecha2
     */
    public static boolean esAnterior(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null || fecha2 == null) {
            return false;
        }
        return fecha1.isBefore(fecha2);
    }

    /**
     * Verifica si una fecha es posterior a otra.
     *
     * @param fecha1 primera fecha
     * @param fecha2 segunda fecha
     * @return true si fecha1 es posterior a fecha2
     */
    public static boolean esPosterior(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null || fecha2 == null) {
            return false;
        }
        return fecha1.isAfter(fecha2);
    }

    /**
     * Verifica si dos fechas son iguales.
     *
     * @param fecha1 primera fecha
     * @param fecha2 segunda fecha
     * @return true si las fechas son iguales
     */
    public static boolean sonIguales(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null || fecha2 == null) {
            return false;
        }
        return fecha1.isEqual(fecha2);
    }

    /**
     * Obtiene la fecha más reciente entre dos fechas.
     *
     * @param fecha1 primera fecha
     * @param fecha2 segunda fecha
     * @return la fecha más reciente, o null si ambas son null
     */
    public static LocalDate obtenerMasReciente(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null) {
            return fecha2;
        }
        if (fecha2 == null) {
            return fecha1;
        }
        return fecha1.isAfter(fecha2) ? fecha1 : fecha2;
    }

    /**
     * Obtiene la fecha más antigua entre dos fechas.
     *
     * @param fecha1 primera fecha
     * @param fecha2 segunda fecha
     * @return la fecha más antigua, o null si ambas son null
     */
    public static LocalDate obtenerMasAntigua(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null) {
            return fecha2;
        }
        if (fecha2 == null) {
            return fecha1;
        }
        return fecha1.isBefore(fecha2) ? fecha1 : fecha2;
    }

    // ==================== CÁLCULOS ====================

    /**
     * Calcula la diferencia en días entre dos fechas.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return cantidad de días de diferencia (puede ser negativo si desde > hasta)
     */
    public static long calcularDiferenciaEnDias(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas no pueden ser null");
        }
        return ChronoUnit.DAYS.between(desde, hasta);
    }

    /**
     * Calcula la diferencia en semanas entre dos fechas.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return cantidad de semanas de diferencia
     */
    public static long calcularDiferenciaEnSemanas(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas no pueden ser null");
        }
        return ChronoUnit.WEEKS.between(desde, hasta);
    }

    /**
     * Calcula la diferencia en meses entre dos fechas.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return cantidad de meses de diferencia
     */
    public static long calcularDiferenciaEnMeses(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas no pueden ser null");
        }
        return ChronoUnit.MONTHS.between(desde, hasta);
    }

    /**
     * Calcula la cantidad de días desde una fecha hasta hoy.
     *
     * @param fecha la fecha inicial
     * @return cantidad de días desde la fecha hasta hoy
     */
    public static long calcularDiasDesdeHoy(LocalDate fecha) {
        return calcularDiferenciaEnDias(fecha, obtenerFechaActual());
    }

    /**
     * Calcula la cantidad de días hasta una fecha desde hoy.
     *
     * @param fecha la fecha final
     * @return cantidad de días desde hoy hasta la fecha
     */
    public static long calcularDiasHastaFecha(LocalDate fecha) {
        return calcularDiferenciaEnDias(obtenerFechaActual(), fecha);
    }

    // ==================== UTILIDADES LEGACY (para compatibilidad) ====================

    /**
     * Convierte un String en formato legacy (dd/MM/yyyy) a Date.
     *
     * Este método mantiene compatibilidad con sistemas antiguos.
     *
     * @param fechaStr la fecha en formato String
     * @return Date correspondiente, o null si hay error de parsing
     * @deprecated Usar {@link #parsearFechaEpagos(String)} y {@link #convertirADate(LocalDate)}
     */
    @Deprecated
    public static Date parsearFechaLegacy(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(Constants.FORMATO_FECHA_EPAGOS);
            sdf.setLenient(false);
            return sdf.parse(fechaStr.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Formatea un Date al formato legacy (dd/MM/yyyy).
     *
     * @param fecha la fecha a formatear
     * @return String con la fecha formateada, o null si el parámetro es null
     * @deprecated Usar {@link #convertirALocalDate(Date)} y {@link #formatearFechaParaEpagos(LocalDate)}
     */
    @Deprecated
    public static String formatearFechaLegacy(Date fecha) {
        if (fecha == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.FORMATO_FECHA_EPAGOS);
        return sdf.format(fecha);
    }

    // ==================== MÉTODOS DE UTILIDAD PARA LOGS ====================

    /**
     * Genera una descripción legible del rango de fechas.
     *
     * Formato: "desde [fecha] hasta [fecha]"
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return String con la descripción del rango
     */
    public static String describirRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return "rango inválido";
        }
        return String.format("desde %s hasta %s",
                formatearFechaISO(desde),
                formatearFechaISO(hasta));
    }

    /**
     * Genera una descripción legible de la antigüedad de una fecha.
     *
     * Ejemplos: "hace 3 días", "hace 2 semanas", "hace 1 mes"
     *
     * @param fecha la fecha a describir
     * @return String con la descripción de antigüedad
     */
    public static String describirAntiguedad(LocalDate fecha) {
        if (fecha == null) {
            return "fecha inválida";
        }

        long dias = calcularDiasDesdeHoy(fecha);

        if (dias == 0) {
            return "hoy";
        } else if (dias == 1) {
            return "ayer";
        } else if (dias < 7) {
            return String.format("hace %d días", dias);
        } else if (dias < 30) {
            long semanas = dias / 7;
            return String.format("hace %d %s", semanas, semanas == 1 ? "semana" : "semanas");
        } else if (dias < 365) {
            long meses = dias / 30;
            return String.format("hace %d %s", meses, meses == 1 ? "mes" : "meses");
        } else {
            long anios = dias / 365;
            return String.format("hace %d %s", anios, anios == 1 ? "año" : "años");
        }
    }

    /**
     * Verifica si una fecha corresponde a hoy.
     *
     * @param fecha la fecha a verificar
     * @return true si la fecha es hoy, false en caso contrario
     */
    public static boolean esHoy(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        return fecha.isEqual(obtenerFechaActual());
    }

    /**
     * Verifica si una fecha corresponde a ayer.
     *
     * @param fecha la fecha a verificar
     * @return true si la fecha es ayer, false en caso contrario
     */
    public static boolean esAyer(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        return fecha.isEqual(obtenerFechaActual().minusDays(1));
    }

    /**
     * Verifica si una fecha está en el mes actual.
     *
     * @param fecha la fecha a verificar
     * @return true si la fecha está en el mes actual, false en caso contrario
     */
    public static boolean esMesActual(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        LocalDate hoy = obtenerFechaActual();
        return fecha.getYear() == hoy.getYear() && fecha.getMonth() == hoy.getMonth();
    }

    /**
     * Verifica si una fecha está en el año actual.
     *
     * @param fecha la fecha a verificar
     * @return true si la fecha está en el año actual, false en caso contrario
     */
    public static boolean esAnioActual(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        return fecha.getYear() == obtenerFechaActual().getYear();
    }
}