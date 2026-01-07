package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec04.helper.NameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

/**
 * Lec03: Thread Safety de FluxSink
 * 
 * Esta clase demuestra que FluxSink es THREAD-SAFE (seguro para uso concurrente),
 * lo que significa que múltiples hilos pueden llamar a fluxSink.next() de forma
 * segura sin causar condiciones de carrera o corrupción de datos.
 * 
 * ⚠️ IMPORTANTE: Esta es solo una demostración técnica. NO significa que debas
 * escribir código así en producción. El diseño correcto generalmente evita
 * la necesidad de acceso concurrente al FluxSink.
 * 
 * Demostración:
 * 
 * demo1() - ArrayList NO es thread-safe:
 * - Múltiples hilos escriben a un ArrayList sin sincronización
 * - Resultado: Puede perder elementos o causar excepciones
 * - ArrayList no está diseñado para acceso concurrente
 * 
 * demo2() - FluxSink SÍ es thread-safe:
 * - Múltiples hilos llaman a generator.generate() que usa FluxSink
 * - Resultado: Todos los elementos se emiten correctamente sin pérdida
 * - FluxSink maneja la sincronización internamente
 * 
 * Características de FluxSink thread-safety:
 * - Puedes llamar a next() desde múltiples hilos simultáneamente
 * - Los elementos se emiten en orden seguro
 * - No hay condiciones de carrera
 * - Internamente maneja la sincronización
 * 
 * Por qué es importante:
 * - Permite integrar Flux.create() con APIs asíncronas multi-hilo
 * - Útil cuando eventos llegan desde múltiples hilos
 * - Facilita la integración con sistemas externos concurrentes
 * 
 * Cuándo necesitas thread-safety:
 * - Cuando integras con APIs que usan múltiples hilos
 * - Cuando eventos llegan desde diferentes fuentes concurrentes
 * - Cuando usas callbacks que pueden ejecutarse en diferentes hilos
 * 
 * ⚠️ Mejores prácticas:
 * - Prefiere diseño single-threaded cuando sea posible
 * - Usa Flux.create() con thread-safety solo cuando sea necesario
 * - Considera usar Flux.generate() si no necesitas acceso multi-hilo
 * - Documenta claramente cuando usas acceso concurrente
 * 
 * Diferencia con Flux.generate():
 * - Flux.create(): Thread-safe, puede emitir desde múltiples hilos
 * - Flux.generate(): No thread-safe por diseño, ejecuta en un solo hilo
 * 
 * Esta demostración muestra que FluxSink maneja correctamente la concurrencia,
 * pero el código de producción debería evitar acceso concurrente innecesario.
 */
public class Lec03FluxSinkThreadSafety {

    private static final Logger log = LoggerFactory.getLogger(Lec03FluxSinkThreadSafety.class);

    public static void main(String[] args) {

        demo2();

    }

    // arraylist is not thread safe
    private static void demo1(){
        var list = new ArrayList<Integer>();
        Runnable runnable = () -> {
            for (int i = 0; i < 1000; i++) {
                list.add(i);
            }
        };
        for (int i = 0; i < 10; i++) {
            Thread.ofPlatform().start(runnable);
        }
        Util.sleepSeconds(3);
        log.info("list size: {}", list.size());
    }

    // arraylist is not thread safe.
    // flux sink is thread safe. we get all the 10000 items safely into array list
    private static void demo2(){
        var list = new ArrayList<String>();
        var generator = new NameGenerator();
        var flux = Flux.create(generator);
        flux.subscribe(list::add);

        Runnable runnable = () -> {
            for (int i = 0; i < 1000; i++) {
                generator.generate();
            }
        };
        for (int i = 0; i < 10; i++) {
            Thread.ofPlatform().start(runnable);
        }
        Util.sleepSeconds(3);
        log.info("list size: {}", list.size());
    }

}
