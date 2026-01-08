package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec04.helper.NameGenerator;
import reactor.core.publisher.Flux;

/**
 * Lec05: Solución al Problema de Flux.create() con Múltiples Suscriptores
 * 
 * Esta clase demuestra cómo solucionar un problema común cuando se usa Flux.create()
 * con múltiples suscriptores. El problema ocurre cuando cada suscriptor crea su propio
 * flujo independiente (Cold Publisher), pero queremos que todos compartan el mismo flujo
 * (Hot Publisher).
 * 
 * PROBLEMA ORIGINAL (sec04/Lec02FluxCreateRefactor):
 * 
 * En la clase original:
 * - Se crea un NameGenerator
 * - Se crea un Flux con Flux.create(generator)
 * - Se suscribe un suscriptor
 * - Se llama a generator.generate() desde otro lugar
 * 
 * El problema:
 * - Si hay múltiples suscriptores, cada uno crea su propio flujo
 * - Cada suscriptor tiene su propia instancia del generador
 * - Los elementos generados pueden no distribuirse correctamente a todos los suscriptores
 * - Comportamiento inconsistente dependiendo del momento de suscripción
 * 
 * SOLUCIÓN: Usar share() para convertir a Hot Publisher
 * 
 * Al agregar .share() al Flux:
 * - El Flux se convierte en un Hot Publisher
 * - Todos los suscriptores comparten el mismo flujo
 * - Los elementos generados se distribuyen a TODOS los suscriptores
 * - Comportamiento consistente y predecible
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo:
 * - Se crea un NameGenerator
 * - Se crea un Flux con Flux.create(generator).share()
 * - Se suscriben dos suscriptores (sub1 y sub2)
 * - Se llama a generator.generate() 10 veces desde otro lugar
 * 
 * Resultado:
 * - Ambos suscriptores (sub1 y sub2) reciben los mismos nombres generados
 * - Los nombres se distribuyen a ambos suscriptores
 * - Comportamiento consistente y predecible
 * 
 * POR QUÉ share() SOLUCIONA EL PROBLEMA:
 * 
 * 1. Conversión a Hot Publisher:
 *    - share() convierte el Cold Publisher en Hot Publisher
 *    - Todos los suscriptores comparten el mismo flujo de datos
 *    - Los elementos se generan una vez y se distribuyen a todos
 * 
 * 2. Distribución consistente:
 *    - Los elementos generados se envían a todos los suscriptores activos
 *    - No hay duplicación de generación
 *    - Comportamiento tipo broadcasting
 * 
 * 3. Sincronización:
 *    - Todos los suscriptores ven los mismos elementos al mismo tiempo
 *    - Útil para notificaciones y eventos compartidos
 * 
 * CASOS DE USO:
 * 
 * 1. Generadores compartidos:
 *    - Cuando un generador debe distribuir elementos a múltiples consumidores
 *    - Para eventos que deben ser recibidos por múltiples suscriptores
 * 
 * 2. Notificaciones:
 *    - Cuando una acción debe notificar a múltiples componentes
 *    - Para sistemas de eventos donde múltiples listeners deben recibir el mismo evento
 * 
 * 3. Logging y monitoreo:
 *    - Cuando los mismos eventos deben ser procesados por múltiples sistemas
 *    - Para distribuir métricas a múltiples dashboards
 * 
 * 4. Procesamiento paralelo:
 *    - Cuando los mismos datos deben ser procesados por múltiples pipelines
 *    - Para análisis donde múltiples algoritmos procesan los mismos datos
 * 
 * DIFERENCIA CON LA VERSIÓN SIN share():
 * 
 * Sin share() (Cold Publisher):
 * - Cada suscriptor tiene su propio flujo
 * - Los elementos generados pueden no llegar a todos los suscriptores
 * - Comportamiento inconsistente
 * 
 * Con share() (Hot Publisher):
 * - Todos los suscriptores comparten el mismo flujo
 * - Los elementos generados se distribuyen a todos
 * - Comportamiento consistente y predecible
 * 
 * CUÁNDO USAR share() CON Flux.create():
 * 
 * - Cuando tienes un generador externo que debe distribuir a múltiples suscriptores
 * - Para eventos que deben ser recibidos por múltiples componentes
 * - Cuando quieres evitar duplicación de generación de datos
 * - Para comportamientos tipo pub/sub donde múltiples suscriptores necesitan los mismos datos
 * 
 * ⚠️ IMPORTANTE:
 * 
 * - share() convierte el Publisher en Hot, lo que puede cambiar el comportamiento
 * - Los suscriptores que se unen tarde pueden perderse datos
 * - Considera usar replay() si necesitas que los suscriptores tardíos reciban datos históricos
 * - share() se detiene cuando no hay suscriptores (usa autoConnect(0) si necesitas que continúe)
 * 
 * Esta solución es esencial cuando trabajas con Flux.create() y necesitas que múltiples
 * suscriptores compartan el mismo flujo de datos generado externamente.
 */
public class Lec05FluxCreateIssueFix {

    public static void main(String[] args) {

        var generator = new NameGenerator();
        var flux = Flux.create(generator).share();
        flux.subscribe(Util.subscriber("sub1"));
        flux.subscribe(Util.subscriber("sub2"));

        // somewhere else!
        for (int i = 0; i < 10; i++) {
            generator.generate();
        }

    }

}
