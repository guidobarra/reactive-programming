package org.java.guba.reactiveprogramming.sec05;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Lec03: Métodos Do (Do Hooks/Callbacks)
 * 
 * Esta clase demuestra los métodos "do" o callbacks que Reactor proporciona para
 * observar y reaccionar a diferentes eventos en el ciclo de vida de un flujo reactivo.
 * 
 * ⚠️ CARACTERÍSTICAS IMPORTANTES:
 * - Los métodos "do" NO mutan los valores emitidos
 * - Todos los valores que el productor emite pasan a través de estos métodos
 * - Se ejecutan en momentos específicos del ciclo de vida reactivo
 * - El ORDEN de ejecución es CRÍTICO y sigue el flujo del suscriptor al productor
 * 
 * MÉTODOS DO DISPONIBLES Y CUÁNDO SE INVOCAN:
 * 
 * 1. doFirst(Runnable):
 *    - Se invoca PRIMERO, antes de que cualquier cosa comience
 *    - Se ejecuta cuando el suscriptor se suscribe, ANTES del productor
 *    - Útil para inicialización o logging inicial
 *    - Se ejecuta de ABAJO hacia ARRIBA (desde el suscriptor hacia el productor)
 * 
 * 2. doOnSubscribe(Consumer<Subscription>):
 *    - Se invoca cuando el productor envía el objeto Subscription al downstream
 *    - Recibe el objeto Subscription como parámetro
 *    - Se ejecuta después de doFirst()
 * 
 * 3. doOnRequest(Consumer<Long>):
 *    - Se invoca cuando el downstream envía una solicitud al productor
 *    - Recibe el número de elementos solicitados (puede ser Long.MAX_VALUE para "todo")
 *    - Los operadores pueden modificar esta solicitud (ej: take() reduce la solicitud)
 *    - ⚠️ Si el suscriptor solicita 4 elementos pero hay un take(2), solo se solicitarán 2
 * 
 * 4. doOnNext(Consumer<T>):
 *    - Se invoca para CADA elemento emitido por el productor
 *    - Se ejecuta cada vez que llega un elemento
 *    - Útil para logging, debugging, o mutar objetos (aunque no muta el valor del flujo)
 *    - Se ejecuta tantas veces como elementos se emitan
 * 
 * 5. doOnComplete(Runnable):
 *    - Se invoca cuando el productor envía la señal onComplete()
 *    - Solo se ejecuta UNA VEZ cuando el flujo completa exitosamente
 *    - NO se ejecuta en caso de error o cancelación
 * 
 * 6. doOnError(Consumer<Throwable>):
 *    - Se invoca cuando el productor envía la señal onError()
 *    - Recibe el Throwable como parámetro
 *    - Solo se ejecuta cuando hay un error
 *    - NO se ejecuta en caso de completación exitosa o cancelación
 * 
 * 7. doOnTerminate(Runnable):
 *    - Se invoca TANTO en caso de completación exitosa COMO en caso de error
 *    - Es una combinación de doOnComplete() y doOnError()
 *    - Útil cuando necesitas ejecutar código en ambos casos
 *    - NO se ejecuta en caso de cancelación
 * 
 * 8. doOnCancel(Runnable):
 *    - Se invoca cuando el downstream envía una solicitud de cancelación
 *    - La cancelación va de ABAJO hacia ARRIBA (del suscriptor al productor)
 *    - Solo se ejecuta cuando hay cancelación explícita
 *    - NO se ejecuta en caso de completación o error
 * 
 * 9. doOnDiscard(Class<T>, Consumer<T>):
 *    - Se invoca cuando elementos son descartados (no recibidos por el suscriptor)
 *    - Ocurre cuando el productor emite más elementos de los que el suscriptor puede recibir
 *    - Por ejemplo: si hay cancelación pero el productor sigue emitiendo
 *    - Recibe el objeto descartado como parámetro
 * 
 * 10. doFinally(Consumer<SignalType>):
 *     - Se invoca SIEMPRE al final, independientemente de la razón
 *     - Se ejecuta después de complete, error, o cancel
 *     - Recibe un SignalType que indica la razón (ON_COMPLETE, ON_ERROR, CANCEL)
 *     - Es como un bloque "finally" en Java
 *     - Útil para limpieza de recursos
 * 
 * ⚠️ ORDEN DE EJECUCIÓN (MUY IMPORTANTE):
 * 
 * El orden de ejecución sigue el flujo del SUSCRIPTOR hacia el PRODUCTOR:
 * 
 * 1. doFirst() - Se ejecuta primero (de abajo hacia arriba)
 * 2. doOnSubscribe() - Cuando se pasa la Subscription
 * 3. doOnRequest() - Cuando se solicita datos
 * 4. [Productor comienza a emitir]
 * 5. doOnNext() - Para cada elemento emitido
 * 6. doOnComplete() / doOnError() - Cuando termina (según el caso)
 * 7. doOnTerminate() - Si es complete o error
 * 8. doOnCancel() - Si hay cancelación (va de abajo hacia arriba)
 * 9. doOnDiscard() - Para elementos descartados
 * 10. doFinally() - SIEMPRE al final, independientemente de la razón
 * 
 * FLUJO DE DIRECCIÓN:
 * - doFirst, doOnSubscribe, doOnRequest: De ABAJO hacia ARRIBA (subscriber → producer)
 * - doOnNext, doOnComplete, doOnError: De ARRIBA hacia ABAJO (producer → subscriber)
 * - doOnCancel: De ABAJO hacia ARRIBA (subscriber → producer)
 * - doFinally: Se ejecuta al final de todo
 * 
 * CASOS DE USO:
 * - Logging y debugging: Ver qué está pasando en cada etapa
 * - Métricas y monitoreo: Contar elementos, errores, cancelaciones
 * - Limpieza de recursos: Usar doFinally() para cerrar recursos
 * - Mutación de objetos: Usar doOnNext() para modificar objetos (aunque no muta el flujo)
 * - Validación: Verificar elementos antes de que lleguen al suscriptor
 * 
 * ⚠️ NOTA IMPORTANTE:
 * - Los métodos "do" son OPCIONALES - no tienes que usarlos todos
 * - No son necesarios en el día a día, pero son útiles para debugging y casos específicos
 * - Familiarizarse con ellos es importante para llevar las habilidades reactivas al siguiente nivel
 * - El orden de ejecución es crítico para entender cómo funciona el flujo reactivo
 * 
 * Esta clase demuestra múltiples métodos "do" duplicados (do-1 y do-2) para mostrar
 * cómo se ejecutan en orden y cómo interactúan entre sí en diferentes puntos del pipeline.
 */
public class Lec03DoCallbacks {

    private static final Logger log = LoggerFactory.getLogger(Lec03DoCallbacks.class);

    public static void main(String[] args) {

        Flux.<Integer>create(fluxSink -> {
                log.info("producer begins");
                for (int i = 0; i < 4; i++) {
                    fluxSink.next(i);
                }
                fluxSink.complete();
               // fluxSink.error(new RuntimeException("oops"));
                log.info("producer ends");
            })
            .doOnComplete(() -> log.info("doOnComplete-1"))
            .doFirst(() -> log.info("doFirst-1"))
            .doOnNext(item -> log.info("doOnNext-1: {}", item))
            .doOnSubscribe(subscription -> log.info("doOnSubscribe-1: {}", subscription))
            .doOnRequest(request -> log.info("doOnRequest-1: {}", request))
            .doOnError(error -> log.info("doOnError-1: {}", error.getMessage()))
            .doOnTerminate(() -> log.info("doOnTerminate-1")) // complete or error case
            .doOnCancel(() -> log.info("doOnCancel-1"))
            .doOnDiscard(Object.class, o -> log.info("doOnDiscard-1: {}", o))
            .doFinally(signal -> log.info("doFinally-1: {}", signal)) // finally irrespective of the reason
           // .take(2)
            .doOnComplete(() -> log.info("doOnComplete-2"))
            .doFirst(() -> log.info("doFirst-2"))
            .doOnNext(item -> log.info("doOnNext-2: {}", item))
            .doOnSubscribe(subscription -> log.info("doOnSubscribe-2: {}", subscription))
            .doOnRequest(request -> log.info("doOnRequest-2: {}", request))
            .doOnError(error -> log.info("doOnError-2: {}", error.getMessage()))
            .doOnTerminate(() -> log.info("doOnTerminate-2")) // complete or error case
            .doOnCancel(() -> log.info("doOnCancel-2"))
            .doOnDiscard(Object.class, o -> log.info("doOnDiscard-2: {}", o))
            .doFinally(signal -> log.info("doFinally-2: {}", signal)) // finally irrespective of the reason
            //.take(4)
            .subscribe(Util.subscriber("subscriber"));


    }

}
