package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec01: Creación de Flux con Flux.create() - Emisión Programática
 * 
 * Flux.create() permite crear un Flux y emitir elementos de forma programática
 * usando un FluxSink. Es útil cuando necesitas control total sobre cuándo y cómo
 * emitir elementos.
 * 
 * Características principales:
 * - Control programático completo sobre la emisión de elementos
 * - Puedes emitir múltiples elementos en una sola invocación
 * - Puedes emitir elementos desde cualquier lugar (callbacks, eventos, etc.)
 * - El FluxSink te permite emitir: next(), complete(), error()
 * 
 * Sintaxis: Flux.create(consumer -> { ... })
 * - El consumer recibe un FluxSink<T>
 * - Puedes llamar a fluxSink.next() para emitir elementos
 * - Debes llamar a fluxSink.complete() cuando termines
 * - Puedes llamar a fluxSink.error() si hay un problema
 * 
 * Diferencia con Flux.just():
 * - Flux.just(): Valores fijos conocidos al crear el Flux
 * - Flux.create(): Lógica programática que decide qué emitir y cuándo
 * 
 * Diferencia con Flux.generate():
 * - Flux.create(): Puede emitir múltiples elementos, puede ser llamado desde múltiples hilos
 * - Flux.generate(): Emite un elemento a la vez, invocado repetidamente según demanda
 * 
 * Casos de uso:
 * - Integración con APIs basadas en callbacks
 * - Emisión de elementos basada en eventos externos
 * - Conversión de código asíncrono tradicional a reactivo
 * - Cuando necesitas emitir elementos desde múltiples fuentes o hilos
 * 
 * ⚠️ Importante:
 * - Debes llamar a complete() o error() para finalizar el flujo
 * - Si no llamas a complete(), el Flux nunca completará
 * - Por defecto, Flux.create() NO respeta la demanda del downstream (ver Lec04)
 * 
 * Cuándo usar:
 * - Cuando necesitas control programático sobre la emisión
 * - Para integrar con APIs que usan callbacks
 * - Cuando los elementos provienen de eventos o fuentes externas
 * - Cuando necesitas emitir desde múltiples hilos (FluxSink es thread-safe)
 */
public class Lec01FluxCreate {

    public static void main(String[] args) {

        Flux.create(fluxSink -> {
                String country;
                do {
                    country = Util.faker().country().name();
                    fluxSink.next(country);
                } while (!country.equalsIgnoreCase("canada"));
                fluxSink.complete();
            })
            .subscribe(Util.subscriber());


    }

}
