package edu.grossmont.blockchain;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import edu.grossmont.p2p.*;

/**
 * Created by rgillespie on 8/2/2018.
 */
public class Blockchain_Test {


    public static void main(String[] args) {

        // *** First stand up Server.
        System.out.print("[main]: Start up Server @ port: ");
        Scanner oPortInput = new Scanner(System.in);

        int iPort = oPortInput.nextInt();

        runServer(iPort);


        // *** Second allow multiple clients.
        System.out.print("[main]: Connect to Server @ IP: ");
        Scanner oServerIPInput = new Scanner(System.in);

        String sServerIP = oServerIPInput.nextLine();

        System.out.print("[main]: Connect to Server @ port: ");
        Scanner oServerPortInput = new Scanner(System.in);

        int iServerPort = oServerPortInput.nextInt();

        runClient(sServerIP, iServerPort, iPort);
    }


    public static void runServer(int iListeningPort){

        P2PServer oServer = new P2PServer(iListeningPort);
        oServer.start();
    }

    public static void runClient(String sRemoteIP, int iRemotePort, int iLocalServerPort){

        P2PClientSession oClient = new P2PClientSession(sRemoteIP, iRemotePort, iLocalServerPort);
        oClient.start();
    }


    /* OLD
    private static void testJsonBlockchain(){
        // Testing json to objects for blockchain passing over wire.

        Blockchain oBlockchain = testBasicChain();

        Gson parser = new Gson();

        // Parse to json.
        String sBlockchainJson = parser.toJson(oBlockchain.chain);
        System.out.println("Blockchain json: " + sBlockchainJson);

        // Parse back to objects.
        List<Block> oBlocks = parser.fromJson(sBlockchainJson, new TypeToken<List<Block>>(){}.getType());

        // Populate new chain and print.
        Blockchain oBlockchainInflated = new Blockchain("testing", 3);
        oBlockchainInflated.chain = new ArrayList<>(oBlocks);
        oBlockchainInflated.printOut();

    }


    private static Blockchain testBasicChain(){

        Blockchain oBlockchain = new Blockchain("testing", 3);

        Transaction oTransaction;

        for(int x = 0; x < oBlockchain.blocksize * 3; x++) {

            oTransaction = new Transaction("{\"buyer\":\"sally\",\"item\":1234,\"amount\":" + x + ".50}");
            oBlockchain.addTransaction(oTransaction);
        }

        System.out.println("Chain is " + oBlockchain.chain.size() + " block(s) long.  Block Details:");

        oBlockchain.printOut();

        return oBlockchain;
    }
    */
}
