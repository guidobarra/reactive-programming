package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec10: Creación de Flux Vacío y Flux con Error
 * 
 * Esta clase demuestra cómo crear Flux que representan estados especiales:
 * un flujo vacío (sin datos) y un flujo con error.
 * 
 * Métodos demostrados:
 * 
 * 1. Flux.empty():
 *    - Crea un Flux que completa inmediatamente SIN emitir ningún elemento
 *    - Emite: onSubscribe → onComplete (sin onNext)
 *    - Útil cuando no hay datos disponibles pero no es un error
 *    - Equivalente a una lista vacía en programación tradicional
 * 
 * 2. Flux.error(Throwable):
 *    - Crea un Flux que emite un error inmediatamente
 *    - Emite: onSubscribe → onError (sin onNext ni onComplete)
 *    - Útil para propagar errores en el flujo reactivo
 *    - El error puede ser capturado y manejado por operadores como onErrorResume()
 * 
 * Casos de uso de Flux.empty():
 * - Cuando una búsqueda no encuentra resultados
 * - Cuando una operación se completa sin datos
 * - Para representar "no hay datos" de forma explícita
 * - En operaciones condicionales que pueden no retornar datos
 * 
 * Casos de uso de Flux.error():
 * - Cuando ocurre una condición de error que debe propagarse
 * - Para validaciones que fallan
 * - Para manejar excepciones en el flujo reactivo
 * - Cuando una operación no puede completarse exitosamente
 * 
 * Diferencia con Mono.empty() y Mono.error():
 * - Mono: Representa 0 o 1 elemento
 * - Flux: Representa 0, 1 o muchos elementos
 * - Ambos tienen empty() y error(), pero Flux puede emitir múltiples elementos antes de completar
 * 
 * Diferencia con lanzar excepción directamente:
 * - Lanzar excepción: Interrumpe el flujo de ejecución
 * - Flux.error(): Integra el error en el flujo reactivo, puede ser manejado
 * 
 * Cuándo usar:
 * - Flux.empty(): Cuando no hay datos pero la operación fue exitosa
 * - Flux.error(): Cuando hay un error que debe manejarse reactivamente
 * - Ambos son útiles para manejar casos límite en pipelines reactivos
 */
public class Lec10FluxEmptyError {

    public static void main(String[] args) {

        Flux.empty()
                .subscribe(Util.subscriber());

        Flux.error(new RuntimeException("oops"))
                .subscribe(Util.subscriber());

    }

}
