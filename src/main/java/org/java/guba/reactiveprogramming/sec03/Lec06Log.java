package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Lec06: Operador log() para Debugging
 * 
 * Esta clase demuestra el uso del operador log() para depurar y entender
 * el flujo de datos en un pipeline reactivo.
 * 
 * Características del operador log():
 * - Registra todos los eventos del ciclo de vida reactivo (onSubscribe, onNext, onComplete, onError)
 * - Muestra información detallada sobre cada elemento que pasa por el pipeline
 * - Útil para debugging y entender el comportamiento del flujo
 * - Puede recibir un nombre opcional para identificar diferentes puntos en el pipeline
 * 
 * Información que muestra:
 * - onSubscribe: Cuando se crea la suscripción
 * - onNext: Cada elemento emitido con su valor
 * - onComplete: Cuando el flujo completa
 * - onError: Si ocurre un error
 * - request: Solicitudes de datos del suscriptor
 * - cancel: Si se cancela la suscripción
 * 
 * Uso típico:
 * - Colocar log() en diferentes puntos del pipeline para ver cómo fluyen los datos
 * - Usar nombres descriptivos: .log("nombre-etapa")
 * - Útil para entender el orden de ejecución de operadores
 * 
 * Diferencia con System.out.println():
 * - log(): Muestra información completa del ciclo de vida reactivo
 * - println(): Solo muestra valores, no información de suscripción/request/etc.
 * 
 * Cuándo usar:
 * - Durante el desarrollo y debugging
 * - Para entender cómo funcionan los operadores
 * - Para diagnosticar problemas en pipelines complejos
 * - Para aprender programación reactiva
 * 
 * ⚠️ Nota: log() puede ser verboso en producción, considera usar niveles de log apropiados
 */
public class Lec06Log {

    public static void main(String[] args) {


        Flux.range(1, 5)
                .log()
                .map(i -> Util.faker().name().firstName())
                .log()
                .subscribe(Util.subscriber());


    }

}
