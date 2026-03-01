package org.java.guba.reactiveprogramming.sec07;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec07.client.ExternalServiceClient;
import reactor.core.scheduler.Schedulers;

/**
 * Lec06: Problema del Event Loop y su Solución con publishOn()
 * 
 * Esta clase demuestra un problema crítico cuando se bloquea el Event Loop en aplicaciones
 * reactivas y cómo solucionarlo elegantemente usando publishOn() para descargar el trabajo
 * bloqueante a otro grupo de hilos.
 * 
 * EL PROBLEMA DEL EVENT LOOP:
 * 
 * En aplicaciones reactivas (especialmente con Netty/WebFlux):
 * - El Event Loop es un hilo especializado que maneja múltiples conexiones/operaciones I/O
 * - Su trabajo principal es manejar operaciones I/O no bloqueantes de manera eficiente
 * - Si bloqueas el Event Loop con operaciones bloqueantes, NO puede hacer su trabajo de I/O
 * - Esto afecta a TODAS las operaciones que dependen de ese Event Loop
 * 
 * COMPORTAMIENTO SIN SOLUCIÓN (Código actual - PROBLEMÁTICO):
 * 
 * En este ejemplo sin publishOn():
 * - Se hacen 5 llamadas a getProductName() (operación no bloqueante, retorna rápidamente)
 * - Cada llamada retorna en ~1 segundo (I/O no bloqueante funciona bien)
 * - PERO luego se procesa con process() que tiene sleepSeconds(1) - OPERACIÓN BLOQUEANTE
 * - El Event Loop viene y entrega el nombre del producto
 * - Luego el Event Loop es FORZADO a ejecutar process() que bloquea durante 1 segundo
 * - Resultado: Los productos se reciben SECUENCIALMENTE, uno cada segundo
 * - Tiempo total: ~5 segundos (uno por uno: producto 1 a los 47s, producto 2 a los 48s, etc.)
 * 
 * POR QUÉ ES UN PROBLEMA:
 * 
 * 1. El Event Loop está diseñado para I/O, no para procesamiento bloqueante:
 *    - Viene y entrega el nombre del producto (su trabajo de I/O)
 *    - Luego se ve forzado a hacer procesamiento bloqueante (NO es su trabajo)
 *    - No puede volver a hacer I/O mientras está bloqueado
 * 
 * 2. Procesamiento secuencial en lugar de paralelo:
 *    - Sin publishOn(): Los productos se procesan uno tras otro (secuencial)
 *    - Cada producto espera a que el anterior termine
 *    - El Event Loop está ocupado procesando en lugar de haciendo I/O
 * 
 * 3. Mal uso del hilo del Event Loop:
 *    - El desarrollador del cliente externo espera que su hilo se use para I/O
 *    - Los consumidores están haciendo mal uso del hilo con procesamiento bloqueante
 *    - Esto degrada el rendimiento de todo el sistema
 * 
 * LA SOLUCIÓN ELEGANTE: publishOn(Schedulers.boundedElastic())
 * 
 * Agregar publishOn() ANTES del map() que contiene el procesamiento bloqueante:
 * ```java
 * client.getProductName(i)
 *       .publishOn(Schedulers.boundedElastic())  // Descarga el trabajo aquí
 *       .map(Lec06EventLoopIssueFix::process)     // Procesa en boundedElastic
 * ```
 * 
 * CÓMO FUNCIONA LA SOLUCIÓN:
 * 
 * 1. El Event Loop hace su trabajo de I/O:
 *    - Recibe el nombre del producto (operación no bloqueante)
 *    - Entrega el dato al siguiente operador (publishOn)
 *    - Inmediatamente vuelve a hacer I/O para otras operaciones
 * 
 * 2. publishOn() descarga el trabajo:
 *    - Toma el dato del Event Loop
 *    - Lo pasa a un hilo de boundedElastic para procesamiento
 *    - El Event Loop queda libre inmediatamente
 * 
 * 3. El procesamiento bloqueante se ejecuta en boundedElastic:
 *    - process() se ejecuta en un hilo del pool boundedElastic
 *    - Múltiples productos se procesan en paralelo (diferentes hilos)
 *    - El Event Loop nunca se bloquea
 * 
 * RESULTADO DESPUÉS DE LA SOLUCIÓN:
 * 
 * - Todos los productos se reciben aproximadamente al mismo tiempo (~1 segundo total)
 * - El Event Loop solo se enfoca en I/O (su trabajo principal)
 * - El procesamiento bloqueante se ejecuta en paralelo en boundedElastic
 * - Mejor rendimiento y escalabilidad
 * 
 * COMPARACIÓN:
 * 
 * SIN publishOn() (PROBLEMÁTICO):
 * - Producto 1 recibido a los 47s, procesado a los 48s
 * - Producto 2 recibido a los 48s, procesado a los 49s
 * - Producto 3 recibido a los 49s, procesado a los 50s
 * - Tiempo total: ~5 segundos (secuencial)
 * - Event Loop bloqueado durante todo el procesamiento
 * 
 * CON publishOn() (SOLUCIONADO):
 * - Todos los productos recibidos ~1 segundo después de iniciar
 * - Todos procesados en paralelo en boundedElastic
 * - Tiempo total: ~1 segundo (paralelo)
 * - Event Loop libre para hacer más I/O
 * 
 * VENTAJAS DE LA SOLUCIÓN:
 * 
 * 1. Event Loop libre para I/O:
 *    - El Event Loop solo hace su trabajo de I/O
 *    - Puede manejar muchas más operaciones simultáneamente
 *    - No se bloquea con procesamiento
 * 
 * 2. Procesamiento paralelo:
 *    - Múltiples productos se procesan simultáneamente
 *    - Cada uno en su propio hilo de boundedElastic
 *    - Mejor utilización de recursos
 * 
 * 3. Separación de responsabilidades:
 *    - Event Loop: I/O no bloqueante (su especialidad)
 *    - boundedElastic: Procesamiento bloqueante (su propósito)
 *    - Cada hilo hace lo que mejor sabe hacer
 * 
 * 4. Solución elegante y simple:
 *    - Solo necesitas agregar publishOn() en el lugar correcto
 *    - Reactor maneja automáticamente el cambio de hilo
 *    - No necesitas manejar hilos manualmente
 * 
 * PERSPECTIVA DEL DESARROLLADOR:
 * 
 * Si eres el desarrollador del ExternalServiceClient:
 * - Sabes que los consumidores podrían hacer mal uso de tu hilo del Event Loop
 * - Puedes proteger tu Event Loop agregando publishOn() antes de exponer el método
 * - Esto asegura que tu Event Loop siempre esté disponible para I/O
 * 
 * Si eres el consumidor:
 * - Debes ser consciente de no bloquear el Event Loop
 * - Usa publishOn() cuando necesites hacer procesamiento bloqueante
 * - Respeta el propósito del Event Loop (I/O, no procesamiento)
 * 
 * MEJORES PRÁCTICAS:
 * 
 * 1. Identifica operaciones bloqueantes:
 *    - Thread.sleep(), operaciones de I/O bloqueantes
 *    - Cálculos que toman tiempo significativo
 *    - Cualquier operación que pueda bloquear el hilo
 * 
 * 2. Usa publishOn() después de I/O no bloqueante:
 *    - Mantén el Event Loop libre para I/O
 *    - Descarga el procesamiento bloqueante a boundedElastic
 *    - Coloca publishOn() ANTES del operador que hace el trabajo bloqueante
 * 
 * 3. Elige el Scheduler apropiado:
 *    - boundedElastic: Para I/O bloqueante y operaciones que esperan
 *    - parallel: Para operaciones CPU-bound
 *    - single: Para operaciones que deben ejecutarse en un solo hilo
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - NUNCA bloquees el Event Loop con operaciones bloqueantes
 * - El Event Loop debe estar libre para hacer I/O
 * - Usa publishOn() para descargar trabajo bloqueante
 * - Esta es una de las prácticas más importantes en programación reactiva
 * 
 * Esta clase demuestra elegantemente cómo Reactor resuelve el problema del Event Loop
 * bloqueado con una simple línea de código (publishOn()), permitiendo que cada hilo
 * haga lo que mejor sabe hacer: Event Loop para I/O, boundedElastic para procesamiento bloqueante.
 */
/*
    Ensure that the external service is up and running!
 */
public class Lec06EventLoopIssueFix {

    public static void main(String[] args) {

        var client = new ExternalServiceClient();

        for (int i = 1; i <= 5; i++) {
            // PROBLEMA: Sin publishOn(), el Event Loop se bloquea con process()
            // Resultado: Los productos se reciben secuencialmente (uno cada segundo, total ~5 segundos)
            //client.getProductName(i)
            //      .map(Lec06EventLoopIssueFix::process)
            //      .subscribe(Util.subscriber());
            
            // SOLUCIÓN: Agregar publishOn() antes del map() para descargar el procesamiento bloqueante
            // Descomenta la siguiente línea para ver la solución:
            client.getProductName(i)
                  .publishOn(Schedulers.boundedElastic())  // Descarga el trabajo al Event Loop
                  .map(Lec06EventLoopIssueFix::process)     // Procesa en boundedElastic
                  .subscribe(Util.subscriber());
            //Resultado: Todos los productos se reciben ~1 segundo (procesamiento paralelo)
        }

        Util.sleepSeconds(20);

    }

    private static String process(String input){
        Util.sleepSeconds(1);
        return input + "-processed";
    }

}
