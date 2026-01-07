package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lec11: Conversión entre Flux y Mono
 * 
 * Esta clase demuestra cómo convertir entre Flux y Mono usando el método from().
 * 
 * Conversiones demostradas:
 * 
 * 1. Mono → Flux (monoToFlux):
 *    - Flux.from(mono): Convierte un Mono en un Flux
 *    - El Flux emitirá el elemento del Mono (si existe) y luego completará
 *    - Si el Mono está vacío, el Flux también estará vacío
 *    - Si el Mono tiene error, el Flux también tendrá error
 * 
 * 2. Flux → Mono (fluxToMono):
 *    - Mono.from(flux): Convierte un Flux en un Mono
 *    - El Mono emitirá SOLO el PRIMER elemento del Flux
 *    - Ignora todos los elementos restantes del Flux
 *    - Útil cuando solo necesitas el primer elemento de un flujo
 * 
 * Características:
 * - from() es un método estático que acepta cualquier Publisher
 * - Funciona tanto con Flux como con Mono
 * - Mantiene el comportamiento reactivo (lazy, no bloqueante)
 * 
 * Diferencia con otros métodos:
 * - Mono.from(flux): Toma solo el primer elemento
 * - flux.next(): También toma solo el primer elemento (mismo resultado)
 * - flux.take(1).next(): Más explícito sobre tomar solo uno
 * 
 * Casos de uso Mono → Flux:
 * - Cuando tienes un Mono pero necesitas usar operadores de Flux
 * - Para componer con otros Flux en operaciones como merge() o concat()
 * - Cuando necesitas tratar un valor único como parte de un flujo
 * 
 * Casos de uso Flux → Mono:
 * - Cuando solo necesitas el primer elemento de un flujo
 * - Para obtener un valor único de una secuencia
 * - Cuando quieres convertir un flujo en un valor único
 * 
 * Cuándo usar:
 * - Mono → Flux: Cuando necesitas operadores de Flux en un valor único
 * - Flux → Mono: Cuando solo necesitas el primer elemento de un flujo
 * - Útil para integrar código que usa Mono con código que usa Flux
 */
public class Lec11FluxMono {

    public static void main(String[] args) {

        monoToFlux();
        fluxToMono();

    }

    private static void fluxToMono(){
        var flux = Flux.range(1, 10);
        Mono.from(flux)
                .subscribe(Util.subscriber());
    }

    private static void monoToFlux(){
        var mono = getUsername(1);
        save(Flux.from(mono));
    }

    private static Mono<String> getUsername(int userId){
        return switch (userId){
            case 1 -> Mono.just("sam");
            case 2 -> Mono.empty(); // null
            default -> Mono.error(new RuntimeException("invalid input"));
        };
    }

    private static void save(Flux<String> flux){
        flux.subscribe(Util.subscriber());
    }

}
