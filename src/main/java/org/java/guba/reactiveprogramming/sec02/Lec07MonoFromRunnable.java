package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Lec07: Creación de Mono con Mono.fromRunnable()
 * 
 * Mono.fromRunnable() ejecuta un Runnable y luego emite Mono.empty().
 * 
 * Características:
 * - El Runnable se ejecuta cuando hay una suscripción (evaluación perezosa)
 * - SIEMPRE emite Mono.empty() después de ejecutar el Runnable
 * - Útil para operaciones que no retornan valor pero necesitan ejecutarse de forma reactiva
 * - Si el Runnable lanza una excepción, se emite Mono.error()
 * 
 * Casos de uso:
 * - Operaciones de logging o notificaciones
 * - Limpieza de recursos
 * - Operaciones de efectos secundarios que no retornan datos
 * 
 * Diferencia con fromSupplier/fromCallable:
 * - fromSupplier/fromCallable: Retornan un valor que se emite
 * - fromRunnable: NO retorna valor, siempre emite Mono.empty()
 * 
 * Diferencia con Mono.empty():
 * - Mono.empty(): Emite inmediatamente sin ejecutar código
 * - Mono.fromRunnable(): Ejecuta código primero y luego emite Mono.empty()
 */
public class Lec07MonoFromRunnable {

    private static final Logger log = LoggerFactory.getLogger(Lec07MonoFromRunnable.class);

    public static void main(String[] args) {

        getProductName(2)
                .subscribe(Util.subscriber());

    }

    private static Mono<String> getProductName(int productId){
        if(productId == 1){
            return Mono.fromSupplier(() -> Util.faker().commerce().productName());
        }
        return Mono.fromRunnable(() -> notifyBusiness(productId));
    }

    private static void notifyBusiness(int productId){
        log.info("notifying business on unavailable product {}", productId);
    }


}
