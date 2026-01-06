package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Lec08: Conversión de CompletableFuture a Mono con Mono.fromFuture()
 * 
 * Mono.fromFuture() convierte un CompletableFuture existente en un Mono.
 * 
 * Características:
 * - Convierte código asíncrono tradicional (CompletableFuture) a programación reactiva
 * - El CompletableFuture puede estar ya en ejecución o crearse perezosamente
 * - Si el Future completa exitosamente, se emite el valor
 * - Si el Future falla, se emite Mono.error()
 * - Útil para integrar código legacy o librerías que usan CompletableFuture
 * 
 * Casos de uso:
 * - Integración con APIs que retornan CompletableFuture
 * - Conversión de código asíncrono tradicional a reactivo
 * - Operaciones que ya están ejecutándose en otro hilo
 * 
 * Diferencia con fromSupplier/fromCallable:
 * - fromSupplier/fromCallable: Ejecutan código síncrono de forma perezosa
 * - fromFuture: Convierte código asíncrono que ya está ejecutándose o puede ejecutarse
 * 
 * Nota: El CompletableFuture puede estar ejecutándose en un hilo separado,
 * lo que permite operaciones no bloqueantes.
 */
public class Lec08MonoFromFuture {

    private static final Logger log = LoggerFactory.getLogger(Lec08MonoFromFuture.class);

    public static void main(String[] args) {

        Mono.fromFuture(Lec08MonoFromFuture::getName)
                .subscribe(Util.subscriber());

        Util.sleepSeconds(1);
    }

    private static CompletableFuture<String> getName(){
        return CompletableFuture.supplyAsync(() -> {
            log.info("generating name");
            return Util.faker().name().firstName();
        });
    }

}
