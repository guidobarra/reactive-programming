package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Lec04: Creación de Flux desde Java Stream
 * 
 * Esta clase demuestra cómo convertir un Java Stream en un Flux reactivo,
 * con especial atención a la diferencia crítica entre pasar un Stream
 * directamente vs pasar un Supplier de Stream.
 * 
 * ⚠️ DIFERENCIA CRÍTICA:
 * 
 * 1. Flux.fromStream(list.stream()) - ❌ PROBLEMÁTICO:
 *    - Pasa el Stream directamente
 *    - Un Stream solo puede ser consumido UNA VEZ
 *    - Si intentas suscribirte múltiples veces, obtendrás IllegalStateException
 *    - El Stream se "consume" en la primera suscripción
 * 
 * 2. Flux.fromStream(list::stream) - ✅ CORRECTO:
 *    - Pasa un Supplier que crea un nuevo Stream cada vez
 *    - Cada suscripción obtiene su propio Stream fresco
 *    - Permite múltiples suscripciones sin problemas
 *    - El Supplier se ejecuta cada vez que hay una suscripción
 * 
 * Por qué es importante:
 * - Los Streams de Java son de un solo uso (single-use)
 * - Una vez consumido, no puede reutilizarse
 * - Flux puede tener múltiples suscriptores
 * - Necesitas un nuevo Stream para cada suscripción
 * 
 * Diferencia con Flux.fromIterable():
 * - Flux.fromIterable(): Crea un nuevo Iterator para cada suscripción (automático)
 * - Flux.fromStream(): Requiere un Supplier para crear un nuevo Stream (manual)
 * 
 * Cuándo usar:
 * - Cuando tienes código que usa Java Streams y quieres convertirlo a Flux
 * - Para integrar operaciones de Stream con programación reactiva
 * - Cuando necesitas procesar datos de forma lazy desde una fuente
 * 
 * ⚠️ SIEMPRE usa list::stream (Supplier) en lugar de list.stream() (Stream directo)
 */
public class Lec04FluxFromStream {

    public static void main(String[] args) {

        var list = List.of(1,2,3,4);
        var stream = list.stream();

//        stream.forEach(System.out::println);
//        stream.forEach(System.out::println);

        //OJO CON pasarle el stream
        //Flux.fromStream(list.stream());
        var flux = Flux.fromStream(list::stream);

        flux.subscribe(Util.subscriber("sub1"));
        flux.subscribe(Util.subscriber("sub2"));




    }

}
