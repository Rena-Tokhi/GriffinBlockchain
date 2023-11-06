package edu.grossmont.p2p;

import java.net.*;
import java.io.*;


/**
 * Created by rgillespie on 8/4/2018.
 */
public class P2PClientSession implements Runnable{

    private Socket m_oSocket;
    PrintWriter m_oWriter;

    private String m_sRemoteIP;
    private int m_iRemotePort;
    private int m_iLocalServerPort;
    private Thread m_oThread;
    private String m_sThreadGuid;
    private int m_iConnectTimeoutMillis = 5000;
    private long m_lSleepTime = 5000; // Don't check to send messages for this long when queue empty.
    private long m_lPingIntervalMillis = 10000; // After this many millis, ping to make sure connection active.
    private long m_lLastPingMillis = 0;
    private String m_sNewSocketCreated = "new";
    private String m_sCloseConnection = "exit";

    private String m_sFirstMessage = null;

    public P2PClientSession(String sRemoteIP, int iRemotePort, int iLocalServerPort){

        initValues(sRemoteIP, iRemotePort, iLocalServerPort, null);
    }
    public P2PClientSession(String sRemoteIP, int iRemotePort, int iLocalServerPort,
                            String sFirstMessage){

        initValues(sRemoteIP, iRemotePort, iLocalServerPort, sFirstMessage);
    }



    private void initValues(String sRemoteIP, int iRemotePort, int iLocalServerPort,
                            String sFirstMessage){

        m_sRemoteIP = sRemoteIP;
        m_iRemotePort = iRemotePort;
        m_iLocalServerPort = iLocalServerPort;
        m_sFirstMessage = sFirstMessage;
    }



    // This method will keep caller from having to instantiate Thread object first.
    public void start(){

        if(m_oThread == null){
            m_oThread = new Thread(this, "[client]: this thread connected to " + m_sRemoteIP + ":" + m_iRemotePort);
            m_oThread.start();
        }
    }


    // Called as part of Runnable interface and shouldn't be called directly by code.
    public void run() {

        P2PMessageManager oMessageManager = new P2PMessageManager();

        String sMessageToSend;
        String sMessageFromServer;
        String sPing = "ping";
        String sNodes = "nodes";
        String sLocalServerPort = "localServerPort:" + m_iLocalServerPort;


        try{

            initSocket();


            // *** MESSAGE #1 ***
            // Pass along server port of this instance so server can connect back if not connected already.
            sendMessageToServer(sLocalServerPort);


            // Send an initial message passed into this class's constructor.
            if(m_sFirstMessage != null) {

                // Allow server to connect back for next message.
                Thread.sleep(2000);

                sendMessageToServer(m_sFirstMessage);

                // Allow server to process above first message before networking.
                Thread.sleep(3000);
            }


            // ************************************************************
            // *** BEGIN Attempt to connect to all server's connections ***

            // *** MESSAGE #2 ***
            // Request all network nodes from server
            // and pass this instance's server port to enable remote server to connect back to this instance server.
            sendMessageToServer(sNodes);

            // Wait for server reply -- if hung then will eventually timeout.
            String sDelimitedNodes = getMessageFromServer();


            // Connect to all nodes server is connected to.
            P2PUtil.parseNodesAndConnect(sDelimitedNodes, m_iLocalServerPort);

            // *** END Connecting to server's connections ***
            // **********************************************

            // Infinite loop until "exit" entered.
            do {

                // Keep checking queue for new messages to send.
                while(true){

                    // Check for new message.
                    sMessageToSend = oMessageManager.getOutgoingMessage(m_sThreadGuid);

                    // Check if time to ping server to make sure socket is still active - only if no message to send.
                    if(sMessageToSend == null &&
                            (System.currentTimeMillis() - m_lLastPingMillis > m_lPingIntervalMillis)){

                        // Send ping to server.
                        sendMessageToServer(sPing);

                        // Only wait for server reply if pinging -- if hung then will eventually timeout.
                        sMessageFromServer = getMessageFromServer();

                        if(sMessageFromServer.equals(sPing)){

                            m_lLastPingMillis = System.currentTimeMillis();

                            // Ping print out reply from server.
//                            System.out.println("[client]: from Server (" + m_sThreadGuid + "): " +
//                                    sMessageFromServer);
                        }
                        else if(sMessageFromServer.equals(m_sNewSocketCreated)){

                            // No need to do anything currently for this state.
                        }
                        else if(sMessageFromServer.equals(m_sCloseConnection)){
                            sMessageToSend = m_sCloseConnection;
                        }
                    }


                    // This will guarantee to only sleep if queue is empty for this session.
                    if(sMessageToSend != null){
                        break;
                    }

                    // Timeout so not eating up cycles.
                    try {
                        Thread.sleep(m_lSleepTime);
                    }
                    catch(Exception ex){
                        System.out.println("[client] Error thrown while sleeping.");
                    }
                }

                // Send message to server.
                sendMessageToServer(sMessageToSend);

                // Currently not getting ack back from server.
                //sMessageFromServer = getMessageFromServer();
                //System.out.println("[client]: from Server (" + m_sRemoteIP + ":" + m_iRemotePort + "): " + sMessageFromServer);
            }
            while (!sMessageToSend.equals(m_sCloseConnection));
        }
        catch (UnknownHostException ex) {

            System.out.println("[client]: Server not found: " + ex.getMessage());

        }
        catch (IOException ex) {

            System.out.println("[client]: I/O error: " + ex.getMessage());
        }
        catch (Exception ex) {

            System.out.println("[client]: error: " + ex.getMessage());
        }
        finally {

            // Make sure socket is closed and remove from message queue.
            oMessageManager.removeOutgoingSessionGuid(m_sThreadGuid);

            if(!m_oSocket.isClosed()){
                try {
                    m_oSocket.close();
                }
                catch(Exception ex){
                    System.out.println("[client]: Trouble closing connection to " + m_sThreadGuid +
                            "(" + ex.getMessage() + ")");
                }
            }
        }

        //System.out.println("[client]: Client closing (" + m_sThreadGuid + ")");
    }


    // Used for first time or reconnect.
    private void initSocket() throws IOException{

        P2PMessageManager oMessageManager = new P2PMessageManager();

        // *************************
        // *** CONNECT to SERVER ***
        // Connect in such a way that we can set timeout (default is 20 secs).
        m_oSocket = new Socket();
        m_oSocket.connect(new InetSocketAddress(m_sRemoteIP, m_iRemotePort), m_iConnectTimeoutMillis);
        // This one line would connect, but we want to adjust the timeout for reconnections to smaller than 20 secs.
        //m_oSocket = new Socket(m_sRemoteIP, m_iRemotePort); // doesn't allow timeout to be set.


        // Connection established.
        //System.out.println("[client]: Connection established to: " + m_oSocket.getRemoteSocketAddress());

        m_sThreadGuid = m_oSocket.getRemoteSocketAddress().toString();

        // Set baseline for pinging.
        m_lLastPingMillis = System.currentTimeMillis();

        // Add to message queue
        oMessageManager.addOutgoingSessionGuid(m_sThreadGuid);

        OutputStream oOutput = m_oSocket.getOutputStream();
        m_oWriter = new PrintWriter(oOutput, true);
    }



    private void sendMessageToServer(String sMessage){

        m_oWriter.println(sMessage);
        m_oWriter.flush();
    }



    private String getMessageFromServer(){

        try {
            InputStream input = m_oSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            return reader.readLine();

        }
        catch(IOException ex){
            if(ex.getMessage().equals("Connection reset")){
                try {
                    initSocket();
                    return m_sNewSocketCreated;
                }
                catch(Exception ex2){

                    // Commenting out for now since appears every time a disconnect.
//                    System.out.println("[client]: Trouble (after connection reset) reconnecting to " + m_sRemoteIP + ":" + m_iRemotePort +
//                            "(" + ex2.getMessage() + ")");
                }
            }
            else
                System.out.println("[client]: Trouble connecting to " + m_sRemoteIP + ":" + m_iRemotePort +
                        "(" + ex.getMessage() + ")");
        }

        return m_sCloseConnection;
    }
}
