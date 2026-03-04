package org.java.guba.reactiveprogramming.kafka;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ImplementationFluxKafka {

    private static final Logger LOG = LoggerFactory.getLogger(ImplementationFluxKafka.class);

    // Simula el parámetro max.poll.records de Kafka
    private static final int MAX_POLL_RECORDS = 300;

    public static void main(String[] args) {
        // Descomentar el demo a ejecutar (ejecutarlos en orden para ver la evolución):
        // demoSubscribeOnCausesRequestDeadlock();   // ❌ Problema: deadlock en request()
        // demoFixedWithDedicatedPollThread();        // ✅ Fix deadlock, pero consumer lento y secuencial
        demoFixedWithConcurrentConsumer();            // ✅ Fix consumer lento con flatMap concurrente

        Util.sleep(Duration.ofSeconds(100));
    }

    // ============================================================
    // ❌ DEMO 1 — PROBLEMA: subscribeOn bloquea la propagación de request()
    // ============================================================
    // CONTEXTO:
    //   El while loop dentro de Flux.create necesita correr en un thread propio
    //   para no bloquear el main. La solución obvia sería usar subscribeOn(),
    //   pero esto genera un deadlock silencioso.
    //
    // POR QUÉ FALLA:
    //   subscribeOn(Schedulers.single()) asigna el thread "single-1" al poll loop.
    //   Ese thread queda ocupado PARA SIEMPRE ejecutando el while(true).
    //
    //   Cuando publishOn consume 8 ítems y llama request(8), FluxSubscribeOn
    //   intercepta ese request e intenta encolarlo en "single-1":
    //
    //     FluxSubscribeOn.request(n) {
    //         if (currentThread != workerThread)
    //             worker.schedule(() -> s.request(n));  ← encola en single-1
    //     }
    //
    //   Pero "single-1" nunca vuelve a su event loop (está en el while).
    //   → El request(8) nunca se ejecuta.
    //   → requestedFromDownstream queda en 0 para siempre.
    //   → El sink duerme 10ms, despierta, ve requested=0, duerme otra vez... ∞
    //
    // LOGS ESPERADOS: solo un "MAX RECORD 10" y luego silencio eterno.
    // SIGUIENTE PASO: ver demoFixedWithDedicatedPollThread()
    private static void demoSubscribeOnCausesRequestDeadlock() {
        Flux
                .create(
                        sink -> {
                            while (!sink.isCancelled()) {
                                long requested = sink.requestedFromDownstream();
                                if (requested == 0) {
                                    Util.sleep(Duration.ofMillis(10));
                                    continue;
                                }

                                int maxRecords = (int) Math.min(requested, MAX_POLL_RECORDS);
                                LOG.info("MAX RECORD {} thread {}", maxRecords, Thread.currentThread().getName());
                                List<Object> records = simulateKafkaPoll(Duration.ofMillis(100), maxRecords);
                                records.forEach(sink::next);
                            }
                        },
                        FluxSink.OverflowStrategy.ERROR)
                // ❌ subscribeOn ocupa "single-1" con el while loop.
                //    Los request() de publishOn se encolan en single-1 pero nunca ejecutan.
                .subscribeOn(Schedulers.single())
                .publishOn(Schedulers.boundedElastic(), 10)
                .cast(Integer.class)
                .subscribe(
                        s -> LOG.info("record {}", s),
                        error -> LOG.error("Error", error),
                        () -> LOG.info("complete")
                );
    }

    // ============================================================
    // ✅ DEMO 2 — FIX: thread propio para el poll loop
    //            NUEVO PROBLEMA: consumer lento y secuencial
    // ============================================================
    // CÓMO SE RESUELVE EL PROBLEMA ANTERIOR:
    //   En lugar de subscribeOn, se inicia el while loop en un Thread propio
    //   dentro del callback de Flux.create. El callback termina inmediatamente
    //   (solo lanza el thread y retorna), sin ocupar ningún scheduler.
    //
    //   Cuando publishOn llama request(n), ya NO pasa por FluxSubscribeOn.
    //   El request va DIRECTO al AtomicLong interno de Flux.create:
    //
    //     FluxCreate.request(n) → AtomicLong.addAndGet(n)  ← atómico, sin scheduling
    //
    //   El thread "kafka-poll" lee ese AtomicLong y procesa continuamente. ✅
    //
    // NUEVO PROBLEMA QUE APARECE:
    //   El procesamiento usa map() con un sleep() bloqueante.
    //   map() ejecuta el bloqueo en el thread de publishOn (boundedElastic-1),
    //   lo que hace que los ítems se procesen uno por uno: 1 ítem/segundo.
    //   Si el consumer es lento y el producer es rápido, se acumula backpressure
    //   pero el throughput sigue siendo limitado a 1 ítem/segundo.
    //
    // LOGS ESPERADOS: "MAX RECORD 10" continuo, pero 1 record/segundo.
    // SIGUIENTE PASO: ver demoFixedWithConcurrentConsumer()
    private static void demoFixedWithDedicatedPollThread() {
        Flux
                .create(
                        sink -> {
                            // El callback termina rápido: solo lanza el thread y retorna.
                            Thread.ofPlatform().name("kafka-poll").start(() -> {
                                while (!sink.isCancelled()) {
                                    long requested = sink.requestedFromDownstream();
                                    // Control de backpressure: si nadie pide datos, esperamos sin quemar CPU
                                    if (requested == 0) {
                                        Util.sleep(Duration.ofMillis(10));
                                        continue;
                                    }

                                    int maxRecords = (int) Math.min(requested, MAX_POLL_RECORDS);
                                    LOG.info("MAX RECORD {} thread {}", maxRecords, Thread.currentThread().getName());
                                    List<Object> records = simulateKafkaPoll(Duration.ofMillis(100), maxRecords);
                                    records.forEach(sink::next);
                                }
                            });
                        },
                        FluxSink.OverflowStrategy.ERROR)
                // limitRate(n, n): highTide=n (primer request) y lowTide=n (refill).
                // Con ambos iguales no hay regla del 75%: siempre pide exactamente n al sink.
                // Alternativa: publishOn(scheduler, n) aplica la regla del 75%:
                //   primer lote = n, siguientes = n - n/4 (ej. 10 → 8)
                .limitRate(10, 10)
                .publishOn(Schedulers.boundedElastic())
                .cast(Integer.class)
                // ❌ map() es SECUENCIAL: el sleep bloquea boundedElastic-1.
                //    Nadie más puede procesar hasta que termine. Throughput: 1 ítem/segundo.
                .map(s -> {
                    Util.sleep(Duration.ofMillis(1000)); // Simula proceso lento (ej: llamada a DB o HTTP)
                    return s;
                })
                .subscribe(
                        s -> LOG.info("record {}", s),
                        error -> LOG.error("Error", error),
                        () -> LOG.info("complete")
                );
    }

    // ============================================================
    // ✅ DEMO 3 — FIX: consumer concurrente con flatMap + subscribeOn
    // ============================================================
    // CÓMO SE RESUELVE EL PROBLEMA ANTERIOR:
    //   Se reemplaza map() por flatMap() con Mono.fromCallable().subscribeOn().
    //   La diferencia clave:
    //
    //   map()     → el sleep bloquea el thread de publishOn (secuencial)
    //   flatMap() → el mapper retorna un Mono INMEDIATAMENTE (sin bloquear).
    //               Cada Mono corre en su PROPIO thread de boundedElastic.
    //               flatMap puede tener hasta maxConcurrency Monos activos a la vez.
    //
    //   Con maxConcurrency=250:
    //     → 250 ítems se procesan en paralelo, cada uno en su propio thread.
    //     → Throughput: 250 ítems/segundo en lugar de 1 ítem/segundo.
    //
    // POR QUÉ Mono.fromCallable() + subscribeOn() y no solo flatMap(Mono.just()):
    //   Mono.just(s) es eager: evalúa el valor inmediatamente en el thread actual.
    //   Mono.fromCallable(() -> ...) es lazy: el trabajo se ejecuta cuando se suscribe,
    //   y subscribeOn() le dice en qué thread ejecutarlo.
    //
    // LOGS ESPERADOS: múltiples threads (boundedElastic-1, 2, 3...) procesando en paralelo,
    //                 250 records por segundo.
    private static void demoFixedWithConcurrentConsumer() {
        Flux
                .create(
                        sink -> {
                            // El callback termina rápido: solo lanza el thread y retorna.
                            Thread.ofPlatform().name("kafka-poll").start(() -> {
                                while (!sink.isCancelled()) {
                                    long requested = sink.requestedFromDownstream();
                                    // Control de backpressure: si nadie pide datos, esperamos sin quemar CPU
                                    if (requested == 0) {
                                        Util.sleep(Duration.ofMillis(10));
                                        continue;
                                    }

                                    int maxRecords = (int) Math.min(requested, MAX_POLL_RECORDS);
                                    LOG.info("MAX RECORD {} thread {}", maxRecords, Thread.currentThread().getName());
                                    List<Object> records = simulateKafkaPoll(Duration.ofMillis(100), maxRecords);
                                    records.forEach(sink::next);
                                }
                            });
                        },
                        FluxSink.OverflowStrategy.ERROR)
                // limitRate(250, 250): el sink siempre recibe lotes de exactamente 250.
                .limitRate(250, 250)
                .publishOn(Schedulers.boundedElastic())
                .cast(Integer.class)
                // ✅ flatMap concurrente: el mapper retorna Mono inmediatamente (no bloquea publishOn).
                //    Cada ítem corre en su propio thread de boundedElastic vía subscribeOn().
                //    maxConcurrency=250: hasta 250 ítems procesándose en paralelo.
                .flatMap(
                        item -> Mono.fromCallable(() -> {
                            Util.sleep(Duration.ofMillis(1000)); // Simula proceso lento en su PROPIO thread
                            return item;
                        }).subscribeOn(Schedulers.boundedElastic()),
                        250  // maxConcurrency: cantidad de Monos activos simultáneamente
                )
                .subscribe(
                        s -> LOG.info("record {}", s),
                        error -> LOG.error("Error", error),
                        () -> LOG.info("complete")
                );
    }

    // Simula el comportamiento de KafkaConsumer.poll():
    //   - Genera hasta maxRecords registros
    //   - Si el valor es par, simula latencia de I/O (50% de las veces)
    private static List<Object> simulateKafkaPoll(Duration ioLatency, int maxRecords) {
        Integer value = Util.faker().random().nextInt(-1000, 1000);

        List<Object> records = new ArrayList<>();
        for (int i = 0; i < maxRecords; i++)
            records.add(value);

        if (value % 2 == 0)
            Util.sleep(ioLatency); // Simula latencia de red/disco (50% de las veces)

        return records;
    }
}
