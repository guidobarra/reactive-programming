package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec07: Flux.generate() con Condiciones de Terminación
 * 
 * Esta clase demuestra dos formas de terminar un Flux.generate():
 * 1. Terminación explícita dentro del lambda (demo1)
 * 2. Terminación usando operadores como takeUntil() (demo2)
 * 
 * demo1() - Terminación explícita:
 * - La lógica de terminación está DENTRO del lambda de generate()
 * - Usa sink.complete() cuando se cumple la condición
 * - Control total sobre cuándo terminar
 * - La condición se evalúa en cada invocación del lambda
 * 
 * demo2() - Terminación con operadores:
 * - Usa takeUntil() DESPUÉS del generate()
 * - El generate() continúa indefinidamente
 * - takeUntil() cancela cuando encuentra el elemento que cumple la condición
 * - Separación de responsabilidades: generación vs terminación
 * 
 * Ventajas de cada enfoque:
 * 
 * Terminación explícita (demo1):
 * - Control preciso sobre cuándo terminar
 * - Puede evitar generar elementos innecesarios
 * - La lógica de terminación está junto a la generación
 * 
 * Terminación con operadores (demo2):
 * - Separación de responsabilidades
 * - Más flexible: puedes cambiar la condición sin modificar el generate()
 * - Reutilizable: el mismo generate() puede usarse con diferentes condiciones
 * - Más declarativo y fácil de leer
 * 
 * Diferencia clave:
 * - demo1: El generate() sabe cuándo terminar (lógica acoplada)
 * - demo2: El generate() solo genera, otro operador decide cuándo terminar (lógica separada)
 * 
 * Cuándo usar cada enfoque:
 * - Terminación explícita: Cuando la condición de terminación es parte integral de la generación
 * - Operadores externos: Cuando quieres separar la generación de la lógica de terminación
 * 
 * Recomendación:
 * - Prefiere usar operadores como takeUntil() cuando sea posible (demo2)
 * - Es más flexible, reutilizable y sigue el principio de responsabilidad única
 * - Usa terminación explícita solo cuando la condición es inseparable de la generación
 * 
 * Este patrón muestra cómo combinar Flux.generate() con operadores para
 * crear flujos más flexibles y mantenibles.
 */
public class Lec07FluxGenerateUntil {

    public static void main(String[] args) {

        demo2();

    }

    private static void demo1() {
        Flux.generate(synchronousSink -> {
                var country = Util.faker().country().name();
                synchronousSink.next(country);
                if (country.equalsIgnoreCase("canada")) {
                    synchronousSink.complete();
                }
            })
            .subscribe(Util.subscriber());
    }

    private static void demo2() {
        Flux.<String>generate(synchronousSink -> {
                var country = Util.faker().country().name();
                synchronousSink.next(country);
            })
            .takeUntil(c -> c.equalsIgnoreCase("canada"))
            .subscribe(Util.subscriber());
    }

}
