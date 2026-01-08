package org.java.guba.reactiveprogramming.sec06;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec06.assignment.ExternalServiceClient;
import org.java.guba.reactiveprogramming.sec06.assignment.InventoryService;
import org.java.guba.reactiveprogramming.sec06.assignment.RevenueService;

/**
 * Lec06: Assignment - Sistema de Procesamiento de Órdenes con Hot Publisher
 * 
 * Esta clase es una tarea práctica que demuestra cómo usar Hot Publishers en un
 * escenario real: un sistema de procesamiento de órdenes donde múltiples servicios
 * deben procesar las mismas órdenes de forma independiente.
 * 
 * ARQUITECTURA DEL SISTEMA:
 * 
 * 1. ExternalServiceClient:
 *    - Proporciona un stream de órdenes (orderStream())
 *    - Las órdenes llegan continuamente desde un servicio externo
 *    - Actúa como fuente de datos compartida
 * 
 * 2. InventoryService:
 *    - Procesa órdenes para actualizar inventario
 *    - Consume el mismo stream de órdenes
 *    - Mantiene su propio estado de inventario
 * 
 * 3. RevenueService:
 *    - Procesa órdenes para calcular ingresos
 *    - Consume el mismo stream de órdenes
 *    - Mantiene su propio estado de ingresos
 * 
 * COMPORTAMIENTO DEMOSTRADO:
 * 
 * En este ejemplo:
 * - El ExternalServiceClient proporciona un stream de órdenes (probablemente Hot Publisher)
 * - Ambos servicios (InventoryService y RevenueService) se suscriben al mismo stream
 * - Cada servicio procesa las mismas órdenes de forma independiente
 * - Cada servicio mantiene su propio estado y genera su propio stream de resultados
 * - Los streams de resultados se suscriben para mostrar el procesamiento
 * 
 * CONCEPTOS APLICADOS:
 * 
 * 1. Hot Publisher:
 *    - El stream de órdenes se comparte entre múltiples servicios
 *    - Cada orden se procesa una vez pero se distribuye a todos los servicios
 *    - Eficiencia: las órdenes no se duplican, solo se distribuyen
 * 
 * 2. Procesamiento paralelo:
 *    - Múltiples servicios procesan las mismas órdenes simultáneamente
 *    - Cada servicio tiene su propia lógica de negocio
 *    - Los servicios son independientes entre sí
 * 
 * 3. Separación de responsabilidades:
 *    - Cada servicio tiene una responsabilidad específica
 *    - Los servicios no se interfieren entre sí
 *    - Fácil de mantener y escalar
 * 
 * CASOS DE USO REALES:
 * 
 * 1. Sistemas de e-commerce:
 *    - Una orden debe actualizar inventario, calcular ingresos, enviar notificaciones, etc.
 *    - Múltiples servicios procesan la misma orden independientemente
 * 
 * 2. Event-driven architecture:
 *    - Eventos se distribuyen a múltiples consumidores
 *    - Cada consumidor procesa el evento según su lógica específica
 * 
 * 3. Microservicios:
 *    - Un evento/orden se distribuye a múltiples microservicios
 *    - Cada microservicio realiza su procesamiento específico
 * 
 * 4. Analytics y reporting:
 *    - Los mismos datos se procesan por múltiples sistemas de análisis
 *    - Cada sistema genera sus propios reportes y métricas
 * 
 * VENTAJAS DE ESTA ARQUITECTURA:
 * 
 * 1. Escalabilidad:
 *    - Puedes agregar más servicios sin modificar los existentes
 *    - Cada servicio escala independientemente
 * 
 * 2. Resiliencia:
 *    - Si un servicio falla, los otros continúan funcionando
 *    - Los servicios están desacoplados
 * 
 * 3. Eficiencia:
 *    - Las órdenes se procesan una vez pero se distribuyen a todos
 *    - No hay duplicación de procesamiento de la fuente
 * 
 * 4. Mantenibilidad:
 *    - Cada servicio tiene responsabilidades claras
 *    - Fácil de entender, probar y mantener
 * 
 * DIFERENCIA CON PROCESAMIENTO SECUENCIAL:
 * 
 * Procesamiento secuencial (tradicional):
 * - Un servicio procesa la orden completamente antes del siguiente
 * - Más lento y acoplado
 * - Si un servicio falla, todo se detiene
 * 
 * Procesamiento reactivo con Hot Publisher:
 * - Múltiples servicios procesan simultáneamente
 * - Más rápido y desacoplado
 * - Si un servicio falla, los otros continúan
 * 
 * ⚠️ NOTA: Asegúrate de que el servicio externo esté ejecutándose (docker-compose up)
 * 
 * Esta implementación demuestra cómo usar Hot Publishers para crear sistemas
 * distribuidos y desacoplados donde múltiples servicios procesan los mismos eventos
 * de forma independiente y eficiente.
 */
public class Lec06Assignment {

    public static void main(String[] args) {

        var client = new ExternalServiceClient();
        var inventoryService = new InventoryService();
        var revenueService = new RevenueService();

        client.orderStream().subscribe(inventoryService::consume);
        client.orderStream().subscribe(revenueService::consume);

        inventoryService.stream()
                        .subscribe(Util.subscriber("inventory"));

        revenueService.stream()
                        .subscribe(Util.subscriber("revenue"));


        Util.sleepSeconds(30);

    }

}
