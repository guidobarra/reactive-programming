package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec08: Flux.generate() con Estado (State)
 * 
 * Esta clase demuestra cómo usar Flux.generate() con estado para mantener
 * información entre invocaciones del lambda. Esto es útil cuando la generación
 * de cada elemento depende del estado acumulado.
 * 
 * Sintaxis con estado:
 * ```java
 * Flux.generate(
 *     () -> initialState,           // Supplier: Estado inicial
 *     (state, sink) -> {            // BiFunction: (estado actual, sink) -> nuevo estado
 *         // Generar elemento basado en el estado
 *         sink.next(element);
 *         // Actualizar estado
 *         return newState;
 *     }
 * )
 * ```
 * 
 * Componentes:
 * 1. Supplier de estado inicial: Proporciona el valor inicial del estado
 * 2. BiFunction generadora: Recibe el estado actual y el sink, retorna el nuevo estado
 * 3. Estado: Puede ser cualquier objeto que mantenga información entre invocaciones
 * 
 * Características:
 * - El estado se pasa entre invocaciones del lambda
 * - Cada invocación recibe el estado actual y retorna el nuevo estado
 * - El estado puede ser mutable o inmutable (preferible inmutable)
 * - Útil para contadores, acumuladores, o cualquier lógica que dependa de valores previos
 * 
 * Ejemplo demostrado:
 * - Estado: Un contador que cuenta cuántos elementos se han generado
 * - Condición de terminación: Cuando el contador llega a 10 o se encuentra "canada"
 * - El estado se actualiza en cada invocación (counter++)
 * 
 * Ventajas sobre variables externas:
 * - El estado está encapsulado dentro del generate()
 * - Thread-safe por diseño (cada suscripción tiene su propio estado)
 * - No hay problemas de concurrencia
 * - Más funcional y declarativo
 * 
 * Diferencia con Flux.generate() simple:
 * - Sin estado: Cada invocación es independiente
 * - Con estado: Cada invocación puede depender del estado de la anterior
 * 
 * Casos de uso:
 * - Contadores y secuencias numéricas con lógica compleja
 * - Generación de elementos que dependen de elementos anteriores
 * - Acumuladores y agregaciones progresivas
 * - Máquinas de estado y generadores con lógica compleja
 * 
 * Cuándo usar:
 * - Cuando necesitas mantener información entre elementos generados
 * - Cuando la generación de cada elemento depende del anterior
 * - Para implementar lógica de generación más compleja
 * - Cuando necesitas contar, acumular o rastrear información durante la generación
 * 
 * Este patrón es esencial para crear generadores reactivos que necesitan
 * mantener estado entre emisiones, de forma segura y funcional.
 */
public class Lec08GenerateWithState {

    public static void main(String[] args) {

        Flux.generate(
                    () -> 0,
                    (counter, sink) -> {
                        var country = Util.faker().country().name();
                        sink.next(country);
                        counter++;
                        if (counter == 10 || country.equalsIgnoreCase("canada")) {
                            sink.complete();
                        }
                        return counter;
                    }
            )
            .subscribe(Util.subscriber());
    }

}
