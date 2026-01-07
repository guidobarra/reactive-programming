package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.sec01.subscriber.SubscriberImpl;
import org.java.guba.reactiveprogramming.sec03.helper.NameGenerator;

/**
 * Lec07: Comparación entre Flux y List - Evaluación Perezosa y Control de Flujo
 * 
 * Esta clase demuestra las diferencias fundamentales entre trabajar con List
 * (colección tradicional) y Flux (flujo reactivo).
 * 
 * Diferencias clave:
 * 
 * 1. List (Colección Tradicional):
 *    - Todos los elementos se generan INMEDIATAMENTE
 *    - Se almacenan TODOS en memoria
 *    - No hay control sobre cuántos elementos procesar
 *    - Debes procesar todos o crear una nueva lista filtrada
 * 
 * 2. Flux (Flujo Reactivo):
 *    - Los elementos se generan ON-DEMAND (lazy)
 *    - Solo se generan los elementos solicitados
 *    - Control total mediante request() y cancel()
 *    - Puedes cancelar en cualquier momento sin generar el resto
 * 
 * Ventajas de Flux:
 * - Eficiencia en memoria: Solo genera lo que necesitas
 * - Control de flujo: Puedes solicitar elementos bajo demanda
 * - Cancelación: Puedes detener la generación cuando ya no necesitas más
 * - Evaluación perezosa: No genera datos hasta que se solicitan
 * 
 * Ejemplo demostrado:
 * - Se solicita solo 3 elementos con request(3)
 * - Luego se cancela la suscripción
 * - Los otros 7 elementos nunca se generan (ahorro de recursos)
 * 
 * Cuándo usar List:
 * - Cuando necesitas todos los elementos inmediatamente
 * - Cuando el tamaño es pequeño y conocido
 * - Para operaciones que requieren acceso aleatorio
 * 
 * Cuándo usar Flux:
 * - Cuando el tamaño es grande o desconocido
 * - Cuando quieres procesar elementos bajo demanda
 * - Cuando necesitas cancelar el procesamiento
 * - Para operaciones de streaming o I/O
 * 
 * Diferencia fundamental: List es "pull all", Flux es "pull on-demand"
 */
public class Lec07FluxVsList {

    public static void main(String[] args) {

//        var list = NameGenerator.getNamesList(10);
//        System.out.println(list);

        var subscriber = new SubscriberImpl();
        NameGenerator.getNamesFlux(10)
                .subscribe(subscriber);

        subscriber.getSubscription().request(3);
        subscriber.getSubscription().cancel();
    }

}
