package org.java.guba.reactiveprogramming.sec04;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec04.assignment.FileReaderServiceImpl;

import java.nio.file.Path;

/**
 * Lec09: Assignment - Lectura de Archivos con Flux
 * 
 * Esta clase es una tarea práctica que demuestra cómo leer un archivo línea por línea
 * usando Flux y programación reactiva, combinando todos los conceptos aprendidos.
 * 
 * Funcionalidad:
 * - Lee un archivo línea por línea de forma reactiva
 * - Cada línea se emite como un elemento del Flux
 * - Usa takeUntil() para detener la lectura cuando encuentra una línea específica
 * - Procesa el archivo de forma no bloqueante y eficiente en memoria
 * 
 * Conceptos aplicados:
 * - Flux para streams de datos (líneas del archivo)
 * - Lectura reactiva línea por línea (no carga todo el archivo en memoria)
 * - Operador takeUntil() para detener cuando se encuentra "line17"
 * - I/O no bloqueante para eficiencia
 * 
 * Ventajas sobre lectura tradicional:
 * - Tradicional: Carga todo el archivo en memoria (ineficiente para archivos grandes)
 * - Reactivo: Lee línea por línea bajo demanda (eficiente en memoria)
 * - Puede cancelar la lectura en cualquier momento
 * - No bloquea el hilo durante la lectura
 * 
 * Flujo demostrado:
 * 1. Se crea un FileReaderService que lee el archivo reactivamente
 * 2. Se lee línea por línea usando Flux
 * 3. Se usa takeUntil() para detener cuando se encuentra "line17"
 * 4. Las líneas se procesan reactivamente a medida que se leen
 * 
 * Casos de uso reales:
 * - Procesamiento de archivos grandes (logs, CSVs, etc.)
 * - Búsqueda en archivos sin cargar todo en memoria
 * - Procesamiento de streams de datos desde archivos
 * - Análisis de archivos línea por línea
 * 
 * Diferencia con lectura tradicional:
 * - Tradicional: File.readAllLines() carga todo en memoria
 * - Reactivo: Lee bajo demanda, solo lo necesario
 * - Tradicional: Bloquea hasta leer todo
 * - Reactivo: No bloquea, procesa mientras lee
 * 
 * Esta implementación combina:
 * - Flux para manejo de streams
 * - Operadores como takeUntil() para control de flujo
 * - I/O no bloqueante para eficiencia
 * - Programación reactiva para procesamiento eficiente de archivos grandes
 * 
 * Es un ejemplo práctico de cómo aplicar programación reactiva a operaciones
 * de I/O tradicionales para mejorar el rendimiento y uso de memoria.
 */
public class Lec09Assignment {

    public static void main(String[] args) {

        var path = Path.of("src/main/resources/sec04/file.txt");
        var fileReaderService = new FileReaderServiceImpl();
        fileReaderService.read(path)
                .takeUntil(s -> s.equalsIgnoreCase("line17"))
                .subscribe(Util.subscriber());


    }

}
