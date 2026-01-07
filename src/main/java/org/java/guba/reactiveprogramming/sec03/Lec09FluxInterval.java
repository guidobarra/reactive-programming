package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Lec09: Creación de Flux con Flux.interval()
 * 
 * Flux.interval() crea un Flux que emite números largos secuenciales
 * (0, 1, 2, 3, ...) a intervalos regulares de tiempo.
 * 
 * Características:
 * - Emite números incrementales empezando desde 0
 * - Los números se emiten periódicamente según el intervalo especificado
 * - Se ejecuta en un Scheduler por defecto (no bloquea el hilo principal)
 * - Es INFINITO por defecto (nunca completa automáticamente)
 * - Debes cancelar explícitamente o usar operadores como take() para limitar
 * 
 * Sintaxis: Flux.interval(Duration period)
 * - period: Intervalo entre emisiones (ej: Duration.ofMillis(500))
 * 
 * Comportamiento:
 * - Emite 0 después del primer periodo
 * - Emite 1 después del segundo periodo
 * - Y así sucesivamente...
 * - Continúa indefinidamente hasta que se cancele
 * 
 * Diferencia con Flux.range():
 * - Flux.range(): Emite un número fijo de elementos inmediatamente
 * - Flux.interval(): Emite números infinitos con retraso entre cada uno
 * 
 * Diferencia con Thread.sleep():
 * - Thread.sleep(): Bloquea el hilo actual
 * - Flux.interval(): No bloquea, usa un Scheduler separado
 * 
 * Casos de uso:
 * - Temporizadores y cronómetros
 * - Polling periódico de datos
 * - Generación de eventos periódicos
 * - Heartbeats y keep-alive
 * - Simulación de datos en tiempo real
 * 
 * ⚠️ Importante:
 * - Siempre usa take() o algún límite para evitar emisiones infinitas
 * - O asegúrate de cancelar la suscripción cuando ya no la necesites
 * - El hilo principal puede terminar antes de que se emitan elementos (usa sleep)
 * 
 * Cuándo usar:
 * - Para operaciones que necesitan ejecutarse periódicamente
 * - Cuando necesitas generar eventos basados en tiempo
 * - Para simulaciones y pruebas que requieren datos temporales
 */
public class Lec09FluxInterval {

    public static void main(String[] args) {

        Flux.interval(Duration.ofMillis(500))
                .map(i -> Util.faker().name().firstName())
                .subscribe(Util.subscriber());

        Util.sleepSeconds(5);

    }

}
