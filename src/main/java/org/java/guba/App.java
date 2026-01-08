package org.java.guba;


import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) {
        whatCanWeExpect2();
    }

    //1,2,onComplete
    static void whatCanWeExpect1() {
        Flux.range(1, 100)
                .take(25)
                .takeWhile(i -> i < 10)
                .takeUntil(i -> i > 1 && i < 5)
                .take(3)
                .subscribe(Util.subscriber());
    }

    //print System.out.println("created") only one
    static void whatCanWeExpect2() {
        Flux<Object> flux = Flux.create(fluxSink -> {
                    System.out.println("created");
                    for (int i = 0; i < 5; i++) {
                        fluxSink.next(i);
                    }
                    fluxSink.complete();
                })
                .publish()
                .refCount(2);
        flux.subscribe(System.out::println);
        flux.subscribe(System.out::println);
        flux.subscribe(System.out::println);
    }
}
