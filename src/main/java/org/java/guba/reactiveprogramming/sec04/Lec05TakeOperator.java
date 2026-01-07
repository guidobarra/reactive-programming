package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec05: Operador take() - Limitar Elementos Emitidos
 * 
 * Esta clase demuestra los diferentes operadores take() que permiten limitar
 * cuántos elementos se emiten desde un Flux. Es similar a limit() en Java Streams.
 * 
 * Variantes del operador take():
 * 
 * 1. take(long n):
 *    - Toma exactamente 'n' elementos del Flux
 *    - Después de tomar 'n' elementos, cancela la suscripción automáticamente
 *    - Similar a limit(n) en Java Streams
 *    - Útil cuando sabes exactamente cuántos elementos necesitas
 * 
 * 2. takeWhile(Predicate):
 *    - Toma elementos MIENTRAS la condición sea verdadera
 *    - Se detiene cuando encuentra el PRIMER elemento que NO cumple la condición
 *    - NO incluye el elemento que rompe la condición
 *    - Similar a takeWhile() en Java Streams
 * 
 * 3. takeUntil(Predicate):
 *    - Toma elementos HASTA que la condición sea verdadera
 *    - Se detiene cuando encuentra el PRIMER elemento que SÍ cumple la condición
 *    - SÍ incluye el elemento que cumple la condición (el último)
 *    - Útil para tomar elementos hasta encontrar un marcador
 * 
 * Diferencia clave takeWhile vs takeUntil:
 * - takeWhile(i -> i < 5): Toma mientras sea menor que 5, NO incluye el 5
 * - takeUntil(i -> i == 5): Toma hasta encontrar el 5, SÍ incluye el 5
 * 
 * Comportamiento común:
 * - Todos cancelan automáticamente la suscripción cuando se cumple la condición
 * - Esto detiene la generación de elementos restantes (eficiente)
 * - El downstream recibe onComplete() después de los elementos tomados
 * 
 * Casos de uso:
 * - take(n): Cuando necesitas exactamente N elementos
 * - takeWhile(): Para filtrar elementos al inicio de un flujo
 * - takeUntil(): Para tomar elementos hasta encontrar un marcador o condición final
 * 
 * Ventajas sobre procesar todos y filtrar:
 * - Eficiencia: Cancela la generación cuando ya no necesitas más elementos
 * - Ahorro de recursos: No genera elementos innecesarios
 * - Mejor rendimiento: Especialmente útil con Flux infinitos o grandes
 * 
 * Diferencia con filter():
 * - filter(): Procesa TODOS los elementos y filtra los que no cumplen
 * - take(): Cancela después de tomar los elementos necesarios (más eficiente)
 * 
 * Cuándo usar:
 * - take(n): Cuando necesitas un número fijo de elementos
 * - takeWhile(): Cuando quieres elementos consecutivos que cumplan una condición
 * - takeUntil(): Cuando quieres elementos hasta encontrar un marcador específico
 * 
 * Estos operadores son esenciales para trabajar con Flux infinitos o grandes
 * de manera eficiente.
 */
public class Lec05TakeOperator {

    public static void main(String[] args) {


        takeUntil();


    }

    private static void take(){
        Flux.range(1, 10)
            .log("take")
            .take(3)
            .log("sub")
            .subscribe(Util.subscriber());
    }

    private static void takeWhile() {
        Flux.range(1, 10)
            .log("take")
            .takeWhile(i -> i < 5) // stop when the condition is not met
            .log("sub")
            .subscribe(Util.subscriber());
    }

    private static void takeUntil() {
        Flux.range(1, 10)
            .log("take")
            .takeUntil(i -> i < 5) // stop when the condition is met + allow the last item
            .log("sub")
            .subscribe(Util.subscriber());
    }

}
