package org.java.guba.reactiveprogramming.sec09;

import org.java.guba.reactiveprogramming.common.Util;
import reactor.core.publisher.Flux;

/*
    To collect the items received via Flux. Assuming we will have finite items!
 */
public class Lec14CollectList {

    public static void main(String[] args) {

        Flux.range(1, 10)
                .collectList()
                .subscribe(Util.subscriber());

    }

}
