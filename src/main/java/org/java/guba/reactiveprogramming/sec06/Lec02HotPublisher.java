package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Lec02: Hot Publisher (Publisher Caliente) con share()
 * 
 * Esta clase demuestra el comportamiento de un HOT PUBLISHER usando el operador share().
 * Un Hot Publisher es como un canal de televisión en vivo o una sala de cine donde todos
 * los espectadores ven la misma película al mismo tiempo.
 * 
 * CARACTERÍSTICAS DE UN HOT PUBLISHER:
 * 
 * 1. Un solo productor de datos para todos los suscriptores:
 *    - El método de creación (movieStream()) se invoca SOLO UNA VEZ
 *    - Todos los suscriptores comparten el mismo flujo de datos
 *    - Los datos se emiten una sola vez y se distribuyen a todos los suscriptores
 * 
 * 2. Comportamiento "Cine/Sala de Teatro":
 *    - Todos ven la misma película al mismo tiempo
 *    - Si alguien se une tarde, se pierde las escenas que ya pasaron
 *    - La película sigue reproduciéndose independientemente de quién esté viendo
 * 
 * 3. share() = publish().refCount(1):
 *    - share() es un ALIAS (atajo) para publish().refCount(1)
 *    - ⚠️ IMPORTANTE: share() NO es un alias de publish().autoConnect(0)
 *    - share() mantiene una referencia activa mientras haya al menos un suscriptor
 *    - Necesita MÍNIMO 1 suscriptor para comenzar a emitir datos
 *    - Se detiene automáticamente cuando NO hay suscriptores (0 suscriptores)
 *    - Si hay una nueva suscripción después de detenerse, vuelve a comenzar desde el principio
 *    - Permite que todos los suscriptores compartan la misma fuente de datos, evitando la multiplicación de emisiones
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo (analogía del cine):
 * 
 * 1. Se crea el flujo de película usando share():
 *    - movieStream() actúa como el teatro/cine
 *    - Emite escenas de película cada segundo (scene 1, scene 2, etc.)
 * 
 * 2. Sam se suscribe después de 2 segundos:
 *    - La película comienza a reproducirse cuando Sam se suscribe
 *    - Sam ve desde la escena 1 (porque es el primer suscriptor)
 *    - Sam toma 4 escenas y luego se va (take(4))
 * 
 * 3. Mike se une 3 segundos después de Sam:
 *    - Mike se pierde las primeras escenas que ya pasaron
 *    - Mike ve desde donde está la película en ese momento (escena 3 o 4)
 *    - Ambos (Sam y Mike) ven la MISMA película al mismo tiempo
 *    - Mike toma 3 escenas y luego se va (take(3))
 * 
 * 4. Comportamiento importante:
 *    - El método movieStream() se invoca SOLO UNA VEZ (no por cada suscriptor)
 *    - "received the request" se imprime solo una vez
 *    - Si Sam se va, la película continúa para Mike
 *    - Si ambos se van, la película se detiene automáticamente (0 suscriptores)
 *    - Si alguien más se suscribe después, la película vuelve a comenzar desde el principio
 * 
 * VENTAJAS DE HOT PUBLISHER:
 * 
 * 1. Eficiencia de recursos:
 *    - Los datos se generan una sola vez, no por cada suscriptor
 *    - Menor uso de CPU y memoria
 *    - Ideal para datos costosos de generar
 * 
 * 2. Tiempo real:
 *    - Todos los suscriptores reciben datos en tiempo real
 *    - Útil para eventos en vivo, actualizaciones de precios, notificaciones
 *    - Simula comportamiento de broadcasting
 * 
 * 3. Compartimiento de estado:
 *    - Todos ven el mismo estado actual
 *    - Útil para dashboards, monitoreo en tiempo real
 *    - Ideal para datos que cambian constantemente
 * 
 * 4. Escalabilidad:
 *    - Puedes agregar muchos suscriptores sin duplicar la generación de datos
 *    - El costo de generación es constante independientemente del número de suscriptores
 * 
 * CASOS DE USO PARA HOT PUBLISHER:
 * 
 * 1. Actualizaciones de precios en tiempo real:
 *    - Todos los clientes ven el mismo precio actual
 *    - Si te unes tarde, ves el precio actual, no los históricos
 * 
 * 2. Notificaciones push:
 *    - Todos los clientes conectados reciben las mismas notificaciones
 *    - Las notificaciones se envían una vez y se distribuyen a todos
 * 
 * 3. Dashboards y monitoreo:
 *    - Múltiples usuarios ven las mismas métricas en tiempo real
 *    - Los datos se actualizan una vez y todos los dashboards se actualizan
 * 
 * 4. Eventos del sistema:
 *    - Logs, métricas, eventos que ocurren una vez
 *    - Múltiples consumidores procesan el mismo evento
 * 
 * 5. Streaming de datos:
 *    - Datos que fluyen continuamente (como un stream de video)
 *    - Todos los espectadores ven el mismo stream
 * 
 * COMPORTAMIENTO DE share():
 * 
 * - Mínimo de suscriptores: 1 (refCount(1))
 * - Se detiene cuando: No hay suscriptores (0 suscriptores)
 * - Re-suscripción: Si se detiene y alguien se suscribe de nuevo, comienza desde el principio
 * - Cancelación: Si un suscriptor cancela, no afecta a otros suscriptores
 * 
 * Para requerir mínimo 2 suscriptores:
 * - Usa: publish().refCount(2) en lugar de share()
 * - La película no comenzará hasta que haya al menos 2 suscriptores
 * 
 * DIFERENCIA CON COLD PUBLISHER:
 * 
 * COLD (Netflix):
 * - Cada suscriptor obtiene su propia película desde el inicio
 * - El método se invoca para cada suscriptor
 * - Si te unes tarde, ves desde el principio
 * 
 * HOT (Cine/Sala de Teatro):
 * - Todos ven la misma película al mismo tiempo
 * - El método se invoca solo una vez
 * - Si te unes tarde, te pierdes lo que ya pasó
 * 
 * CUÁNDO USAR HOT PUBLISHER:
 * 
 * - Cuando los datos son costosos de generar y quieres compartirlos
 * - Para eventos en tiempo real que ocurren una vez
 * - Cuando múltiples suscriptores necesitan ver el mismo estado actual
 * - Para datos que fluyen continuamente (streaming)
 * - Cuando la eficiencia de recursos es importante
 * - Para comportamientos tipo broadcasting o pub/sub
 * 
 * ⚠️ CONSIDERACIONES:
 * 
 * - Los suscriptores que se unen tarde pueden perderse datos
 * - Si necesitas que los suscriptores tardíos vean datos históricos, usa replay() (ver Lec04)
 * - La cancelación de un suscriptor no detiene el flujo para otros
 * - El flujo se detiene automáticamente cuando no hay suscriptores (puedes usar autoConnect(0) para evitar esto)
 */
public class Lec02HotPublisher {

    private static final Logger log = LoggerFactory.getLogger(Lec02HotPublisher.class);

    public static void main(String[] args) {

        var movieFlux = movieStream().share();

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
