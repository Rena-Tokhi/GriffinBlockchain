package edu.grossmont.blockchain;

/**
 * Created by rgillespie on 8/11/2018.
 */
public class TransactionInput {

    public String transactionOutputId; //Reference to TransactionOutputs -> transactionId
    public TransactionOutput UTXO; //Contains the Unspent transaction output

    public TransactionInput(TransactionOutput UTXO){

        transactionOutputId = UTXO.id;
        this.UTXO = UTXO;
    }
}
