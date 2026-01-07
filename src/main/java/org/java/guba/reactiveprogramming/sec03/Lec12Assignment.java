package org.java.guba.reactiveprogramming.sec03;


import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec03.assignment.StockPriceObserver;
import org.java.guba.reactiveprogramming.sec03.client.ExternalServiceClient;

/**
 * Lec12: Assignment - Observador de Cambios de Precios de Acciones
 * 
 * Esta clase es una tarea práctica que demuestra cómo implementar un observador
 * de cambios de precios de acciones usando programación reactiva.
 * 
 * Funcionalidad:
 * - Se conecta a un servicio externo que envía actualizaciones de precios en streaming
 * - Usa un Subscriber personalizado (StockPriceObserver) para procesar los cambios
 * - Los precios llegan en tiempo real de forma no bloqueante
 * - El observador puede procesar, filtrar o reaccionar a los cambios de precio
 * 
 * Conceptos aplicados:
 * - Streaming de datos en tiempo real
 * - I/O no bloqueante
 * - Subscriber personalizado para lógica de negocio específica
 * - Manejo de flujos continuos de datos
 * 
 * Diferencia con polling tradicional:
 * - Tradicional: Consulta periódica al servidor (ineficiente, puede perder datos)
 * - Reactivo: El servidor envía actualizaciones cuando ocurren (eficiente, en tiempo real)
 * 
 * Casos de uso reales:
 * - Aplicaciones de trading y finanzas
 * - Dashboards en tiempo real
 * - Monitoreo de métricas
 * - Notificaciones push
 * 
 * ⚠️ Nota: Asegúrate de que el servicio externo esté ejecutándose (docker-compose up)
 * 
 * Esta implementación combina todos los conceptos aprendidos:
 * - Flux para streams de datos
 * - Subscriber personalizado para lógica específica
 * - I/O no bloqueante para eficiencia
 * - Manejo de datos en tiempo real
 */
public class Lec12Assignment {

    public static void main(String[] args) {

        var client = new ExternalServiceClient();
        var subscriber = new StockPriceObserver();
        client.getPriceChanges()
                .subscribe(subscriber);


        Util.sleepSeconds(20);

    }

}
