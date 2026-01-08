package org.java.guba.reactiveprogramming.sec05;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec05.assignment.ExternalServiceClient;

/*
    Ensure that the external service is up and running!
 */
public class Lec11Assignment {

    public static void main(String[] args) {


        var client = new ExternalServiceClient();

        for (int i = 1; i < 5; i++) {
            client.getProductName(i)
                    .subscribe(Util.subscriber());
        }

        Util.sleepSeconds(3);


    }

}
