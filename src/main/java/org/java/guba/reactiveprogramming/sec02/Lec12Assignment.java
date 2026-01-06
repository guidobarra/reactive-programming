package org.java.guba.reactiveprogramming.sec02;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec02.assignment.FileServiceImpl;

/**
 * Lec12: Assignment - Operaciones con Archivos usando Programación Reactiva
 * 
 * Esta clase es una tarea práctica que demuestra cómo implementar operaciones
 * de archivos usando Mono y programación reactiva.
 * 
 * Operaciones implementadas:
 * 1. write(): Escribe contenido a un archivo de forma reactiva
 * 2. read(): Lee contenido de un archivo de forma reactiva
 * 3. delete(): Elimina un archivo de forma reactiva
 * 
 * Características:
 * - Todas las operaciones retornan Mono<String> o Mono<Void>
 * - Las operaciones son no bloqueantes (usando I/O asíncrono)
 * - Los errores se manejan de forma reactiva (onError)
 * - Permite componer múltiples operaciones en un pipeline reactivo
 * 
 * Flujo demostrado:
 * 1. Escribe contenido a "file.txt"
 * 2. Lee el contenido del archivo
 * 3. Elimina el archivo
 * 
 * Diferencia con I/O tradicional:
 * - I/O tradicional: Bloquea el hilo durante operaciones de archivo
 * - I/O Reactivo: No bloquea, permite procesar otras operaciones mientras espera
 * 
 * Nota: Esta es una implementación práctica que combina todos los conceptos
 * aprendidos en las lecciones anteriores (Mono, subscribe, fromSupplier, etc.)
 */
public class Lec12Assignment {

    public static void main(String[] args) {

        var fileService = new FileServiceImpl();

        fileService.write("file.txt", "This is a test file")
                        .subscribe(Util.subscriber());

        fileService.read("file.txt")
                .subscribe(Util.subscriber());

        fileService.delete("file.txt")
                .subscribe(Util.subscriber());

    }

}
