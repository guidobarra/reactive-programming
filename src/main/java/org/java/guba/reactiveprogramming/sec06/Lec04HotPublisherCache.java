package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Lec04: Hot Publisher con Cache usando replay()
 * 
 * Esta clase demuestra cómo combinar un Hot Publisher con replay() para cachear
 * valores históricos, permitiendo que los suscriptores tardíos reciban datos anteriores.
 * 
 * PROBLEMA RESUELTO:
 * 
 * Con un Hot Publisher normal (share() o autoConnect(0)):
 * - Los suscriptores que se unen tarde se pierden los datos que ya pasaron
 * - Solo reciben datos nuevos desde el momento en que se suscriben
 * - No hay forma de ver el historial
 * 
 * Con replay():
 * - Los valores se cachean automáticamente
 * - Los suscriptores tardíos reciben los valores cacheados primero
 * - Luego continúan recibiendo valores nuevos en tiempo real
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo (precios de acciones):
 * 
 * 1. Se crea un flujo de precios con replay(1).autoConnect(0):
 *    - replay(1): Cachea el ÚLTIMO valor emitido
 *    - autoConnect(0): Comienza inmediatamente
 *    - Los precios se emiten cada 3 segundos
 * 
 * 2. Después de 4 segundos, Sam se suscribe:
 *    - Sam recibe el precio cacheado (el último precio emitido)
 *    - Luego continúa recibiendo precios nuevos en tiempo real
 * 
 * 3. Después de otros 4 segundos, Mike se suscribe:
 *    - Mike también recibe el precio cacheado (el precio actual)
 *    - Luego continúa recibiendo precios nuevos junto con Sam
 *    - Ambos ven el mismo precio actual y los mismos precios futuros
 * 
 * CARACTERÍSTICAS DE replay(n):
 * 
 * - replay(1): Cachea solo el último valor emitido
 * - replay(5): Cachea los últimos 5 valores emitidos
 * - replay(): Cachea TODOS los valores emitidos (puede consumir mucha memoria)
 * - replay(Duration): Cachea valores emitidos en el último período de tiempo
 * 
 * COMBINACIÓN: replay(n).autoConnect(0)
 * 
 * - replay(n): Cachea los últimos n valores
 * - autoConnect(0): Comienza inmediatamente y no se detiene
 * - Los suscriptores reciben primero los valores cacheados, luego los nuevos
 * 
 * VENTAJAS DE replay():
 * 
 * 1. Suscriptores tardíos no se pierden datos:
 *    - Reciben el estado actual inmediatamente
 *    - Luego continúan con datos en tiempo real
 *    - Útil para dashboards y aplicaciones de monitoreo
 * 
 * 2. Estado inicial consistente:
 *    - Todos los suscriptores ven el mismo estado inicial
 *    - Útil para sincronizar múltiples clientes
 *    - Reduce la latencia percibida
 * 
 * 3. Flexibilidad:
 *    - Puedes cachear solo el último valor (replay(1)) o varios
 *    - Puedes limitar por tiempo en lugar de cantidad
 *    - Balance entre memoria y utilidad
 * 
 * CASOS DE USO PARA replay():
 * 
 * 1. Precios de acciones en tiempo real:
 *    - Los nuevos clientes ven el precio actual inmediatamente
 *    - Luego reciben actualizaciones en tiempo real
 *    - Útil para aplicaciones de trading
 * 
 * 2. Dashboards de monitoreo:
 *    - Los usuarios que se conectan ven el estado actual
 *    - Luego ven actualizaciones en tiempo real
 *    - Mejora la experiencia del usuario
 * 
 * 3. Métricas del sistema:
 *    - Los nuevos suscriptores ven las métricas actuales
 *    - Luego continúan recibiendo actualizaciones
 *    - Útil para herramientas de observabilidad
 * 
 * 4. Estado de aplicaciones:
 *    - Los clientes que se conectan ven el estado actual
 *    - Luego sincronizan con cambios en tiempo real
 *    - Útil para aplicaciones colaborativas
 * 
 * CONSIDERACIONES DE MEMORIA:
 * 
 * - replay(1): Usa memoria mínima (solo el último valor)
 * - replay(n): Usa más memoria proporcional a n
 * - replay(): Puede usar memoria ilimitada si el flujo es infinito
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - replay() consume memoria para cachear valores
 * - Con flujos infinitos, replay() sin límite puede causar OutOfMemoryError
 * - Usa replay(1) o replay(n pequeño) cuando sea posible
 * - Considera replay(Duration) para limitar por tiempo en lugar de cantidad
 * 
 * DIFERENCIA CON COLD PUBLISHER:
 * 
 * - COLD: Cada suscriptor obtiene todos los datos desde el principio (pero se regeneran)
 * - HOT con replay(): Todos comparten el mismo flujo, pero los tardíos reciben cache
 * 
 * - COLD: Los datos se regeneran para cada suscriptor (más costoso)
 * - HOT con replay(): Los datos se generan una vez y se cachean (más eficiente)
 * 
 * CUÁNDO USAR replay():
 * 
 * - Cuando necesitas que los suscriptores tardíos vean el estado actual
 * - Para datos que cambian frecuentemente pero el último valor es importante
 * - Cuando quieres combinar eficiencia (Hot) con acceso histórico (Cold)
 * - Para aplicaciones donde el estado inicial es crítico
 * - Cuando la memoria no es una preocupación principal
 * 
 * Esta combinación (Hot Publisher + replay()) ofrece lo mejor de ambos mundos:
 * eficiencia de recursos (Hot) y acceso a datos históricos (Cold).
 */
public class Lec04HotPublisherCache {

    private static final Logger log = LoggerFactory.getLogger(Lec04HotPublisherCache.class);

    public static void main(String[] args) {

        var stockFlux = stockStream().replay(1).autoConnect(0);

        Util.sleepSeconds(4);

        log.info("sam joining");
        stockFlux
                .subscribe(Util.subscriber("sam"));

        Util.sleepSeconds(4);

        log.info("mike joining");
        stockFlux
                .subscribe(Util.subscriber("mike"));

        Util.sleepSeconds(15);

    }

    private static Flux<Integer> stockStream() {
        return Flux.generate(sink -> sink.next(Util.faker().random().nextInt(10, 100)))
                   .delayElements(Duration.ofSeconds(3))
                   .doOnNext(price -> log.info("emitting price: {}", price))
                   .cast(Integer.class);
    }

}
