package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Mono;

/**
 * Lec04: Emisión de Mono Vacío y Errores
 * 
 * Esta clase demuestra cómo manejar diferentes escenarios en un Mono:
 * 
 * 1. Mono.just(value): Emite un valor exitosamente
 * 2. Mono.empty(): Emite onComplete() sin valores (equivalente a null en programación reactiva)
 * 3. Mono.error(Throwable): Emite onError() con una excepción
 * 
 * Casos de uso:
 * - Mono.empty(): Cuando no hay datos disponibles pero no es un error
 * - Mono.error(): Cuando ocurre una condición de error que debe propagarse
 * 
 * Diferencia clave: Mono.empty() completa exitosamente sin datos, mientras que
 * Mono.error() indica una condición de error que debe ser manejada.
 */
public class Lec04MonoEmptyError {

    public static void main(String[] args) {

        getUsername(3)
                .subscribe(Util.subscriber());

    }

    private static Mono<String> getUsername(int userId){
        return switch (userId){
            case 1 -> Mono.just("sam");
            case 2 -> Mono.empty(); // null
            default -> Mono.error(new RuntimeException("invalid input"));
        };
    }

}
