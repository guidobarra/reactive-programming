package org.java.guba.reactiveprogramming.sec09;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec09.applications.OrderService;
import org.java.guba.reactiveprogramming.sec09.applications.UserService;

/*
    Sequential non-blocking IO calls!
    flatMap is used to flatten the inner publisher / to subscribe to the inner publisher
    Mono is supposed to be 1 item - what if the flatMap returns multiple items!?
 */
public class Lec10MonoFlatMapMany {

    public static void main(String[] args) {

        /*
            We have username
            get all user orders!
         */

        UserService.getUserId("jake")
                .flatMapMany(OrderService::getUserOrders)
                .subscribe(Util.subscriber());


        Util.sleepSeconds(3);
    }

}
