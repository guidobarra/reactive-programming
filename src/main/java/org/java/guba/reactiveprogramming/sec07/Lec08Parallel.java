package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Lec08: Procesamiento Paralelo con parallel() y runOn()
 * 
 * Esta clase demuestra cómo usar parallel() y runOn() para procesar elementos de un
 * Flux en paralelo, dividiendo el flujo en múltiples "rails" (rieles) que se procesan
 * simultáneamente.
 * 
 * QUÉ HACE parallel() + runOn():
 * 
 * parallel():
 * - Divide el Flux en múltiples "rails" (sub-flujos paralelos)
 * - Cada rail procesa una porción de los elementos
 * - Permite procesamiento verdadero en paralelo
 * - Debes especificar el número de rails (típicamente igual al número de CPUs)
 * 
 * runOn():
 * - Especifica en qué Scheduler se ejecutan los rails paralelos
 * - Cada rail se ejecuta en un hilo diferente del Scheduler
 * - Típicamente usas Schedulers.parallel() para CPU-bound
 * 
 * sequential():
 * - Vuelve a combinar los rails paralelos en un solo flujo
 * - Necesario antes de operadores que requieren un flujo secuencial
 * - Opcional si solo necesitas procesar en paralelo sin orden
 * 
 * CARACTERÍSTICAS:
 * 
 * 1. Procesamiento verdadero en paralelo:
 *    - Múltiples elementos se procesan simultáneamente
 *    - Cada rail procesa elementos independientemente
 *    - Mejor utilización de múltiples CPUs
 * 
 * 2. División de trabajo:
 *    - Los elementos se distribuyen entre los rails
 *    - Cada rail procesa su porción de elementos
 *    - El orden puede no preservarse (depende de qué rail termina primero)
 * 
 * 3. Requiere runOn():
 *    - Debes especificar un Scheduler con runOn()
 *    - Típicamente Schedulers.parallel() para CPU-bound
 *    - Sin runOn(), los rails se ejecutan en el hilo actual (no hay paralelismo)
 * 
 * VENTAJAS:
 * 
 * 1. Mejor rendimiento para CPU-bound:
 *    - Aprovecha múltiples CPUs/cores
 *    - Procesa múltiples elementos simultáneamente
 *    - Reduce el tiempo total de procesamiento
 * 
 * 2. Escalabilidad:
 *    - Puedes ajustar el número de rails según tus CPUs
 *    - Mejor utilización de recursos del sistema
 *    - Ideal para operaciones que pueden paralelizarse
 * 
 * 3. Flexibilidad:
 *    - Puedes procesar en paralelo y luego combinar
 *    - Útil para operaciones independientes entre elementos
 * 
 * DESVENTAJAS Y CONSIDERACIONES:
 * 
 * 1. Overhead:
 *    - Hay overhead en dividir y combinar los rails
 *    - Para operaciones rápidas, el overhead puede ser mayor que el beneficio
 *    - No siempre mejora el rendimiento
 * 
 * 2. Complejidad:
 *    - Más complejo que procesamiento secuencial
 *    - Puede ser difícil de depurar
 *    - Requiere entender el paralelismo
 * 
 * 3. Orden no garantizado:
 *    - Los elementos pueden procesarse fuera de orden
 *    - Necesitas sequential() si el orden es importante
 *    - Puede causar problemas si el orden es crítico
 * 
 * CASOS DE USO:
 * 
 * 1. Operaciones CPU-intensivas:
 *    - Cálculos pesados que pueden paralelizarse
 *    - Transformaciones complejas de datos
 *    - Procesamiento de imágenes o video
 * 
 * 2. Operaciones independientes:
 *    - Cuando cada elemento se procesa independientemente
 *    - Operaciones que no dependen del orden
 *    - Transformaciones que no comparten estado
 * 
 * 3. Procesamiento batch:
 *    - Cuando tienes muchos elementos para procesar
 *    - Operaciones que toman tiempo y pueden paralelizarse
 * 
 * CUÁNDO NO USAR:
 * 
 * - Operaciones I/O bloqueantes (usa subscribeOn/publishOn en su lugar)
 * - Operaciones muy rápidas (el overhead no vale la pena)
 * - Cuando el orden es crítico y no puedes usar sequential()
 * - Para operaciones que ya son no bloqueantes y eficientes
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - ⚠️ A MENUDO NO NECESITAS ESTO
 * - Prefiere operaciones no bloqueantes para llamadas de red
 * - parallel() es principalmente para CPU-bound, no para I/O
 * - El overhead puede ser mayor que el beneficio para operaciones rápidas
 * - Usa parallel() solo cuando realmente necesitas procesamiento CPU paralelo
 * 
 * DIFERENCIA CON subscribeOn() / publishOn():
 * 
 * subscribeOn() / publishOn():
 * - Cambian el hilo pero procesan secuencialmente
 * - Útiles para I/O bloqueante
 * - Procesan un elemento a la vez en un hilo diferente
 * 
 * parallel() + runOn():
 * - Procesa múltiples elementos simultáneamente
 * - Útil para CPU-bound paralelizable
 * - Divide el trabajo en múltiples rails paralelos
 * 
 * EJEMPLO DEMOSTRADO:
 * 
 * En este código:
 * - Flux.range(1, 10) crea 10 elementos
 * - parallel(3) divide en 3 rails paralelos
 * - runOn(Schedulers.parallel()) ejecuta cada rail en un hilo paralelo
 * - process() se ejecuta en paralelo para múltiples elementos
 * - Los elementos se procesan simultáneamente en diferentes hilos
 * 
 * NOTA SOBRE sequential():
 * 
 * - En el código está comentado, pero normalmente lo necesitarías
 * - sequential() combina los rails de vuelta en un flujo secuencial
 * - Necesario si quieres preservar el orden o usar operadores que requieren secuencialidad
 * - Sin sequential(), el resultado puede estar desordenado
 * 
 * Esta clase demuestra procesamiento paralelo, pero recuerda: para la mayoría de casos,
 * especialmente con I/O, las operaciones no bloqueantes son preferibles sobre parallel().
 */
/*
    Often times you really do not need this!
    - prefer non-blocking IO for network calls
 */
public class Lec08Parallel {

    private static final Logger log = LoggerFactory.getLogger(Lec08Parallel.class);

    public static void main(String[] args) {

        Flux.range(1, 10)
                .parallel(3)
                .runOn(Schedulers.parallel())
                .map(Lec08Parallel::process)
             //   .sequential()
                .map(i -> i + "a")
                .subscribe(Util.subscriber());

        Util.sleepSeconds(30);
    }

    private static int process(int i){
        log.info("time consuming task {}", i);
        Util.sleepSeconds(1);
        return i * 2;
    }

}
