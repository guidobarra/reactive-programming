package org.java.guba.reactiveprogramming.sec09;

import org.java.guba.reactiveprogramming.common.Util;
import org.java.guba.reactiveprogramming.sec09.helper.Kayak;

public class Lec06MergeUseCase {

    public static void main(String[] args) {

        Kayak.getFlights()
                .subscribe(Util.subscriber());


        Util.sleepSeconds(3);


    }

}
