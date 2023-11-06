package edu.grossmont.p2p;


import edu.grossmont.blockchain.BlockchainUtil;

import java.net.*;
import java.io.*;

/**
 * Created by rgillespie on 8/4/2018.
 */
public class P2PServer implements Runnable {

    private static String thisServerIP;
    private static int thisServerPort;
    private Thread m_oThread;


    public P2PServer(int iPort) {

        thisServerPort = iPort;
    }


    // This method will keep caller from having to instantiate Thread object first.
    public void start(){

        if(m_oThread == null){
            m_oThread = new Thread(this, "server thread @ port " + thisServerPort);
            m_oThread.start();
        }
    }


    // Called as part of Runnable interface and shouldn't be called directly by code.
    public void run(){

        try (ServerSocket oServerSocket = new ServerSocket(thisServerPort)) {

            System.out.println("Server is listening on port " + thisServerPort);

            // Doesn't work on network masked computers, like Grossmont labs.
            //thisServerIP = oServerSocket.getInetAddress().getLocalHost().getHostAddress();

            // This is a work around to getting IP other computers need to be using to connect.
            try(final DatagramSocket socket = new DatagramSocket()){
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                thisServerIP = socket.getLocalAddress().getHostAddress();
            }

            while (true) {
                Socket oSocket = oServerSocket.accept();
                //System.out.println("[server]: New client connected: " + oSocket.getRemoteSocketAddress());

                // Send off this new client connection to its own thread.
                new P2PServerSession(oSocket, oSocket.getRemoteSocketAddress().toString()).start();
            }
        }
        catch (IOException ex) {
            System.out.println("[server]: Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }


    public static String getThisServerIP() {
        return thisServerIP;
    }

    public static int getThisServerPort() {
        return thisServerPort;
    }

}
