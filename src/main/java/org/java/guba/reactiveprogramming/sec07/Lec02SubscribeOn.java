package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec02: subscribeOn() - Cambiar el Hilo para Upstream
 * 
 * Esta clase demuestra el uso de subscribeOn() para cambiar el hilo donde se ejecuta
 * TODO el pipeline reactivo, desde la fuente (upstream) hasta el suscriptor.
 * 
 * QUÉ HACE subscribeOn():
 * 
 * subscribeOn() cambia el hilo donde se ejecuta TODO el pipeline, incluyendo:
 * - La creación y ejecución del Publisher (Flux.create, Flux.generate, etc.)
 * - Todos los operadores upstream (antes de subscribeOn)
 * - Todos los operadores downstream (después de subscribeOn)
 * - El Subscriber final
 * 
 * CARACTERÍSTICAS CLAVE:
 * 
 * 1. Afecta TODO el pipeline:
 *    - Desde la fuente hasta el suscriptor
 *    - Todos los operadores se ejecutan en el Scheduler especificado
 *    - Incluye doFirst() que está ANTES de subscribeOn()
 * 
 * 2. Solo el MÁS CERCANO a la fuente tiene efecto:
 *    - Si hay múltiples subscribeOn(), solo el primero (más cercano a la fuente) se aplica
 *    - Los subscribeOn() posteriores son ignorados
 *    - Esto se demuestra en Lec03MultipleSubscribeOn
 * 
 * 3. Ejecución asíncrona:
 *    - El subscribe() retorna inmediatamente
 *    - El trabajo se ejecuta en un hilo diferente del Scheduler
 *    - No bloquea el hilo que llama a subscribe()
 * 
 * SCHEDULERS COMUNES:
 * 
 * - Schedulers.boundedElastic(): Para operaciones I/O bloqueantes (BD, APIs, archivos)
 * - Schedulers.parallel(): Para operaciones CPU-bound paralelas
 * - Schedulers.single(): Un solo hilo dedicado
 * - Schedulers.immediate(): Ejecuta en el hilo actual (por defecto)
 * 
 * VENTAJAS DE subscribeOn():
 * 
 * 1. No bloquea el hilo principal:
 *    - Las operaciones bloqueantes se ejecutan en un hilo separado
 *    - El hilo principal puede continuar con otras tareas
 *    - Ideal para aplicaciones con UI o servidores
 * 
 * 2. Control del contexto de ejecución:
 *    - Puedes especificar dónde debe ejecutarse el trabajo
 *    - Útil para operaciones que requieren un contexto específico
 * 
 * 3. Múltiples suscriptores:
 *    - Cada suscripción puede ejecutarse en paralelo
 *    - Útil para procesar múltiples solicitudes simultáneamente
 * 
 * CASOS DE USO:
 * 
 * 1. Operaciones I/O bloqueantes:
 *    - Llamadas a base de datos bloqueantes
 *    - Llamadas a APIs REST bloqueantes
 *    - Lectura/escritura de archivos
 * 
 * 2. Operaciones CPU-intensivas:
 *    - Cálculos pesados que no deben bloquear el hilo principal
 *    - Procesamiento de datos complejo
 * 
 * 3. Aplicaciones con UI:
 *    - Para no bloquear el hilo de la UI
 *    - Mantener la interfaz responsiva
 * 
 * DIFERENCIA CON publishOn():
 * 
 * - subscribeOn(): Cambia el hilo para TODO el pipeline (upstream y downstream)
 * - publishOn(): Cambia el hilo solo para operadores DOWNSTREAM (después de publishOn)
 * 
 * - subscribeOn(): Solo el más cercano a la fuente tiene efecto
 * - publishOn(): Cada publishOn() cambia el hilo para los operadores siguientes
 * 
 * EJEMPLO DEMOSTRADO:
 * 
 * En este código:
 * - Flux.create() se ejecuta en boundedElastic
 * - doOnNext() se ejecuta en boundedElastic
 * - doFirst() (ambos) se ejecutan en boundedElastic
 * - El Subscriber se ejecuta en boundedElastic
 * - Todo se ejecuta en el mismo hilo del Scheduler
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - Usa subscribeOn() cuando quieres cambiar el hilo para TODO el pipeline
 * - Especialmente útil para operaciones bloqueantes en la fuente
 * - Solo el subscribeOn() más cercano a la fuente tiene efecto
 * - Prefiere publishOn() si solo necesitas cambiar el hilo para parte del pipeline
 */
public class Lec02SubscribeOn {

    private static final Logger log = LoggerFactory.getLogger(Lec02SubscribeOn.class);

    public static void main(String[] args) {

        var flux = Flux.create(sink -> {
                           for (int i = 1; i < 3; i++) {
                               log.info("generating: {}", i);
                               sink.next(i);
                           }
                           sink.complete();
                       })
                       .doOnNext(v -> log.info("value: {}", v))
                       .doFirst(() -> log.info("first1"))
                       .subscribeOn(Schedulers.boundedElastic())
                       .doFirst(() -> log.info("first2"));


        Runnable runnable1 = () -> flux.subscribe(Util.subscriber("sub1"));
        Runnable runnable2 = () -> flux.subscribe(Util.subscriber("sub2"));

        Thread.ofPlatform().name("th-virtual-A").start(runnable1);
        Thread.ofPlatform().name("th-virtual-B").start(runnable2);

        Util.sleepSeconds(2);

    }

}
