package org.java.guba.reactiveprogramming.sec03;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec03.client.ExternalServiceClient;

/**
 * Lec08: I/O No Bloqueante con Mensajes Streaming
 * 
 * Esta clase demuestra cómo trabajar con servicios externos que envían
 * mensajes en streaming de forma no bloqueante.
 * 
 * Características:
 * - El servicio externo envía múltiples mensajes en un stream continuo
 * - Cada mensaje se procesa reactivamente cuando llega
 * - Múltiples suscriptores pueden recibir el mismo stream
 * - Las operaciones son no bloqueantes (no bloquean el hilo)
 * 
 * Conceptos clave:
 * - Streaming: Los datos llegan en tiempo real, no todos a la vez
 * - No bloqueante: El hilo no espera, procesa otros datos mientras tanto
 * - Múltiples suscriptores: Cada uno recibe su propia copia del stream
 * 
 * Diferencia con request-response tradicional:
 * - Tradicional: Una solicitud → Una respuesta → Fin
 * - Streaming: Una solicitud → Múltiples respuestas → Continúa hasta completar
 * 
 * Ventajas del streaming:
 * - Procesamiento en tiempo real de datos que llegan progresivamente
 * - Eficiencia: No necesitas esperar a que todos los datos estén listos
 * - Escalabilidad: Puedes manejar flujos grandes sin cargar todo en memoria
 * 
 * Casos de uso:
 * - Actualizaciones de precios en tiempo real
 * - Notificaciones push
 * - Lectura de archivos grandes línea por línea
 * - WebSockets y Server-Sent Events
 * 
 * ⚠️ Nota: Asegúrate de que el servicio externo esté ejecutándose (docker-compose up)
 * 
 * Cuándo usar:
 * - Cuando recibes datos continuos de una fuente externa
 * - Para procesar datos en tiempo real
 * - Cuando los datos son demasiado grandes para cargar de una vez
 * - Para aplicaciones que requieren actualizaciones en vivo
 */
public class Lec08NonBlockingStreamingMessages {

    public static void main(String[] args) {

        var client = new ExternalServiceClient();

        client.getNames()
                .subscribe(Util.subscriber("sub1"));

        client.getNames()
                .subscribe(Util.subscriber("sub2"));

        Util.sleepSeconds(6);

    }

}
