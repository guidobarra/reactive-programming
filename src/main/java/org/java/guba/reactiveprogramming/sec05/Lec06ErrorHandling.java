package org.java.guba.reactiveprogramming.sec05;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lec06: Manejo de Errores en Pipelines Reactivos
 * 
 * Esta clase demuestra las diferentes estrategias para manejar errores en un pipeline
 * reactivo. El manejo de errores es CRÍTICO en programación reactiva porque los errores
 * pueden propagarse rápidamente y terminar todo el flujo si no se manejan correctamente.
 * 
 * ⚠️ IMPORTANCIA DEL MANEJO DE ERRORES:
 * - En programación reactiva, un error sin manejar termina TODO el flujo
 * - Los errores se propagan hacia abajo en el pipeline
 * - Sin manejo adecuado, un solo error puede detener toda la aplicación
 * - El manejo de errores debe ser proactivo y estratégico
 * 
 * ESTRATEGIAS DE MANEJO DE ERRORES DEMOSTRADAS:
 * 
 * 1. onErrorContinue() - Continuar después del error:
 *    - Omite el elemento que causó el error y continúa con el siguiente
 *    - Útil cuando algunos elementos pueden fallar pero quieres procesar el resto
 *    - Recibe un BiConsumer<Throwable, Object> para logging o procesamiento del error
 *    - El flujo continúa normalmente después del error
 * 
 *    Cuándo usar:
 *    - Cuando procesas múltiples elementos y algunos pueden fallar
 *    - Para logging de errores sin detener el procesamiento
 *    - En operaciones batch donde quieres procesar todos los elementos posibles
 *    - Cuando el error en un elemento no afecta a los demás
 * 
 *    Ejemplo: Procesar una lista de archivos, algunos pueden no existir
 * 
 * 2. onErrorComplete() - Completar silenciosamente:
 *    - Cuando ocurre un error, simplemente completa el flujo sin emitir el error
 *    - El suscriptor recibe onComplete() en lugar de onError()
 *    - Útil cuando un error es esperado y no es crítico
 * 
 *    Cuándo usar:
 *    - Cuando un error es esperado y no necesita ser propagado
 *    - Para operaciones opcionales donde el error no es importante
 *    - Cuando quieres tratar errores como "no hay datos disponibles"
 *    - Para simplificar el manejo de errores en el suscriptor
 * 
 *    Ejemplo: Buscar un elemento opcional que puede no existir
 * 
 * 3. onErrorReturn() - Retornar un valor por defecto:
 *    - Cuando ocurre un error, emite un valor por defecto en su lugar
 *    - Puede ser específico por tipo de excepción o genérico
 *    - El flujo completa normalmente con el valor por defecto
 *    - Se evalúa en orden: primero los específicos, luego el genérico
 * 
 *    Variantes:
 *    - onErrorReturn(T fallbackValue): Para cualquier error
 *    - onErrorReturn(Class<E> type, T fallbackValue): Para un tipo específico de excepción
 * 
 *    Cuándo usar:
 *    - Cuando tienes un valor por defecto conocido para errores
 *    - Para operaciones donde un valor por defecto es aceptable
 *    - Cuando diferentes tipos de errores requieren diferentes valores por defecto
 *    - Para evitar que el error llegue al suscriptor
 * 
 *    Ejemplo: Si falla una consulta a BD, retornar una lista vacía o valor por defecto
 * 
 * 4. onErrorResume() - Usar un Publisher alternativo:
 *    - Cuando ocurre un error, cambia a otro Publisher (Mono/Flux) como fallback
 *    - Más flexible que onErrorReturn() porque permite lógica compleja
 *    - Puede retornar un Mono/Flux diferente basado en el tipo de error
 *    - Se evalúa en orden: primero los específicos, luego el genérico
 * 
 *    Variantes:
 *    - onErrorResume(Function<Throwable, Mono<T>> fallback): Para cualquier error
 *    - onErrorResume(Class<E> type, Function<E, Mono<T>> fallback): Para un tipo específico
 * 
 *    Cuándo usar:
 *    - Cuando necesitas lógica compleja para manejar el error
 *    - Para fallback a servicios alternativos
 *    - Cuando el fallback requiere operaciones asíncronas
 *    - Para retry con diferentes estrategias según el tipo de error
 *    - Cuando necesitas consultar otra fuente de datos en caso de error
 * 
 *    Ejemplo: Si falla el servicio principal, consultar un servicio de respaldo
 * 
 * JERARQUÍA DE EVALUACIÓN:
 * 
 * Los operadores de manejo de errores se evalúan en orden de arriba hacia abajo:
 * 
 * ```java
 * .onErrorResume(ArithmeticException.class, ex -> fallback1())  // 1. Evalúa primero
 * .onErrorResume(ex -> fallback2())                              // 2. Si no coincide el tipo
 * .onErrorReturn(-5)                                             // 3. Último recurso
 * ```
 * 
 * - Si el error es ArithmeticException → usa fallback1()
 * - Si es otro tipo de error → usa fallback2()
 * - Si fallback2() también falla → retorna -5
 * 
 * CASOS DE USO REALES:
 * 
 * 1. Procesamiento de datos con validación:
 *    - Usa onErrorContinue() para procesar elementos válidos y omitir inválidos
 * 
 * 2. Consultas a servicios externos:
 *    - Usa onErrorResume() para fallback a servicio alternativo
 * 
 * 3. Operaciones opcionales:
 *    - Usa onErrorComplete() para tratar errores como "no disponible"
 * 
 * 4. Valores por defecto:
 *    - Usa onErrorReturn() cuando tienes un valor seguro por defecto
 * 
 * 5. Retry con fallback:
 *    - Combina onErrorResume() con lógica de retry
 * 
 * MEJORES PRÁCTICAS:
 * 
 * - Siempre maneja errores en puntos estratégicos del pipeline
 * - Usa tipos específicos de excepciones cuando sea posible
 * - Ordena los manejadores de más específico a más genérico
 * - Loggea errores para debugging (especialmente con onErrorContinue)
 * - Considera el impacto en el negocio: ¿es crítico el error?
 * - No ignores errores silenciosamente sin entender las consecuencias
 * - Usa onErrorResume() para lógica compleja, onErrorReturn() para valores simples
 * 
 * ⚠️ ADVERTENCIAS:
 * 
 * - onErrorContinue() puede ocultar errores importantes si se usa incorrectamente
 * - onErrorComplete() puede hacer que errores críticos pasen desapercibidos
 * - Siempre considera si el error debe ser propagado al suscriptor
 * - El orden de los operadores de error es crítico
 * - No uses múltiples estrategias contradictorias en el mismo pipeline
 * 
 * Esta clase es fundamental para entender cómo mantener pipelines reactivos robustos
 * y resilientes ante errores, que es esencial en aplicaciones de producción.
 */
public class Lec06ErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(Lec06ErrorHandling.class);

    public static void main(String[] args) {



    }

    // skip the error and continue
    private static void onErrorContinue(){
        Flux.range(1, 10)
            .map(i -> i == 5 ? 5 / 0 : i) // intentional
            .onErrorContinue((ex, obj) -> log.error("==> {}", obj, ex))
            .subscribe(Util.subscriber());
    }

    // in case of error, emit complete
    private static void onErrorComplete() {
        Mono.just(1)
            .onErrorComplete()
            .subscribe(Util.subscriber());
    }

    // when you want to return a hardcoded value / simple computation
    private static void onErrorReturn() {
        Mono.just(5)
            .map(i -> i == 5 ? 5 / 0 : i) // intentional
            .onErrorReturn(IllegalArgumentException.class, -1)
            .onErrorReturn(ArithmeticException.class, -2)
            .onErrorReturn(-3)
            .subscribe(Util.subscriber());
    }

    // when you have to use another publisher in case of error
    private static void onErrorResume() {
        Mono.error(new RuntimeException("oops"))
            .onErrorResume(ArithmeticException.class, ex -> fallback1())
            .onErrorResume(ex -> fallback2())
            .onErrorReturn(-5)
            .subscribe(Util.subscriber());
    }

    private static Mono<Integer> fallback1() {
        return Mono.fromSupplier(() -> Util.faker().random().nextInt(10, 100));
    }

    private static Mono<Integer> fallback2() {
        return Mono.fromSupplier(() -> Util.faker().random().nextInt(100, 1000));
    }

}
