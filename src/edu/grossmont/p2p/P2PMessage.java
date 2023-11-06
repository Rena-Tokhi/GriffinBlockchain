package edu.grossmont.p2p;

/**
 * Created by rgillespie on 8/5/2018.
 */
public class P2PMessage {

    // originally an interface but decoupled blockchain message object from p2p section.
//    public void setMessageType(String sMessageType);
//    public String getMessageType();
//    public void setMessage(String sMessage);
//    public String getMessage();

    private String sSessionGuid;
    private String sMessage;

    // Used for queue.
    public P2PMessage next = null;

    public P2PMessage(){

    }

    public void setSessionGuid(String sSessionGuid){

        this.sSessionGuid = sSessionGuid;
    }

    // Valuable if want to know which client message came from,
    // since messages from multiple clients are aggregated into one queue.
    public String getSessionGuid(){

        return sSessionGuid;
    }

    public void setMessage(String sMessage){

        this.sMessage = sMessage;
    }

    public String getMessage(){

        return sMessage;
    }
}
