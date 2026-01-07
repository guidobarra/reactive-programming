package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Lec06: Creación de Flux con Flux.generate()
 * 
 * Flux.generate() es una forma más simple y controlada de crear un Flux
 * comparado con Flux.create(). Está diseñado para generar elementos uno a la vez
 * basándose en la demanda del downstream.
 * 
 * Características principales:
 * - Emite SOLO UN elemento por invocación del lambda
 * - Se invoca REPETIDAMENTE basándose en la demanda del downstream
 * - Respeta automáticamente el backpressure (a diferencia de Flux.create())
 * - Ejecuta en un solo hilo (no thread-safe por diseño)
 * - Más simple y seguro que Flux.create() para la mayoría de casos
 * 
 * Sintaxis: Flux.generate(sink -> { ... })
 * - El sink es un SynchronousSink<T>
 * - Debes llamar a sink.next() para emitir UN elemento
 * - Puedes llamar a sink.complete() para finalizar
 * - Puedes llamar a sink.error() para indicar un error
 * 
 * Comportamiento:
 * - El lambda se invoca cada vez que el downstream solicita un elemento
 * - Debes emitir exactamente UN elemento por invocación
 * - Si no emites nada, el flujo se detiene
 * - Si llamas a complete() o error(), el flujo termina
 * 
 * Diferencia con Flux.create():
 * - Flux.generate(): Emite un elemento a la vez, respeta demanda automáticamente
 * - Flux.create(): Puede emitir múltiples elementos, NO respeta demanda por defecto
 * - Flux.generate(): Más simple, single-threaded
 * - Flux.create(): Más flexible, thread-safe, requiere manejo manual de demanda
 * 
 * Diferencia con Flux.range():
 * - Flux.range(): Genera números consecutivos de forma optimizada
 * - Flux.generate(): Permite lógica personalizada para generar cualquier tipo de elemento
 * 
 * Cuándo usar Flux.generate():
 * - Cuando necesitas generar elementos uno a la vez
 * - Cuando la generación depende del estado anterior
 * - Cuando quieres respetar automáticamente el backpressure
 * - Para secuencias que pueden ser infinitas o muy largas
 * - Cuando no necesitas acceso multi-hilo
 * 
 * Cuándo NO usar Flux.generate():
 * - Cuando necesitas emitir múltiples elementos en una sola invocación
 * - Cuando necesitas acceso desde múltiples hilos
 * - Cuando la generación es completamente independiente entre elementos
 * 
 * Ventajas sobre Flux.create():
 * - Más simple y fácil de entender
 * - Respeta automáticamente el backpressure
 * - Menos propenso a errores
 * - Mejor para la mayoría de casos de uso
 * 
 * Este operador es ideal para generar secuencias donde cada elemento puede
 * depender del anterior o requiere lógica de generación personalizada.
 */
public class Lec06FluxGenerate {

    private static final Logger log = LoggerFactory.getLogger(Lec06FluxGenerate.class);

    public static void main(String[] args) {


        Flux.generate(synchronousSink -> {
                log.info("invoked");
                synchronousSink.next(1);
                // synchronousSink.next(2);
                //synchronousSink.complete();
                synchronousSink.error(new RuntimeException("oops"));
            })
            .take(4)
            .subscribe(Util.subscriber());


    }

}
