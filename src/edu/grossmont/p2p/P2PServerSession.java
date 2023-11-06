package edu.grossmont.p2p;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rgillespie on 8/4/2018.
 */
public class P2PServerSession implements Runnable {

    private Socket m_oSocket;
    private Thread m_oThread;
    private String m_sClientAddress;
    private String m_sThreadGuid;


    public P2PServerSession(Socket socket, String sTheadGuid) {

        this.m_oSocket = socket;
        this.m_sThreadGuid = sTheadGuid;

        m_sClientAddress = m_oSocket.getRemoteSocketAddress().toString();
    }


    // This method will keep caller from having to instantiate Thread object first.
    public void start(){

        if(m_oThread == null){
            m_oThread = new Thread(this, "server session with client: " +
                    m_oSocket.getRemoteSocketAddress().toString());
            m_oThread.start();
        }
    }


    // Called as part of Runnable interface and shouldn't be called directly by code.
    public void run() {

        P2PMessageManager oMessageManager = new P2PMessageManager();
        String sPingRequest = "ping";
        String sExitRequest = "exit";
        String sNodesRequest = "nodes";
        String sClientsServerPort = "localServerPort:";

        // Register this client.
        oMessageManager.addClientGuid(m_sThreadGuid);

        try {
            InputStream input = m_oSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            OutputStream output = m_oSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);


            String sReceivedMessage;

            // Enter constant listening loop until "exit" received from client.
            while(true) {
                sReceivedMessage = reader.readLine();

                // Check for ping and immediately reply, else client will close connection.
                if(sReceivedMessage.equals(sPingRequest)) {

                    writer.println(sReceivedMessage);
                    writer.flush();
                }

                // STEP 1: Receive client's server IP/PORT (Port will be different from client port).
                // ***Usually the first call from client.
                else if(sReceivedMessage.startsWith(sClientsServerPort)){

                    // *** CONNECT BACK to this client as a server if not already connected -- continues network expansion ***
                    // NOTE: This will guarantee that all nodes are connected to all nodes on network -- no leaves.

                    // 1. First create server guid version of this client based on passed in port.
                    String sRemoteServerPort = sReceivedMessage.split(":")[1];

                    // 2. Next get current client IP.
                    String sRemoteServerIP = m_sThreadGuid.split(":")[0];
                    String sRemoteServerFull = sRemoteServerIP + ":" + sRemoteServerPort;

                    // 3. Then concat and send if doesn't exist in already connected list.
                    if(!oMessageManager.getServerGuidsDelimited().contains(sRemoteServerFull)){

                        P2PUtil.parseNodesAndConnect(sRemoteServerFull, m_oSocket.getLocalPort());
                    }
                }

                // STEP 2: Get currently connected network nodes.
                // *** Usually the second call from client.
                else if(sReceivedMessage.equals(sNodesRequest)){

                    // Send comma delimited servers.
                    writer.println(oMessageManager.getServerGuidsDelimited());
                }

                // Close connection to client.
                else if(sReceivedMessage.equals(sExitRequest)){
                    break;
                }

                // Put into message queue for manager since didn't match any hard coded P2P commands.
                else{

                    // This will be exchange of blockchain json messages.
                    oMessageManager.receivedMessageFromClient(sReceivedMessage, m_sThreadGuid);
                }

                // Currently not needed because monitor prints out.
//                System.out.println("[server session]: Server received from Client(" +
//                        m_oSocket.getRemoteSocketAddress().toString() + "): " + sReceivedMessage);
            }

            m_oSocket.close();
        }
        catch (IOException ex) {
            if(ex.getMessage().equals("Connection reset")){
                // Commenting out for now since appears every time a disconnect.
                //System.out.println("[server session]: client disconnected -- " + m_sClientAddress);
            }
            else {
                System.out.println("[server session]: Server exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        finally {

            // Make sure socket is closed.
            if(!m_oSocket.isClosed()){
                try {
                    m_oSocket.close();
                }
                catch(Exception ex){
                    System.out.println("Trouble closing connection to " + m_sThreadGuid +
                            "(" + ex.getMessage() + ")");
                }
            }
        }

        oMessageManager.removeClientGuid(m_sThreadGuid);
        //System.out.println("[server]: Server session closing with " + m_sThreadGuid);

        // Remove and outgoing connections to any similar IP.

    }
}
