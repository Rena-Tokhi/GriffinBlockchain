package edu.grossmont.p2p;

import edu.grossmont.blockchain.Blockchain;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

/**
 * Created by rgillespie on 8/6/2018.
 */
public class P2PUtil {


    public String generateGuid(){

        return UUID.randomUUID().toString();
    }


    // User initiates connection into entire network at startup.
    // Also used by server when client first connects  to see if should connect back to it.
    public static synchronized void parseNodesAndConnect(String sDelimitedNodes, int iLocalServerPort){

        //System.out.println("nodes received: " + sDelimitedNodes);

        String[] asNodes = sDelimitedNodes.split(",");
        String[] asIPAndPort;
        String sLocalServerIP = P2PServer.getThisServerIP();
        P2PClientSession oClient;
        P2PMessageManager oManager = new P2PMessageManager();

        for(String sNode: asNodes){

            // Account for no connected nodes from server (means server is connected to 0 servers).
            if(sNode.length() > 0) {
                if (sNode.substring(0, 1).equals("/")) {
                    sNode = sNode.substring(1);
                }
                asIPAndPort = sNode.split(":");

                if (asIPAndPort.length == 2) {

                    // Check if already connected to first and not this same server:
                    if(!oManager.getServerGuidsDelimited().contains(sNode) &&
                            !(sNode.contains(P2PServer.getThisServerIP() + ":" + P2PServer.getThisServerPort()))) {

                        // Ignore if IP is same as this node only if this network is "1 instance per node" restricted.
                        // Scenarios... server node is:
                        // 1. Same machine w/ different port -- only connect if not restricted to one instance.
                        // 2. Same machine w/ same port -- don't connect.
                        // 3. Different machine -- connect.


//                        // Don't allow local server if restricted to one instance per server.
//                        if ((sLocalServerIP.equals("127.0.0.1") || P2PServer.getThisServerIP().equals(sLocalServerIP)) &&
//                                Blockchain.getHeadBlock().isOneInstancePerNode()) {
//
//                            System.out.println("[main]: This blockchain restricts to only one instance " +
//                                    "of this blockchain running on one machine.");
//                        } else {

                            // Sends client off onto separate thread if not localhost:
                        if(!asIPAndPort[0].equals("127.0.0.1") && !asIPAndPort[0].equals("localhost")){
                            oClient = new P2PClientSession(asIPAndPort[0], Integer.parseInt(asIPAndPort[1]), iLocalServerPort);
                            oClient.start();
                        }
                    }
                }
            }
        }
    }


    // Allows a one time socket call and then closing connection
    public static String connectForOneMessage(String sIP, int iPort, String sMessage){


        try (Socket oSocket = new Socket())
        {
            oSocket.connect(new InetSocketAddress(sIP, iPort), 5000);

            OutputStream output = oSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.println(sMessage);
            writer.flush();

            InputStream input = oSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String sReceivedMessage = reader.readLine();
            oSocket.close();

            return sReceivedMessage;
        }
        catch (IOException ex)
        {
            System.out.println("[Client]: server exception:" + ex.getMessage());
            // ex.printStackTrace();
            return null;
        }
    }
}
