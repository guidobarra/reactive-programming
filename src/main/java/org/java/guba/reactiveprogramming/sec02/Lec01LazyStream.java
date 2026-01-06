package org.java.guba.reactiveprogramming.sec02;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Lec01: Demostración de Evaluación Perezosa (Lazy Evaluation) en Java Streams
 * 
 * Esta clase demuestra el concepto de evaluación perezosa usando Java Streams.
 * Los operadores intermedios (como peek) NO se ejecutan hasta que se llama a un
 * operador terminal (como toList, forEach, collect, etc.).
 * 
 * En programación reactiva, este concepto es fundamental: los Publishers no
 * producen datos hasta que un Subscriber se suscribe y solicita datos.
 * 
 * Diferencia clave: Sin el operador terminal (toList), el peek nunca se ejecutaría.
 */
public class Lec01LazyStream {

    private static final Logger log = LoggerFactory.getLogger(Lec01LazyStream.class);

    public static void main(String[] args) {

        Stream.of(1)
                .peek(i -> log.info("received : {}", i))
                .toList();


    }

    
}
