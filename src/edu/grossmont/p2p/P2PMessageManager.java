package edu.grossmont.p2p;

import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rgillespie on 8/6/2018.
 */
public class P2PMessageManager {

    private static volatile P2PMessageQueue m_oIncomingMessageQueue;

    // All connected servers have a key entry.
    private static volatile ConcurrentHashMap<String, P2PMessageQueue> m_mapOutgoingGuidToQueue;

    // Tracks connected clients.
    private static volatile LinkedHashSet<String> m_oClientGuids;


    public P2PMessageManager(){

        if(m_oIncomingMessageQueue == null) {
            m_oIncomingMessageQueue = new P2PMessageQueue();
        }
        if(m_mapOutgoingGuidToQueue == null){
            m_mapOutgoingGuidToQueue = new ConcurrentHashMap<>();
        }
        if(m_oClientGuids == null){
            m_oClientGuids = new LinkedHashSet<>();
        }
    }



    public void addOutgoingSessionGuid(String sSessionGuid){
        synchronized (m_mapOutgoingGuidToQueue) {
            m_mapOutgoingGuidToQueue.putIfAbsent(sSessionGuid, new P2PMessageQueue());
        }
    }

    public void removeOutgoingSessionGuid(String sSessionGuid){

        synchronized (m_mapOutgoingGuidToQueue) {
            if (sSessionGuid != null) {
                m_mapOutgoingGuidToQueue.remove(sSessionGuid);
            }
        }
    }



    public void sendMessageToServer(String sMessage, String sSessionGuid){

        synchronized (m_mapOutgoingGuidToQueue) {
            if (m_mapOutgoingGuidToQueue.containsKey(sSessionGuid)) {

                P2PMessage oMessage = new P2PMessage();
                oMessage.setMessage(sMessage);
                oMessage.setSessionGuid(sSessionGuid);
                m_mapOutgoingGuidToQueue.get(sSessionGuid).enqueue(oMessage);
            }
        }
    }



    public void broadcastMessageToServers(String sMessage){

        synchronized (m_mapOutgoingGuidToQueue) {
            for (P2PMessageQueue oQueue : m_mapOutgoingGuidToQueue.values()) {
                P2PMessage oMessage = new P2PMessage();
                oMessage.setMessage(sMessage);
                // Don't believe reason to set session guid for outgoing messages since queues are grouped by guid.

                oQueue.enqueue(oMessage);
            }
        }
    }



    public void receivedMessageFromClient(String sMessage, String sSessionGuid){

        P2PMessage oMessage = new P2PMessage();

        oMessage.setSessionGuid(sSessionGuid);
        oMessage.setMessage(sMessage);

        m_oIncomingMessageQueue.enqueue(oMessage);
    }




    public void receivedMessageFromServer(String sMessage, String sSessionGuid){

        P2PMessage oMessage = new P2PMessage();

        oMessage.setSessionGuid(sSessionGuid);
        oMessage.setMessage(sMessage);

        m_oIncomingMessageQueue.enqueue(oMessage);
    }



    public String getOutgoingMessage(String sSessionGuid){

        synchronized (m_mapOutgoingGuidToQueue) {
            if (m_mapOutgoingGuidToQueue.containsKey(sSessionGuid)) {

                P2PMessage oMessage = m_mapOutgoingGuidToQueue.get(sSessionGuid).dequeue();

                if (oMessage != null) {
                    return oMessage.getMessage();
                }

                return null;
            } else {
                addOutgoingSessionGuid(sSessionGuid);
                return null;
            }
        }
    }



    // Provides opportunity to read sending client IP.
    public P2PMessage getIncomingMessageObject(){

        return m_oIncomingMessageQueue.dequeue();
    }


    // Extracts message only if caller doesn't care about which client IP it came from.
    public String getIncomingMessage(){

        P2PMessage oMessage = m_oIncomingMessageQueue.dequeue();

        if(oMessage != null){
            return oMessage.getMessage();
        }

        return null;
    }


    public String getServerGuidsDelimited(){

        synchronized (m_mapOutgoingGuidToQueue) {
            StringBuilder sbGuids = new StringBuilder();

            for (String sGuid : m_mapOutgoingGuidToQueue.keySet()) {

                sbGuids.append(",");
                sbGuids.append(sGuid);
            }

            // Take off first comma and return.
            if(sbGuids.length() < 1){
                return sbGuids.toString();
            }
            else {
                return sbGuids.toString().substring(1);
            }
        }
    }


    public String getClientGuidsDelimited(){

        synchronized(m_oClientGuids){

            StringBuilder sbGuids = new StringBuilder();

            for(String sGuid: m_oClientGuids){

                sbGuids.append(",");
                sbGuids.append(sGuid);
            }

            // Take off first comma and return.
            if(sbGuids.length() < 1){
                return sbGuids.toString();
            }
            else {
                return sbGuids.toString().substring(1);
            }
        }
    }



    public int getClientsConnectedCount(){
        return m_oClientGuids.size();
    }



    public void addClientGuid(String sGuid){

        synchronized(m_oClientGuids){
            m_oClientGuids.add(sGuid);
        }
    }

    public void removeClientGuid(String sGuid){

        synchronized(m_oClientGuids){
            m_oClientGuids.remove(sGuid);
        }
    }

}
