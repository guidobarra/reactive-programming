package org.java.guba.reactiveprogramming.sec07;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lec05: Estrategias de Backpressure (Contrapresión)
 * 
 * Esta clase está preparada para demostrar las diferentes estrategias que Reactor
 * proporciona para manejar la contrapresión cuando el productor emite datos más rápido
 * de lo que el consumidor puede procesar.
 * 
 * QUÉ ES BACKPRESSURE:
 * 
 * Backpressure (contrapresión) ocurre cuando:
 * - El productor emite datos más rápido de lo que el consumidor puede procesar
 * - El buffer entre productor y consumidor se llena
 * - Necesitas una estrategia para manejar este desbalance
 * 
 * ESTRATEGIAS DISPONIBLES EN REACTOR:
 * 
 * 1. BUFFER (por defecto):
 *    - Acumula elementos en un buffer hasta que el consumidor esté listo
 *    - Puede causar OutOfMemoryError si el buffer crece demasiado
 *    - Útil cuando el desbalance es temporal
 * 
 * 2. DROP:
 *    - Descarta elementos nuevos cuando el buffer está lleno
 *    - Solo procesa los elementos que puede manejar
 *    - Útil cuando perder algunos elementos es aceptable
 * 
 * 3. LATEST:
 *    - Mantiene solo el elemento más reciente
 *    - Descarta elementos antiguos cuando llegan nuevos
 *    - Útil cuando solo necesitas el estado más actual
 * 
 * 4. ERROR:
 *    - Lanza una excepción cuando el buffer se desborda
 *    - Fuerza el manejo explícito del error
 *    - Útil para detectar problemas de rendimiento
 * 
 * 5. IGNORE:
 *    - Ignora las solicitudes de backpressure
 *    - Puede causar problemas si no se maneja correctamente
 *    - No recomendado para la mayoría de casos
 * 
 * CUÁNDO USAR CADA ESTRATEGIA:
 * 
 * BUFFER:
 * - Cuando el desbalance es temporal y esperado
 * - Para operaciones donde todos los elementos son importantes
 * - Cuando tienes memoria suficiente
 * 
 * DROP:
 * - Cuando perder algunos elementos es aceptable
 * - Para métricas o datos que se actualizan frecuentemente
 * - Cuando la velocidad es más importante que la completitud
 * 
 * LATEST:
 * - Cuando solo necesitas el estado más reciente
 * - Para actualizaciones de estado o precios
 * - Cuando los valores antiguos no son relevantes
 * 
 * ERROR:
 * - Para detectar problemas de rendimiento
 * - Cuando quieres fallar rápido en lugar de degradar
 * - Para debugging y monitoreo
 * 
 * ⚠️ NOTA: Esta clase está preparada para demostrar estas estrategias.
 * En una implementación completa, se mostrarían ejemplos de cada estrategia
 * usando métodos como onBackpressureBuffer(), onBackpressureDrop(), etc.
 */
/*
    Reactor provides various strategies to handle the backpressure
 */
public class Lec05BackPressureStrategies {

    private static final Logger log = LoggerFactory.getLogger(Lec05BackPressureStrategies.class);


}
