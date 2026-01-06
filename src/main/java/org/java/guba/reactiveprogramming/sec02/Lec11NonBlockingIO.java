package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec02.client.ExternalServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lec11: Demostración de I/O No Bloqueante (Non-Blocking I/O)
 * 
 * Esta clase demuestra la diferencia entre operaciones bloqueantes y no bloqueantes
 * en programación reactiva.
 * 
 * Conceptos clave:
 * - I/O No Bloqueante: Las llamadas a servicios externos NO bloquean el hilo
 * - Múltiples operaciones pueden ejecutarse concurrentemente sin crear múltiples hilos
 * - El método subscribe() inicia la operación de forma asíncrona y retorna inmediatamente
 * 
 * Comparación:
 * 
 * 1. Con subscribe() (No Bloqueante - RECOMENDADO):
 *    - client.getProductName(i).subscribe(Util.subscriber())
 *    - Retorna inmediatamente, la operación continúa en segundo plano
 *    - Permite procesar múltiples solicitudes concurrentemente
 *    - Un solo hilo puede manejar muchas operaciones I/O
 * 
 * 2. Con block() (Bloqueante - NO RECOMENDADO):
 *    - var name = client.getProductName(i).block()
 *    - BLOQUEA el hilo hasta que la operación complete
 *    - Cada llamada espera a que la anterior termine
 *    - Requiere múltiples hilos para concurrencia (ineficiente)
 * 
 * Diferencia clave:
 * - subscribe(): No bloquea, permite procesar 100 solicitudes concurrentemente
 * - block(): Bloquea, procesa una solicitud a la vez (muy lento)
 * 
 * Nota: Asegúrate de que el servicio externo esté ejecutándose (docker-compose up)
 */
public class Lec11NonBlockingIO {

    private static final Logger log = LoggerFactory.getLogger(Lec11NonBlockingIO.class);

    public static void main(String[] args) {

        var client = new ExternalServiceClient();

        log.info("starting");

        for (int i = 1; i <= 100; i++) {
            client.getProductName(i)
                    .subscribe(Util.subscriber());

            //vs
            //block (no use block)

            /*
            var name = client.getProductName(i)
                    .block();
            log.info(name);
             */
        }

        Util.sleepSeconds(2);
    }

}
