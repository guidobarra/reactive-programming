package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec01.subscriber.SubscriberImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Lec04: Flux.create() y la Demanda del Downstream (Backpressure)
 * 
 * Esta clase demuestra un aspecto CRÍTICO de Flux.create(): Por defecto,
 * NO respeta la demanda del downstream (backpressure). Esto es por diseño.
 * 
 * ⚠️ COMPORTAMIENTO POR DEFECTO:
 * - Flux.create() emite TODOS los elementos INMEDIATAMENTE
 * - NO espera a que el suscriptor solicite elementos con request()
 * - Puede causar problemas de memoria si emites muchos elementos
 * - El suscriptor puede cancelar, pero los elementos ya emitidos se pierden
 * 
 * Dos modos de operación:
 * 
 * 1. produceEarly() - Comportamiento por defecto (NO recomendado para muchos elementos):
 *    - Emite todos los elementos inmediatamente
 *    - Ignora las solicitudes del suscriptor
 *    - Útil solo para pocos elementos o cuando quieres emitir todo de una vez
 * 
 * 2. produceOnDemand() - Respeta la demanda (RECOMENDADO):
 *    - Usa fluxSink.onRequest() para escuchar solicitudes del suscriptor
 *    - Solo genera elementos cuando el suscriptor los solicita
 *    - Respeta el backpressure correctamente
 *    - Más eficiente en memoria y recursos
 * 
 * Cómo respetar la demanda:
 * ```java
 * Flux.create(fluxSink -> {
 *     fluxSink.onRequest(request -> {
 *         // Solo genera 'request' elementos
 *         for (int i = 0; i < request && !fluxSink.isCancelled(); i++) {
 *             fluxSink.next(generateElement());
 *         }
 *     });
 * })
 * ```
 * 
 * Diferencia con Flux.generate():
 * - Flux.generate(): SIEMPRE respeta la demanda automáticamente
 * - Flux.create(): Por defecto NO respeta la demanda (debes implementarlo manualmente)
 * 
 * Cuándo usar cada modo:
 * - produceEarly: Cuando tienes pocos elementos o necesitas emitir todo inmediatamente
 * - produceOnDemand: Cuando tienes muchos elementos o generación costosa (SIEMPRE preferible)
 * 
 * ⚠️ Mejores prácticas:
 * - SIEMPRE implementa onRequest() cuando generas muchos elementos
 * - Verifica fluxSink.isCancelled() antes de emitir
 * - Respeta el número de elementos solicitados (request)
 * - Esto previene problemas de memoria y mejora la eficiencia
 * 
 * Esta lección es fundamental para entender cómo manejar backpressure
 * correctamente en Flux.create().
 */
public class Lec04FluxCreateDownstreamDemand {

    private static final Logger log = LoggerFactory.getLogger(Lec04FluxCreateDownstreamDemand.class);

    public static void main(String[] args) {

        produceOnDemand();

    }

    private static void produceEarly(){
        var subscriber = new SubscriberImpl();
        Flux.<String>create(fluxSink -> {
            for (int i = 0; i < 10; i++) {
                var name = Util.faker().name().firstName();
                log.info("generated: {}", name);
                fluxSink.next(name);
            }
            fluxSink.complete();
        }).subscribe(subscriber);


        Util.sleepSeconds(2);
        subscriber.getSubscription().request(2);
        Util.sleepSeconds(2);
        subscriber.getSubscription().request(2);
        Util.sleepSeconds(2);
        subscriber.getSubscription().cancel();
        subscriber.getSubscription().request(2);
    }

    private static void produceOnDemand(){
        var subscriber = new SubscriberImpl();
        Flux.<String>create(fluxSink -> {

            fluxSink.onRequest(request -> {
                for (int i = 0; i < request && !fluxSink.isCancelled(); i++) {
                    var name = Util.faker().name().firstName();
                    log.info("generated: {}", name);
                    fluxSink.next(name);
                }
            });


        }).subscribe(subscriber);


        Util.sleepSeconds(2);
        subscriber.getSubscription().request(2);
        Util.sleepSeconds(2);
        subscriber.getSubscription().request(2);
        Util.sleepSeconds(2);
        subscriber.getSubscription().cancel();
        subscriber.getSubscription().request(2);
    }


}
