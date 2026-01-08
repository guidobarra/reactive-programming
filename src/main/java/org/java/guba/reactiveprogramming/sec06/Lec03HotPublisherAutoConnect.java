package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Lec03: Hot Publisher con autoConnect() - Publisher Realmente Caliente
 * 
 * Esta clase demuestra cómo crear un Hot Publisher que NO se detiene cuando los
 * suscriptores cancelan, usando publish().autoConnect(0).
 * 
 * DIFERENCIA CLAVE CON share() / publish().refCount(1):
 * 
 * share() / publish().refCount(1):
 * - Se detiene automáticamente cuando NO hay suscriptores (0 suscriptores)
 * - Necesita al menos 1 suscriptor para comenzar
 * - Si todos los suscriptores se van, el flujo se detiene
 * - Si alguien se suscribe después, el flujo vuelve a comenzar desde el principio
 * 
 * publish().autoConnect(0):
 * - NO se detiene cuando los suscriptores cancelan
 * - Comienza a producir datos INMEDIATAMENTE, incluso con 0 suscriptores
 * - Una vez que comienza, continúa produciendo independientemente de los suscriptores
 * - Es un "Hot Publisher realmente caliente" - siempre está activo
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo:
 * - Se crea el flujo con publish().autoConnect(0)
 * - El flujo comienza INMEDIATAMENTE, incluso antes de que haya suscriptores
 * - Sam se suscribe después de 2 segundos (ya se perdió algunas escenas)
 * - Mike se une 3 segundos después de Sam
 * - Si Sam y Mike se van, el flujo CONTINÚA produciendo datos
 * - El flujo nunca se detiene automáticamente
 * 
 * CARACTERÍSTICAS DE autoConnect(n):
 * 
 * - autoConnect(0): Comienza inmediatamente, sin esperar suscriptores
 * - autoConnect(1): Comienza cuando hay al menos 1 suscriptor (similar a refCount(1))
 * - autoConnect(2): Comienza cuando hay al menos 2 suscriptores
 * - Una vez que comienza, NO se detiene automáticamente
 * 
 * VENTAJAS DE autoConnect(0):
 * 
 * 1. Flujo siempre activo:
 *    - Los datos siempre se están generando
 *    - Útil para datos que deben estar disponibles constantemente
 *    - No hay interrupciones por falta de suscriptores
 * 
 * 2. Pre-calentamiento:
 *    - Los datos pueden estar listos antes de que lleguen suscriptores
 *    - Reduce la latencia para nuevos suscriptores
 *    - Útil para mantener cachés calientes
 * 
 * 3. Comportamiento de broadcasting real:
 *    - Simula un canal de TV que siempre está transmitiendo
 *    - Los suscriptores se unen cuando quieren, pero la transmisión continúa
 * 
 * DESVENTAJAS DE autoConnect(0):
 * 
 * 1. Consumo de recursos:
 *    - Consume recursos incluso cuando no hay suscriptores
 *    - Puede ser ineficiente si los datos son costosos de generar
 *    - Los datos generados sin suscriptores se descartan
 * 
 * 2. No hay control de parada automática:
 *    - Debes detenerlo manualmente si es necesario
 *    - Puede continuar indefinidamente consumiendo recursos
 * 
 * CASOS DE USO PARA autoConnect(0):
 * 
 * 1. Servicios de monitoreo continuo:
 *    - Métricas del sistema que siempre deben estar disponibles
 *    - Health checks que se ejecutan constantemente
 * 
 * 2. Cachés que deben mantenerse calientes:
 *    - Datos que se actualizan constantemente
 *    - Pre-cálculo de datos para reducir latencia
 * 
 * 3. Canales de broadcasting:
 *    - Transmisiones que siempre están activas
 *    - Servicios que deben estar disponibles 24/7
 * 
 * 4. Eventos del sistema:
 *    - Logs del sistema que se generan continuamente
 *    - Eventos que ocurren independientemente de los consumidores
 * 
 * CUÁNDO USAR autoConnect(0) vs share() / refCount(1):
 * 
 * Usa autoConnect(0) cuando:
 * - Los datos deben estar siempre disponibles
 * - Quieres pre-calentar datos antes de que lleguen suscriptores
 * - El costo de mantener el flujo activo es aceptable
 * - Necesitas comportamiento de broadcasting real
 * 
 * Usa share() / refCount(1) cuando:
 * - Quieres ahorrar recursos cuando no hay suscriptores
 * - Los datos son costosos de generar
 * - Es aceptable que el flujo se detenga cuando no hay suscriptores
 * - Prefieres eficiencia sobre disponibilidad constante
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - autoConnect(0) hace que el Publisher sea "realmente caliente"
 * - Una vez que comienza, continúa indefinidamente
 * - Considera el impacto en recursos antes de usarlo
 * - Puede ser necesario detenerlo manualmente en algunos casos
 * - Úsalo cuando realmente necesites disponibilidad constante
 */
public class Lec03HotPublisherAutoConnect {

    private static final Logger log = LoggerFactory.getLogger(Lec03HotPublisherAutoConnect.class);

    public static void main(String[] args) {

        var movieFlux = movieStream().publish().autoConnect(0);

        Util.sleepSeconds(2);

        movieFlux
                .take(4)
                .subscribe(Util.subscriber("sam"));

        Util.sleepSeconds(3);

        movieFlux
                .take(3)
                .subscribe(Util.subscriber("mike"));


        Util.sleepSeconds(15);

    }

    // movie theater
    private static Flux<String> movieStream() {
        return Flux.generate(
                           () -> {
                               log.info("received the request");
                               return 1;
                           },
                           (state, sink) -> {
                               var scene = "movie scene " + state;
                               log.info("playing {}", scene);
                               sink.next(scene);
                               return ++state;
                           }
                   )
                   .take(10)
                   .delayElements(Duration.ofSeconds(1))
                   .cast(String.class);
    }

}
