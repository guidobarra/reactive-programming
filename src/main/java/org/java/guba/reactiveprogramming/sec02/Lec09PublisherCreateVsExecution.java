package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Lec09: Diferencia entre Creación y Ejecución del Publisher
 * 
 * Esta clase demuestra un concepto fundamental: crear un Publisher es una operación
 * LIVIANA (lightweight), mientras que ejecutar la lógica de negocio puede ser
 * COSTOSA y debe retrasarse.
 * 
 * Conceptos clave:
 * - Crear un Mono es rápido y no ejecuta código costoso
 * - La lógica costosa dentro de fromSupplier() NO se ejecuta hasta la suscripción
 * - Esto permite componer múltiples operaciones reactivas sin ejecutarlas inmediatamente
 * 
 * Flujo demostrado:
 * 1. Se llama a getName() -> se crea el Mono (rápido)
 * 2. Se llama a subscribe() -> AHORA se ejecuta el Supplier (puede ser lento)
 * 
 * Diferencia con Mono.just():
 * - Mono.just(): Ejecutaría la lógica costosa INMEDIATAMENTE al crear el Mono
 * - Mono.fromSupplier(): Retrasa la ejecución hasta la suscripción
 * 
 * Beneficio: Permite construir pipelines reactivos complejos sin ejecutar código
 * hasta que realmente se necesite, optimizando recursos y permitiendo cancelación.
 */
public class Lec09PublisherCreateVsExecution {

    private static final Logger log = LoggerFactory.getLogger(Lec09PublisherCreateVsExecution.class);

    public static void main(String[] args) {

        getName()
                .subscribe(Util.subscriber());

    }

    private static Mono<String> getName(){
        log.info("entered the method");
        return Mono.fromSupplier(() -> {
            log.info("generating name");
            Util.sleepSeconds(3);
            return Util.faker().name().firstName();
        });
    }

}
