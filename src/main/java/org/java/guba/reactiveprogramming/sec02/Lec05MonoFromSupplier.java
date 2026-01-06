package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lec05: Creación de Mono con Mono.fromSupplier()
 * 
 * Mono.fromSupplier() retrasa la ejecución hasta que un Subscriber se suscribe.
 * 
 * Características:
 * - Evaluación PEREZOSA (lazy): El Supplier NO se ejecuta hasta la suscripción
 * - El Supplier NO puede lanzar excepciones verificadas (checked exceptions)
 * - Si el Supplier retorna null, se emite Mono.empty()
 * - Útil para operaciones costosas que queremos ejecutar solo cuando sea necesario
 * 
 * Diferencia con Mono.just():
 * - Mono.just(): Evalúa el valor INMEDIATAMENTE al crear el Mono
 * - Mono.fromSupplier(): Evalúa el valor SOLO cuando hay una suscripción
 * 
 * Diferencia con Mono.fromCallable():
 * - fromSupplier: NO puede lanzar excepciones verificadas
 * - fromCallable: PUEDE lanzar excepciones verificadas (throws Exception)
 */
public class Lec05MonoFromSupplier {

    private static final Logger log = LoggerFactory.getLogger(Lec05MonoFromSupplier.class);

    public static void main(String[] args) {

        var list = List.of(1, 2, 3);
        Mono.fromSupplier(() -> sum(list))
                .subscribe(Util.subscriber());

    }

    private static int sum(List<Integer> list) {
        log.info("finding the sum of {}", list);
        return list.stream().mapToInt(a -> a).sum();
    }

}
