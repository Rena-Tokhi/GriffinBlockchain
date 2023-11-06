package edu.grossmont.blockchain;

import edu.grossmont.p2p.P2PMessage;
import edu.grossmont.p2p.P2PUtil;

/**
 * Created by rgillespie on 8/5/2018.
 */
public class BlockchainMessage {

    // Enum will serialize using gson.
    public enum MessageType{
        BLOCKCHAINREQUEST,BLOCKCHAIN,BLOCK,TRANSACTION,IM
    }

    private MessageType eMessageType;
    private Blockchain oBlockchain;
    private Block oBlock;
    private Transaction oTransaction;
    private String sMessage;


    // Constructors force contents of message to be singular.

    // This generic one allows a request of a certain type w/ or w/o input, like BLOCKCHAINREQUEST, IM, etc.
    public BlockchainMessage(MessageType type, String sMessage){

        // This handles other types that don't require input, like BLOCKCHAINREQUEST.
        eMessageType = type;
        this.sMessage = sMessage;
    }
    public BlockchainMessage(Block oBlock){
        this.oBlock = oBlock;
        eMessageType = MessageType.BLOCK;
    }
    public BlockchainMessage(Transaction oTransaction){
        this.oTransaction = oTransaction;
        eMessageType = MessageType.TRANSACTION;
    }



    // Serialize for network messaging.
    public String serialize(){

        return BlockchainUtil.serializeBlockchainMessageToJson(this, false);
    }

    public static BlockchainMessage deserialize(String sJson){

        return BlockchainUtil.deserializeBlockchainMessageFromJson(sJson);
    }


    public MessageType getMessageType(){

        return eMessageType;
    }

    public Block getBlock() {
        return oBlock;
    }

    public Transaction getTransaction() {
        return oTransaction;
    }

    public String getMessage(){return sMessage;}

}
