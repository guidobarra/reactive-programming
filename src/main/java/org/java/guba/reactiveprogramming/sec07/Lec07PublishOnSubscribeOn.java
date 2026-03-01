package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec07: Combinación de publishOn() y subscribeOn()
 * 
 * Esta clase demuestra cómo combinar publishOn() y subscribeOn() en el mismo pipeline
 * para tener control total sobre dónde se ejecuta cada parte del flujo reactivo.
 * 
 * COMBINACIÓN PODEROSA:
 * 
 * subscribeOn() + publishOn() permite:
 * - Controlar dónde se ejecuta la fuente (upstream)
 * - Controlar dónde se ejecutan partes específicas del pipeline (downstream)
 * - Optimizar diferentes partes del flujo con diferentes Schedulers
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo (orden en el código):
 * - Flux.create() se ejecuta en el hilo que llama a subscribe() (NO hay subscribeOn antes)
 * - publishOn(Schedulers.parallel()) cambia el hilo para los siguientes operadores
 * - doOnNext() se ejecuta en parallel (después del publishOn)
 * - doFirst("first1") se ejecuta en parallel (después del publishOn)
 * - subscribeOn(Schedulers.boundedElastic()) está DESPUÉS de publishOn, así que NO tiene efecto
 *    (porque subscribeOn solo afecta si está más cercano a la fuente que otros subscribeOn)
 *    ⚠️ IMPORTANTE: Como subscribeOn() está después de publishOn(), NO afecta Flux.create()
 * - doFirst("first2") se ejecuta en parallel (sigue después del publishOn)
 * - El Subscriber se ejecuta en parallel
 * 
 * REGLAS DE PRECEDENCIA:
 * 
 * 1. subscribeOn():
 *    - Solo el más cercano a la fuente tiene efecto
 *    - En este caso, subscribeOn() está DESPUÉS de publishOn(), así que NO tiene efecto
 *    - La fuente (Flux.create) se ejecuta en el hilo que llama a subscribe() (comportamiento por defecto)
 *    - ⚠️ Para que subscribeOn() tenga efecto, debe estar ANTES de publishOn() o más cerca de la fuente
 * 
 * 2. publishOn():
 *    - Cada publishOn() cambia el hilo para los operadores siguientes
 *    - En este caso, todo después del publishOn() se ejecuta en parallel
 * 
 * PATRÓN RECOMENDADO:
 * 
 * Orden ideal en el pipeline:
 * ```java
 * Flux.create(...)                    // Fuente bloqueante
 *     .subscribeOn(boundedElastic)    // Fuente en boundedElastic
 *     .map(...)                        // En boundedElastic
 *     .publishOn(parallel)             // Cambia a parallel
 *     .map(...)                        // En parallel (CPU-bound)
 *     .publishOn(boundedElastic)      // Cambia a boundedElastic
 *     .flatMap(...)                    // En boundedElastic (I/O)
 * ```
 * 
 * VENTAJAS DE LA COMBINACIÓN:
 * 
 * 1. Optimización selectiva:
 *    - Usas el Scheduler apropiado para cada tipo de operación
 *    - I/O bloqueante en boundedElastic
 *    - CPU-bound en parallel
 *    - Máximo rendimiento
 * 
 * 2. Flexibilidad:
 *    - Puedes cambiar el hilo múltiples veces
 *    - Cada parte del pipeline puede ejecutarse donde sea más eficiente
 *    - Control granular del contexto de ejecución
 * 
 * 3. Separación de responsabilidades:
 *    - subscribeOn() para la fuente
 *    - publishOn() para optimización downstream
 *    - Código más claro y mantenible
 * 
 * CASOS DE USO REALES:
 * 
 * 1. Pipeline con múltiples tipos de operaciones:
 *    - Lee de BD (subscribeOn boundedElastic)
 *    - Transforma datos (publishOn parallel)
 *    - Escribe a otro servicio (publishOn boundedElastic)
 * 
 * 2. Procesamiento de datos complejo:
 *    - Recibe datos (subscribeOn)
 *    - Procesa en paralelo (publishOn parallel)
 *    - Agrega resultados (publishOn single)
 * 
 * 3. Optimización de rendimiento:
 *    - Identifica cuellos de botella
 *    - Usa subscribeOn() para la fuente bloqueante
 *    - Usa publishOn() para optimizar partes específicas
 * 
 * DIFERENCIAS CLAVE:
 * 
 * subscribeOn():
 * - Afecta TODO el pipeline (pero solo el más cercano a la fuente)
 * - Ideal para operaciones bloqueantes en la fuente
 * - Se coloca cerca de la fuente
 * 
 * publishOn():
 * - Solo afecta operadores después de él
 * - Ideal para optimizar partes específicas
 * - Puede aparecer múltiples veces
 * 
 * CUÁNDO USAR CADA UNO:
 * 
 * Usa subscribeOn() cuando:
 * - La fuente es bloqueante (Flux.create bloqueante, llamadas a BD)
 * - Quieres que todo se ejecute en un Scheduler específico
 * - Tienes operaciones bloqueantes al inicio del pipeline
 * 
 * Usa publishOn() cuando:
 * - Solo partes específicas necesitan cambiar de hilo
 * - Quieres optimizar selectivamente el pipeline
 * - Necesitas diferentes Schedulers para diferentes partes
 * 
 * Usa ambos cuando:
 * - La fuente es bloqueante Y partes del pipeline necesitan optimización
 * - Quieres máximo control sobre la ejecución
 * - Tienes un pipeline complejo con múltiples tipos de operaciones
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - subscribeOn() solo el más cercano a la fuente tiene efecto
 * - publishOn() cada uno cambia el hilo para los siguientes
 * - Combínalos para máximo control y optimización
 * - El orden importa: subscribeOn() cerca de la fuente, publishOn() donde necesites
 * 
 * Esta combinación es fundamental para crear pipelines reactivos eficientes y optimizados.
 */
/*
    publish on for downstream!
    subscribeOn for upstream
 */
public class Lec07PublishOnSubscribeOn {

    private static final Logger log = LoggerFactory.getLogger(Lec07PublishOnSubscribeOn.class);

    public static void main(String[] args) {

        var flux = Flux.create(sink -> {
                           for (int i = 1; i < 3; i++) {
                               log.info("generating: {}", i);
                               sink.next(i);
                           }
                           sink.complete();
                       })
                       .publishOn(Schedulers.parallel())
                       .doOnNext(v -> log.info("value: {}", v))
                       .doFirst(() -> log.info("first1"))
                       .subscribeOn(Schedulers.boundedElastic())
                       .doFirst(() -> log.info("first2"));


        Runnable runnable1 = () -> flux.subscribe(Util.subscriber("sub1"));

        Thread.ofPlatform().start(runnable1);

        Util.sleepSeconds(2);


    }

}
