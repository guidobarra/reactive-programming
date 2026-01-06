package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lec06: Creación de Mono con Mono.fromCallable()
 * 
 * Mono.fromCallable() retrasa la ejecución hasta que un Subscriber se suscribe,
 * similar a fromSupplier pero con soporte para excepciones verificadas.
 * 
 * Características:
 * - Evaluación PEREZOSA (lazy): El Callable NO se ejecuta hasta la suscripción
 * - El Callable PUEDE lanzar excepciones verificadas (checked exceptions)
 * - Si el Callable retorna null, se emite Mono.empty()
 * - Si el Callable lanza una excepción, se emite Mono.error()
 * - Útil para operaciones que pueden fallar (I/O, llamadas a APIs, etc.)
 * 
 * Diferencia con Mono.fromSupplier():
 * - fromSupplier: NO puede lanzar excepciones verificadas (Supplier no tiene throws)
 * - fromCallable: PUEDE lanzar excepciones verificadas (Callable puede tener throws Exception)
 * 
 * Diferencia con Mono.just():
 * - Mono.just(): Evalúa inmediatamente, no maneja excepciones de manera reactiva
 * - Mono.fromCallable(): Evalúa solo al suscribirse, convierte excepciones en onError()
 */
public class Lec06MonoFromCallable {

    private static final Logger log = LoggerFactory.getLogger(Lec06MonoFromCallable.class);

    public static void main(String[] args) {

        var list = List.of(1, 2, 3);
        Mono.fromCallable(() -> sum(list))
                .subscribe(Util.subscriber());

    }

    private static int sum(List<Integer> list) throws Exception {
        log.info("finding the sum of {}", list);
        return list.stream().mapToInt(a -> a).sum();
    }

}
