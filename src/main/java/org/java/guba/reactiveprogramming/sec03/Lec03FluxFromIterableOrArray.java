package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Lec03: Creación de Flux desde Iterable o Array
 * 
 * Esta clase demuestra cómo crear un Flux desde colecciones existentes.
 * 
 * Métodos demostrados:
 * 1. Flux.fromIterable(Iterable): Convierte cualquier Iterable (List, Set, etc.) en Flux
 * 2. Flux.fromArray(T[]): Convierte un array en Flux
 * 
 * Características:
 * - Los elementos se emiten en el orden del Iterable/Array
 * - La colección debe existir al momento de crear el Flux
 * - Si la colección es mutable y cambia después, el Flux no refleja los cambios
 * - Emite todos los elementos y luego completa
 * 
 * Diferencia con Flux.just():
 * - Flux.just(): Para valores individuales pasados como argumentos
 * - Flux.fromIterable(): Para colecciones completas (más eficiente para muchos elementos)
 * 
 * Diferencia con Flux.fromStream():
 * - Flux.fromIterable(): La colección ya existe en memoria
 * - Flux.fromStream(): Convierte un Stream de Java (puede ser lazy)
 * 
 * Cuándo usar:
 * - Cuando tienes una colección existente que quieres convertir a Flux
 * - Para integrar código tradicional con programación reactiva
 * - Cuando los datos ya están en memoria y quieres procesarlos reactivamente
 */
public class Lec03FluxFromIterableOrArray {

    public static void main(String[] args) {

        var list = List.of("a", "b", "c");
        Flux.fromIterable(list)
                .subscribe(Util.subscriber());

        Integer[] arr = {1,2,3,4,5,6};
        Flux.fromArray(arr)
                .subscribe(Util.subscriber());

    }

}
