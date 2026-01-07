package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec04.helper.NameGenerator;
import reactor.core.publisher.Flux;

/**
 * Lec02: Refactorización de Flux.create() - Separación de Responsabilidades
 * 
 * Esta clase demuestra cómo refactorizar Flux.create() separando la lógica de
 * creación del Flux de la lógica de generación de datos.
 * 
 * Patrón demostrado:
 * - Crear un objeto generador (NameGenerator) que implementa Consumer<FluxSink>
 * - El generador encapsula la lógica de emisión de elementos
 * - El Flux se crea pasando el generador: Flux.create(generator)
 * - La generación de elementos puede ocurrir desde cualquier lugar del código
 * 
 * Ventajas de esta refactorización:
 * - Separación de responsabilidades: El generador maneja la lógica de negocio
 * - Reutilización: El mismo generador puede usarse en diferentes contextos
 * - Testabilidad: Puedes probar el generador independientemente
 * - Flexibilidad: Puedes generar elementos desde cualquier parte del código
 * 
 * Flujo demostrado:
 * 1. Se crea el generador (NameGenerator)
 * 2. Se crea el Flux pasando el generador
 * 3. Se suscribe al Flux (el generador se conecta al FluxSink)
 * 4. Desde otro lugar del código, se llama a generator.generate()
 * 5. El generador emite elementos a través del FluxSink
 * 
 * Diferencia con Lec01FluxCreate:
 * - Lec01: La lógica está inline dentro del Flux.create()
 * - Lec02: La lógica está encapsulada en un objeto separado (mejor diseño)
 * 
 * Cuándo usar este patrón:
 * - Cuando la lógica de generación es compleja
 * - Cuando necesitas reutilizar la lógica de generación
 * - Cuando la generación ocurre desde múltiples lugares
 * - Para mejorar la legibilidad y mantenibilidad del código
 * 
 * Este patrón es especialmente útil cuando:
 * - Tienes múltiples fuentes de datos que deben emitir al mismo Flux
 * - La generación de datos ocurre de forma asíncrona o desde eventos
 * - Necesitas compartir el mismo generador entre diferentes partes de la aplicación
 */
public class Lec02FluxCreateRefactor {

    public static void main(String[] args) {

        var generator = new NameGenerator();
        var flux = Flux.create(generator);
        flux.subscribe(Util.subscriber());

        // somewhere else!
        for (int i = 0; i < 10; i++) {
            generator.generate();
        }

    }

}
