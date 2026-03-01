package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec03: Múltiples subscribeOn() - Precedencia
 * 
 * Esta clase demuestra qué sucede cuando hay múltiples subscribeOn() en el mismo pipeline
 * y cuál tiene precedencia.
 * 
 * REGLA CRÍTICA DE subscribeOn():
 * 
 * ⚠️ SOLO EL subscribeOn() MÁS CERCANO A LA FUENTE TIENE EFECTO
 * 
 * - Si hay múltiples subscribeOn(), solo el primero (más cercano al Publisher) se aplica
 * - Los subscribeOn() posteriores son IGNORADOS
 * - Esto es diferente de publishOn(), donde cada uno tiene efecto
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo:
 * - subscribeOn(Schedulers.newParallel("gubathread")) está más cerca de la fuente
 * - subscribeOn(Schedulers.boundedElastic()) está más lejos
 * - RESULTADO: Solo el primero (newParallel) se aplica, boundedElastic es ignorado
 * - Todo el pipeline se ejecuta en el Scheduler newParallel
 * 
 * POR QUÉ ESTE COMPORTAMIENTO:
 * 
 * 1. Lógica de diseño:
 *    - subscribeOn() afecta TODO el pipeline desde la fuente
 *    - No tiene sentido tener múltiples subscribeOn() porque solo puede haber un hilo inicial
 *    - El más cercano a la fuente determina dónde comienza todo
 * 
 * 2. Simplicidad:
 *    - Evita confusión sobre qué Scheduler se está usando
 *    - Hace el código más predecible
 *    - Solo necesitas especificar subscribeOn() una vez, cerca de la fuente
 * 
 * DIFERENCIA CON publishOn():
 * 
 * - subscribeOn(): Solo el más cercano a la fuente tiene efecto (los demás se ignoran)
 * - publishOn(): Cada publishOn() cambia el hilo para los operadores siguientes (todos tienen efecto)
 * 
 * MEJORES PRÁCTICAS:
 * 
 * 1. Usa solo UN subscribeOn():
 *    - Colócalo lo más cerca posible de la fuente
 *    - Evita múltiples subscribeOn() en el mismo pipeline
 * 
 * 2. Si necesitas cambiar el hilo en diferentes partes:
 *    - Usa subscribeOn() para la fuente (upstream)
 *    - Usa publishOn() para partes específicas del pipeline (downstream)
 * 
 * 3. Orden recomendado:
 *    - subscribeOn() cerca de la fuente (para operaciones bloqueantes iniciales)
 *    - publishOn() donde necesites cambiar el hilo para operadores específicos
 * 
 * CASOS DE USO:
 * 
 * - Cuando quieres asegurar que toda la cadena se ejecute en un Scheduler específico
 * - Para operaciones bloqueantes que comienzan desde la fuente
 * - Cuando necesitas control total sobre dónde se ejecuta el pipeline
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - No pongas múltiples subscribeOn() esperando que todos tengan efecto
 * - Solo el más cercano a la fuente se aplica
 * - Si necesitas cambiar el hilo en diferentes partes, usa publishOn()
 * - Esta regla es fundamental para entender cómo funcionan los Schedulers en Reactor
 */
/*
    We can have multiple subscribeOn.
    The closest to the source will take the precedence!
 */
public class Lec03MultipleSubscribeOn {

    private static final Logger log = LoggerFactory.getLogger(Lec03MultipleSubscribeOn.class);

    public static void main(String[] args) {

        var flux = Flux.create(sink -> {
                           for (int i = 1; i < 3; i++) {
                               log.info("generating: {}", i);
                               sink.next(i);
                           }
                           sink.complete();
                       })
                       .subscribeOn(Schedulers.newParallel("gubathread"))
                       .doOnNext(v -> log.info("value: {}", v))
                       .doFirst(() -> log.info("first1"))
                       .subscribeOn(Schedulers.boundedElastic())
                       .doFirst(() -> log.info("first2"));


        Runnable runnable1 = () -> flux.subscribe(Util.subscriber("sub1"));

        Thread.ofPlatform().start(runnable1);

        Util.sleepSeconds(2);

    }

}
