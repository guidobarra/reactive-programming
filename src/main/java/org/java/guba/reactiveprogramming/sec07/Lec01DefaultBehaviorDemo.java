package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Lec01: Comportamiento por Defecto - Thread Actual
 * 
 * Esta clase demuestra el comportamiento por defecto de Reactor cuando NO se especifica
 * ningún Scheduler. Por defecto, TODO el trabajo se ejecuta en el hilo actual (el hilo
 * que llama a subscribe()).
 * 
 * COMPORTAMIENTO POR DEFECTO:
 * 
 * - Sin subscribeOn() ni publishOn(): Todo el pipeline se ejecuta en el mismo hilo
 * - El hilo que llama a subscribe() es el que ejecuta toda la cadena reactiva
 * - Esto incluye: creación del Publisher, emisión de datos, operadores, y el Subscriber
 * - Si el hilo se bloquea, TODO el pipeline se bloquea
 * 
 * CARACTERÍSTICAS:
 * 
 * 1. Ejecución síncrona:
 *    - Todo se ejecuta secuencialmente en el mismo hilo
 *    - No hay cambio de contexto entre operadores
 *    - Simple pero puede bloquear el hilo principal
 * 
 * 2. Sin overhead de cambio de hilos:
 *    - No hay costo de cambio de contexto entre hilos
 *    - Más rápido para operaciones rápidas y no bloqueantes
 *    - Ideal para operaciones CPU-bound rápidas
 * 
 * 3. Bloqueo potencial:
 *    - Si alguna operación bloquea, bloquea todo el hilo
 *    - Puede bloquear el hilo principal de la aplicación
 *    - No es adecuado para operaciones I/O bloqueantes
 * 
 * CASOS DE USO PARA COMPORTAMIENTO POR DEFECTO:
 * 
 * - Operaciones rápidas y no bloqueantes (transformaciones simples)
 * - Operaciones CPU-bound que son muy rápidas
 * - Cuando quieres ejecución síncrona y predecible
 * - Para pruebas y ejemplos simples
 * 
 * CUÁNDO NO USAR COMPORTAMIENTO POR DEFECTO:
 * 
 * - Operaciones I/O bloqueantes (llamadas a BD, APIs, archivos)
 * - Operaciones que toman mucho tiempo
 * - Cuando necesitas no bloquear el hilo principal
 * - Para operaciones que deben ejecutarse en paralelo
 * 
 * ⚠️ IMPORTANTE:
 * 
 * En aplicaciones reales, especialmente con I/O bloqueante, necesitarás usar
 * subscribeOn() o publishOn() para evitar bloquear el hilo principal. Este comportamiento
 * por defecto es útil para entender cómo funciona Reactor, pero en producción generalmente
 * necesitarás especificar Schedulers apropiados.
 * 
 * Esta clase es fundamental para entender por qué necesitamos subscribeOn() y publishOn()
 * en aplicaciones reales.
 */
/*
    By default, the current thread is doing all the work
 */
public class Lec01DefaultBehaviorDemo {

    private static final Logger log = LoggerFactory.getLogger(Lec01DefaultBehaviorDemo.class);

    public static void main(String[] args) {

        var flux = Flux.create(sink -> {
                           for (int i = 1; i < 3; i++) {
                               log.info("generating: {}", i);
                               sink.next(i);
                           }
                           sink.complete();
                       })
                       .doOnNext(v -> log.info("value: {}", v));

        Runnable runnable = () -> flux.subscribe(Util.subscriber("sub1"));

        Thread.ofPlatform().start(runnable);

    }

}
