package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec02: Múltiples Suscriptores y Operadores (filter, map)
 * 
 * Esta clase demuestra cómo un mismo Flux puede tener múltiples suscriptores,
 * cada uno con su propia cadena de operadores.
 * 
 * Conceptos clave:
 * - Un Flux puede tener múltiples suscriptores independientes
 * - Cada suscripción crea una nueva ejecución del pipeline
 * - Los operadores como filter() y map() se aplican a cada suscripción
 * - Cada suscriptor recibe su propia instancia de los datos
 * 
 * Operadores demostrados:
 * - filter(Predicate): Filtra elementos que cumplen una condición
 * - map(Function): Transforma cada elemento
 * 
 * Diferencia importante:
 * - Cada subscribe() crea una NUEVA suscripción independiente
 * - Si el Flux emite datos, cada suscriptor los recibe por separado
 * - Los operadores se ejecutan para cada suscripción
 * 
 * Cuándo usar:
 * - Cuando necesitas procesar el mismo flujo de datos de diferentes maneras
 * - Para aplicar diferentes transformaciones al mismo origen de datos
 * - Para demostrar la independencia de las suscripciones
 */
public class Lec02MultipleSubscribers {


    public static void main(String[] args) {

        var flux = Flux.just(1,2,3,4,5,6);

        flux.subscribe(Util.subscriber("sub1"));
        flux.filter(i -> i > 7)
                .subscribe(Util.subscriber("sub2"));
        flux
                .filter(i -> i % 2 == 0)
                .map(i -> i + "a")
                .subscribe(Util.subscriber("sub3"));

    }

}
