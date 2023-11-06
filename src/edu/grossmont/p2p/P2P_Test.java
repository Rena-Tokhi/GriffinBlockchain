package edu.grossmont.p2p;

import java.util.Scanner;

/**
 * Created by rgillespie on 8/5/2018.
 */
public class P2P_Test {

    public static void main(String[] args){

        P2PServer oServer = new P2PServer(8800);

        oServer.start();


//        P2PClientSession oClient = new P2PClientSession("192.168.0.14", 8800);
//
//        oClient.start();


//        P2PClientSession oClient2 = new P2PClientSession("192.168.0.40", 8800);
//
//        oClient2.start();

    }
}
