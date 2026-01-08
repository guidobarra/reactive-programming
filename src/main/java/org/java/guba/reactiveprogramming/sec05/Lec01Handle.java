package org.java.guba.reactiveprogramming.sec05;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec01: Operador handle() - Combinación de filter() y map()
 * 
 * El operador handle() es un operador poderoso que combina la funcionalidad de
 * filter() y map() en un solo operador, además de permitir manejo de errores.
 * 
 * Características principales:
 * - Permite transformar elementos (como map())
 * - Permite filtrar elementos (como filter())
 * - Permite emitir errores programáticamente
 * - Control total sobre qué elementos se emiten y cómo
 * 
 * Sintaxis: .handle((item, sink) -> { ... })
 * - Recibe el elemento actual y un SynchronousSink
 * - Puedes llamar a sink.next() para emitir un valor (transformado o no)
 * - Puedes NO llamar a sink.next() para filtrar el elemento
 * - Puedes llamar a sink.error() para emitir un error
 * - Puedes llamar a sink.complete() para terminar el flujo
 * 
 * Ventajas sobre filter() + map() separados:
 * - Un solo operador en lugar de dos
 * - Puedes decidir dinámicamente si emitir o no un elemento
 * - Puedes transformar Y filtrar en la misma operación
 * - Puedes emitir errores basados en condiciones específicas
 * 
 * Ejemplo demostrado:
 * - Elemento 1: Se transforma a -2 (map-like behavior)
 * - Elemento 4: Se filtra (no se emite, filter-like behavior)
 * - Elemento 7: Se emite un error (error handling)
 * - Otros elementos: Se emiten tal cual
 * 
 * Casos de uso:
 * - Cuando necesitas lógica compleja que combina filtrado y transformación
 * - Cuando quieres filtrar elementos basándote en cálculos complejos
 * - Cuando necesitas emitir errores condicionales en el pipeline
 * - Para validación y transformación en un solo paso
 * - Cuando la lógica de filtrado y transformación está estrechamente relacionada
 * 
 * Diferencia con filter() + map():
 * - filter() + map(): Dos operadores separados, más verboso
 * - handle(): Un solo operador, más flexible y conciso
 * 
 * Cuándo usar:
 * - Cuando necesitas filtrar Y transformar en la misma operación
 * - Cuando la lógica de filtrado depende de cálculos complejos
 * - Cuando necesitas emitir errores basados en condiciones específicas
 * - Para simplificar pipelines que requieren múltiples operaciones
 * 
 * ⚠️ Nota: handle() es más potente pero también más complejo. Usa filter() y map()
 * separados cuando sea posible para mejor legibilidad, y handle() cuando necesites
 * la flexibilidad adicional.
 */
public class Lec01Handle {

    public static void main(String[] args) {

       Flux.range(1, 10)
               .handle((item, sink) -> {
                   switch (item){
                       case 1 -> sink.next(-2);
                       case 4 -> {}
                       case 7 -> sink.error(new RuntimeException("oops"));
                       default -> sink.next(item);
                   }
               })
               .cast(Integer.class)
               .subscribe(Util.subscriber());
    }

}
