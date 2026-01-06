package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lec10: Retraso de la Creación del Publisher con Mono.defer()
 * 
 * Mono.defer() retrasa TANTO la creación del Publisher como su ejecución
 * hasta que hay una suscripción.
 * 
 * Características:
 * - El Supplier dentro de defer() se ejecuta SOLO cuando hay una suscripción
 * - Retorna un nuevo Publisher cada vez que se suscribe alguien
 * - Útil cuando la CREACIÓN del Publisher es costosa o depende de estado mutable
 * 
 * Casos de uso:
 * - Cuando la creación del Publisher requiere operaciones costosas
 * - Cuando necesitas un Publisher "fresco" para cada suscripción
 * - Cuando el Publisher depende de estado que puede cambiar entre suscripciones
 * 
 * Diferencia con Mono.fromSupplier():
 * - fromSupplier: Retrasa la EJECUCIÓN de la lógica, pero el Publisher se crea inmediatamente
 * - defer: Retrasa la CREACIÓN del Publisher completo hasta la suscripción
 * 
 * Diferencia con Mono.just():
 * - Mono.just(): Crea y evalúa inmediatamente
 * - Mono.defer(): Crea y evalúa solo al suscribirse
 * 
 * Ejemplo: Si crear el Publisher requiere preparar datos costosos (como en createPublisher()),
 * defer() asegura que esa preparación solo ocurra cuando realmente se necesita.
 */
public class Lec10MonoDefer {

    private static final Logger log = LoggerFactory.getLogger(Lec10MonoDefer.class);

    public static void main(String[] args) {

        Mono.defer(Lec10MonoDefer::createPublisher)
                .subscribe(Util.subscriber());

    }

    // time-consuming publisher creation
    private static Mono<Integer> createPublisher(){
        log.info("creating publisher");
        var list = List.of(1, 2, 3);
        Util.sleepSeconds(1);
        return Mono.fromSupplier(() -> sum(list));
    }

    // time-consuming business logic
    private static int sum(List<Integer> list) {
        log.info("finding the sum of {}", list);
        Util.sleepSeconds(3);
        return list.stream().mapToInt(a -> a).sum();
    }
    
}
