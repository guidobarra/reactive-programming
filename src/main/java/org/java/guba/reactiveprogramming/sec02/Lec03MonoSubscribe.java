package org.java.guba.reactiveprogramming.sec02;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Lec03: Métodos Sobrecargados de subscribe()
 * 
 * Esta clase demuestra las diferentes variantes del método subscribe() en Mono.
 * 
 * El método subscribe() puede recibir:
 * 1. Consumer<T> onNext: Maneja cada elemento recibido
 * 2. Consumer<Throwable> onError: Maneja errores
 * 3. Runnable onComplete: Se ejecuta cuando el flujo completa
 * 4. Consumer<Subscription> onSubscribe: Se ejecuta cuando se recibe la Subscription
 * 
 * Diferencia clave: Permite manejar todos los eventos del ciclo de vida reactivo
 * (onNext, onError, onComplete, onSubscribe) de manera declarativa sin necesidad
 * de implementar la interfaz Subscriber completa.
 */
public class Lec03MonoSubscribe {

    private static final Logger log = LoggerFactory.getLogger(Lec03MonoSubscribe.class);

    public static void main(String[] args) {

        var mono = Mono.just(1);

        mono.subscribe(
                i -> log.info("received: {}", i),
                err -> log.error("error", err),
                () -> log.info("completed"),
                subscription -> subscription.request(1)
        );

    }

}
