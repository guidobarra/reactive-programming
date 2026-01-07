package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec01: Creación de Flux con Flux.just()
 * 
 * Flux.just() crea un Flux que emite una secuencia de elementos proporcionados
 * y luego completa. Los valores deben estar YA EN MEMORIA.
 * 
 * Características:
 * - Evaluación INMEDIATA: Los valores se evalúan al crear el Flux (no es lazy)
 * - Puede aceptar múltiples valores de diferentes tipos (pero se recomienda homogeneidad)
 * - Si algún valor es null, lanzará NullPointerException
 * - Útil para secuencias cortas de datos conocidos
 * 
 * Diferencia con Flux.fromIterable():
 * - Flux.just(): Para valores individuales pasados como argumentos
 * - Flux.fromIterable(): Para colecciones existentes (List, Set, etc.)
 * 
 * Diferencia con Flux.fromSupplier():
 * - Flux.just(): Evalúa valores inmediatamente
 * - Flux.fromSupplier(): Retrasa la evaluación hasta la suscripción
 * 
 * Cuándo usar:
 * - Cuando tienes valores conocidos y disponibles inmediatamente
 * - Para secuencias cortas y fijas de datos
 * - Para pruebas y ejemplos simples
 */
public class Lec01FluxJust {


    public static void main(String[] args) {

        Flux.just(1, 2, 3, "sam")
                .subscribe(Util.subscriber());

    }

}
