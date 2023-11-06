package edu.grossmont.blockchain;

/**
 * Created by rgillespie on 8/11/2018.
 */
public class TransactionOutput {

    public String id;
    public String recipientPublicKey;
    public float amount;
    public String parentTransactionId; // The hash of the transaction this output was created in.


    public TransactionOutput(String recipientPublicKey, float amount, String parentTransactionId) {
        this.recipientPublicKey = recipientPublicKey;
        this.amount = amount;
        this.parentTransactionId = parentTransactionId;
        this.id = BlockchainUtil.generateHash(recipientPublicKey + amount + parentTransactionId);
    }
}
