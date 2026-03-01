package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec05: publishOn() - Cambiar el Hilo para Downstream
 * 
 * Esta clase demuestra el uso de publishOn() para cambiar el hilo donde se ejecutan
 * los operadores DOWNSTREAM (después de publishOn), sin afectar los operadores upstream.
 * 
 * QUÉ HACE publishOn():
 * 
 * publishOn() cambia el hilo SOLO para los operadores que están DESPUÉS de él en el pipeline:
 * - Los operadores ANTES de publishOn() se ejecutan en el hilo anterior
 * - Los operadores DESPUÉS de publishOn() se ejecutan en el Scheduler especificado
 * - Puedes tener múltiples publishOn() y cada uno cambia el hilo para los siguientes
 * 
 * CARACTERÍSTICAS CLAVE:
 * 
 * 1. Solo afecta DOWNSTREAM:
 *    - Los operadores antes de publishOn() NO cambian de hilo
 *    - Solo los operadores después cambian al nuevo Scheduler
 *    - Útil para cambiar el hilo en puntos específicos del pipeline
 * 
 * 2. Múltiples publishOn() tienen efecto:
 *    - Cada publishOn() cambia el hilo para los operadores siguientes
 *    - Puedes cambiar el hilo múltiples veces en el mismo pipeline
 *    - Útil para diferentes partes del pipeline que necesitan diferentes hilos
 * 
 * 3. Ejecución asíncrona downstream:
 *    - Los operadores después de publishOn() se ejecutan en el nuevo hilo
 *    - No bloquea el hilo anterior
 *    - Permite procesamiento paralelo
 * 
 * DIFERENCIA CRÍTICA CON subscribeOn():
 * 
 * subscribeOn():
 * - Cambia el hilo para TODO el pipeline (upstream y downstream)
 * - Solo el más cercano a la fuente tiene efecto
 * - Afecta desde la fuente hasta el suscriptor
 * 
 * publishOn():
 * - Cambia el hilo SOLO para downstream (operadores después)
 * - Cada publishOn() tiene efecto independiente
 * - No afecta los operadores antes de él
 * 
 * EJEMPLO DEMOSTRADO:
 * 
 * En este código:
 * - Flux.create() se ejecuta en el hilo actual (main o el que llama a subscribe)
 * - publishOn(Schedulers.parallel()) cambia el hilo
 * - doOnNext() se ejecuta en parallel
 * - doFirst("first1") se ejecuta en parallel (está después de publishOn)
 * - publishOn(Schedulers.boundedElastic()) cambia el hilo nuevamente
 * - doFirst("first2") se ejecuta en boundedElastic
 * - El Subscriber se ejecuta en boundedElastic
 * 
 * VENTAJAS DE publishOn():
 * 
 * 1. Control granular:
 *    - Puedes cambiar el hilo solo donde lo necesitas
 *    - No afecta toda la cadena como subscribeOn()
 *    - Útil para optimizar partes específicas del pipeline
 * 
 * 2. Múltiples cambios de hilo:
 *    - Puedes usar diferentes Schedulers para diferentes partes
 *    - Ejemplo: CPU-bound en parallel(), I/O en boundedElastic()
 *    - Máxima flexibilidad
 * 
 * 3. Optimización selectiva:
 *    - Solo cambias el hilo donde realmente lo necesitas
 *    - Evitas overhead innecesario de cambio de contexto
 *    - Mejor rendimiento cuando se usa correctamente
 * 
 * CASOS DE USO:
 * 
 * 1. Procesamiento CPU-intensivo después de I/O:
 *    - Lee datos (I/O) en un hilo, procesa (CPU) en otro
 *    - publishOn(Schedulers.parallel()) después de la lectura
 * 
 * 2. Diferentes tipos de operaciones:
 *    - Operaciones bloqueantes en boundedElastic()
 *    - Operaciones CPU en parallel()
 *    - Cambias el hilo según el tipo de operación
 * 
 * 3. Optimización de pipeline:
 *    - Mantienes la fuente en el hilo actual
 *    - Cambias el hilo solo para operaciones costosas
 *    - Mejor uso de recursos
 * 
 * PATRÓN COMÚN: subscribeOn() + publishOn()
 * 
 * Combinación poderosa:
 * - subscribeOn(): Para operaciones bloqueantes en la fuente
 * - publishOn(): Para cambiar el hilo en partes específicas del pipeline
 * 
 * Ejemplo:
 * ```java
 * Flux.create(...)  // Bloqueante, necesita subscribeOn()
 *     .subscribeOn(Schedulers.boundedElastic())  // Fuente en boundedElastic
 *     .map(...)  // Se ejecuta en boundedElastic
 *     .publishOn(Schedulers.parallel())  // Cambia a parallel
 *     .map(...)  // Se ejecuta en parallel
 * ```
 * 
 * CUÁNDO USAR publishOn() vs subscribeOn():
 * 
 * Usa subscribeOn() cuando:
 * - La fuente es bloqueante (Flux.create bloqueante, llamadas a BD, etc.)
 * - Quieres que TODO el pipeline se ejecute en un Scheduler específico
 * 
 * Usa publishOn() cuando:
 * - Solo partes específicas del pipeline necesitan cambiar de hilo
 * - Quieres optimizar selectivamente partes del pipeline
 * - Necesitas diferentes Schedulers para diferentes partes
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - publishOn() solo afecta los operadores después de él
 * - Cada publishOn() cambia el hilo independientemente
 * - Úsalo para optimización granular del pipeline
 * - Combínalo con subscribeOn() para máximo control
 */
/*
    publish on for downstream!
 */
public class Lec05PublishOn {

    private static final Logger log = LoggerFactory.getLogger(Lec05PublishOn.class);

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
                       .publishOn(Schedulers.boundedElastic())
                       .doFirst(() -> log.info("first2"));


        Runnable runnable1 = () -> flux.subscribe(Util.subscriber("sub1"));

        Thread.ofPlatform().start(runnable1);

        Util.sleepSeconds(2);


    }

}
