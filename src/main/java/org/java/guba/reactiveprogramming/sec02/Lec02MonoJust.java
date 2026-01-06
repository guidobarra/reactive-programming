package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.sec01.subscriber.SubscriberImpl;
import reactor.core.publisher.Mono;

/**
 * Lec02: Creación de Mono con Mono.just()
 * 
 * Mono.just() se usa cuando el valor a emitir YA ESTÁ EN MEMORIA.
 * 
 * Características:
 * - El valor se evalúa INMEDIATAMENTE al crear el Mono (no es lazy)
 * - Si el valor es null, lanzará NullPointerException
 * - Una vez que el Publisher envía onComplete(), las solicitudes adicionales
 *   o cancelaciones no tienen efecto
 * 
 * Diferencia con fromSupplier: Mono.just() evalúa el valor inmediatamente,
 * mientras que fromSupplier retrasa la evaluación hasta la suscripción.
 */
public class Lec02MonoJust {

    public static void main(String[] args) {

        var mono = Mono.just("vins");
        var subscriber = new SubscriberImpl();
        mono.subscribe(subscriber);

        subscriber.getSubscription().request(10);

        // adding these will have no effect as producer already sent complete
        subscriber.getSubscription().request(10);
        subscriber.getSubscription().cancel();

    }


}
