package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec05: Creación de Flux con Flux.range()
 * 
 * Flux.range() crea un Flux que emite una secuencia de números enteros
 * consecutivos en un rango especificado.
 * 
 * Sintaxis: Flux.range(start, count)
 * - start: Número inicial (inclusive)
 * - count: Cantidad de números a emitir
 * - Emite: start, start+1, start+2, ..., start+count-1
 * 
 * Características:
 * - Genera números enteros de forma eficiente (no crea una lista en memoria)
 * - Evaluación perezosa: Los números se generan solo cuando se solicitan
 * - Útil para generar secuencias numéricas sin necesidad de crear colecciones
 * - Muy eficiente en memoria para rangos grandes
 * 
 * Ejemplo:
 * - Flux.range(3, 10) emite: 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
 * 
 * Diferencia con crear una List y usar fromIterable():
 * - Flux.range(): No crea una lista en memoria, genera números on-demand
 * - fromIterable(List): Crea toda la lista en memoria primero
 * 
 * Casos de uso:
 * - Generar índices para procesamiento
 * - Crear secuencias numéricas para pruebas
 * - Combinar con map() para generar datos basados en índices
 * - Cuando necesitas un rango grande pero no quieres consumir memoria
 * 
 * Cuándo usar:
 * - Para generar secuencias numéricas de forma eficiente
 * - Cuando necesitas índices o contadores en un pipeline reactivo
 * - Para evitar crear colecciones grandes en memoria
 */
public class Lec05FluxRange {

    public static void main(String[] args) {

        Flux.range(3, 10)
                .subscribe(Util.subscriber());

        // assignment - generate 10 random first names
        Flux.range(1, 10)
                .map(i -> Util.faker().name().firstName())
                .subscribe(Util.subscriber());

    }

}
