package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec04: Virtual Threads (Hilos Virtuales) en Reactor
 * 
 * Esta clase demuestra cómo habilitar el soporte de Virtual Threads (Project Loom) en Reactor
 * para mejorar el rendimiento y la escalabilidad de aplicaciones con muchas operaciones bloqueantes.
 * 
 * QUÉ SON VIRTUAL THREADS:
 * 
 * Virtual Threads son una característica de Java 19+ (Project Loom) que permite crear
 * millones de hilos ligeros sin el overhead de los hilos de plataforma tradicionales.
 * 
 * Características:
 * - Muy ligeros: Puedes crear millones sin problemas de memoria
 * - Manejados por la JVM: No mapean 1:1 con hilos del sistema operativo
 * - Ideales para I/O bloqueante: Perfectos para operaciones que esperan I/O
 * - Mejor escalabilidad: Puedes tener miles de operaciones concurrentes
 * 
 * CÓMO HABILITAR EN REACTOR:
 * 
 * Antes de usar Schedulers.boundedElastic(), configura:
 * ```java
 * System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
 * ```
 * 
 * Esto hace que boundedElastic() use Virtual Threads en lugar de hilos de plataforma.
 * 
 * VENTAJAS DE VIRTUAL THREADS:
 * 
 * 1. Escalabilidad masiva:
 *    - Puedes tener millones de operaciones concurrentes
 *    - No hay límite práctico como con hilos de plataforma
 *    - Ideal para aplicaciones con muchas operaciones I/O
 * 
 * 2. Mejor uso de recursos:
 *    - Menor consumo de memoria por hilo
 *    - Mejor aprovechamiento de CPU
 *    - Menos overhead de cambio de contexto
 * 
 * 3. Simplificación del código:
 *    - Puedes usar código bloqueante sin preocuparte por el número de hilos
 *    - No necesitas pools de hilos complejos
 *    - Más fácil de razonar y mantener
 * 
 * CASOS DE USO IDEALES:
 * 
 * 1. Aplicaciones con muchas operaciones I/O:
 *    - Servicios REST con muchas llamadas bloqueantes
 *    - Aplicaciones que hacen muchas consultas a BD
 *    - Procesamiento de archivos en paralelo
 * 
 * 2. Microservicios:
 *    - Servicios que manejan muchas solicitudes concurrentes
 *    - APIs que hacen múltiples llamadas a otros servicios
 *    - Procesamiento de eventos con I/O bloqueante
 * 
 * 3. Aplicaciones de alto rendimiento:
 *    - Cuando necesitas escalar a miles de operaciones concurrentes
 *    - Sistemas que procesan muchos datos con I/O bloqueante
 * 
 * REQUISITOS:
 * 
 * - Java 19 o superior (para Virtual Threads nativos)
 * - O Java 17+ con dependencias de Project Loom
 * - Reactor 3.5.0+ para soporte de Virtual Threads
 * 
 * DIFERENCIA CON HILOS DE PLATAFORMA:
 * 
 * Hilos de Plataforma (tradicionales):
 * - Limitados (típicamente cientos o miles)
 * - Alto overhead de memoria
 * - Mapean 1:1 con hilos del SO
 * - Cambio de contexto costoso
 * 
 * Virtual Threads:
 * - Prácticamente ilimitados (millones)
 * - Bajo overhead de memoria
 * - Manejados por la JVM
 * - Cambio de contexto muy eficiente
 * 
 * CUÁNDO USAR VIRTUAL THREADS:
 * 
 * - Cuando tienes muchas operaciones I/O bloqueantes concurrentes
 * - Para aplicaciones que necesitan escalar a miles de operaciones
 * - Cuando el código bloqueante es más simple que el asíncrono
 * - Para mejorar el rendimiento sin cambiar mucho código
 * 
 * CUÁNDO NO USAR:
 * 
 * - Para operaciones CPU-intensivas (usa parallel() en su lugar)
 * - Cuando ya tienes código completamente no bloqueante
 * - Si estás en Java < 19 sin Project Loom
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - Virtual Threads son ideales para I/O bloqueante, no para CPU-bound
 * - Debes habilitar la propiedad ANTES de usar boundedElastic()
 * - Verifica que tu versión de Java y Reactor soporten Virtual Threads
 * - Virtual Threads no reemplazan la necesidad de código no bloqueante, pero lo hacen más viable
 * 
 * Esta característica representa el futuro de la programación concurrente en Java, combinando
 * la simplicidad del código bloqueante con la escalabilidad de la programación asíncrona.
 */
/*
    reactor supports virtual threads
    System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
 */
public class Lec04VirtualThreads {

    private static final Logger log = LoggerFactory.getLogger(Lec04VirtualThreads.class);

    public static void main(String[] args) {

        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");

        var flux = Flux.create(sink -> {
                           for (int i = 1; i < 3; i++) {
                               log.info("generating: {}", i);
                               sink.next(i);
                           }
                           sink.complete();
                       })
                       .doOnNext(v -> log.info("value: {}", v))
                       .doFirst(() -> log.info("first1-{}", Thread.currentThread().isVirtual()))
                       .subscribeOn(Schedulers.boundedElastic())
                       .doFirst(() -> log.info("first2-{}", Thread.currentThread().isVirtual()));


        Runnable runnable1 = () -> flux.subscribe(Util.subscriber("sub1"));

        Thread.ofPlatform().name("th-platform").start(runnable1);

        Util.sleepSeconds(2);

    }

}
