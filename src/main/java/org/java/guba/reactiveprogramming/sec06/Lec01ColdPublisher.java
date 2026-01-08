package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Lec01: Cold Publisher (Publisher Frío)
 * 
 * Esta clase demuestra el comportamiento de un COLD PUBLISHER, que es el comportamiento
 * por defecto de los Publishers en Reactor.
 * 
 * CARACTERÍSTICAS DE UN COLD PUBLISHER:
 * 
 * 1. Creación de flujo independiente para cada suscriptor:
 *    - Cada vez que un Subscriber se suscribe, se crea un NUEVO flujo de datos
 *    - Cada suscriptor recibe su propia copia completa de los datos
 *    - El método de creación (Flux.create, Flux.generate, etc.) se invoca para CADA suscriptor
 * 
 * 2. Evaluación perezosa por suscripción:
 *    - Los datos se generan solo cuando hay una suscripción
 *    - Cada suscripción genera sus propios datos desde el principio
 *    - No hay compartimiento de datos entre suscriptores
 * 
 * 3. Comportamiento "Netflix-like":
 *    - Cada persona ve su propia película desde el principio
 *    - Si alguien se une tarde, ve desde el inicio
 *    - Cada suscriptor tiene su propia experiencia independiente
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo:
 * - Se crea un Flux que emite 0, 1, 2
 * - sub1 se suscribe → El método Flux.create() se invoca UNA VEZ para sub1
 * - sub2 se suscribe → El método Flux.create() se invoca OTRA VEZ para sub2
 * - Cada suscriptor recibe: 0, 1, 2 (su propia copia completa)
 * 
 * VENTAJAS DE COLD PUBLISHER:
 * 
 * 1. Aislamiento completo:
 *    - Cada suscriptor tiene su propio flujo independiente
 *    - No hay interferencia entre suscriptores
 *    - Cancelación de un suscriptor no afecta a otros
 * 
 * 2. Datos completos:
 *    - Cada suscriptor recibe todos los datos desde el principio
 *    - No se pierden datos si te unes tarde
 *    - Ideal para datos que deben procesarse completamente
 * 
 * 3. Seguridad:
 *    - No hay estado compartido entre suscriptores
 *    - Cada suscripción es independiente y segura
 *    - Fácil de razonar y depurar
 * 
 * 4. Flexibilidad:
 *    - Cada suscriptor puede procesar los datos de manera diferente
 *    - Puedes aplicar diferentes operadores a cada suscripción
 *    - No hay restricciones de tiempo
 * 
 * CASOS DE USO PARA COLD PUBLISHER:
 * 
 * 1. Lectura de archivos:
 *    - Cada suscriptor lee el archivo completo desde el inicio
 *    - Útil cuando necesitas procesar todos los datos
 * 
 * 2. Consultas a base de datos:
 *    - Cada suscriptor ejecuta su propia consulta
 *    - Cada uno obtiene resultados completos e independientes
 * 
 * 3. Transformación de datos:
 *    - Cuando cada suscriptor necesita procesar todos los datos
 *    - Para operaciones batch o ETL
 * 
 * 4. APIs REST:
 *    - Cada llamada obtiene su propia respuesta completa
 *    - Cada cliente recibe datos frescos
 * 
 * 5. Procesamiento de colecciones:
 *    - Cuando necesitas procesar todos los elementos
 *    - Para operaciones que requieren el conjunto completo de datos
 * 
 * DIFERENCIA CON HOT PUBLISHER:
 * 
 * - COLD: Cada suscriptor obtiene su propio flujo completo (como Netflix)
 * - HOT: Todos los suscriptores comparten el mismo flujo (como TV en vivo)
 * 
 * - COLD: El método de creación se invoca para cada suscriptor
 * - HOT: El método de creación se invoca una sola vez
 * 
 * - COLD: Si te unes tarde, ves desde el principio
 * - HOT: Si te unes tarde, te pierdes lo que ya pasó
 * 
 * CUÁNDO USAR COLD PUBLISHER:
 * 
 * - Cuando cada suscriptor necesita todos los datos desde el principio
 * - Para operaciones que deben ejecutarse independientemente
 * - Cuando los datos son estáticos o pueden regenerarse
 * - Para procesamiento batch o análisis de datos completos
 * - Cuando necesitas aislamiento completo entre suscriptores
 * 
 * ⚠️ NOTA: Por defecto, todos los Publishers en Reactor son COLD. No necesitas hacer
 * nada especial para crear un Cold Publisher, es el comportamiento natural.
 */
public class Lec01ColdPublisher {

    private static final Logger log = LoggerFactory.getLogger(Lec01ColdPublisher.class);

    public static void main(String[] args) {

        var flux = Flux.create(fluxSink -> {
            log.info("invoked");
            for (int i = 0; i < 3; i++) {
                fluxSink.next(i);
            }
            fluxSink.complete();
        });


        flux.subscribe(Util.subscriber("sub1"));
        flux.subscribe(Util.subscriber("sub2"));

    }

}
