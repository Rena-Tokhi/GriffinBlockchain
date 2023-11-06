package edu.grossmont.blockchain;


import edu.grossmont.cryptography.RsaProcessor;

import java.util.ArrayList;

/**
 * Created by rgillespie on 8/2/2018.
 */
public class Transaction{

    public String blockchainTitle;
    public long timestamp;
    public String hash;
    public String sendersPublicKey;
    public String recipientsPublicKey;

    public float amountToRecipient;
    public String itemIdToSender; // This would be Item object's GUID.

    private ArrayList<TransactionInput> inputs = new ArrayList<TransactionInput>();
    private ArrayList<TransactionOutput> outputs = new ArrayList<TransactionOutput>();

    public String sendersSignature; // This proves sender created this transaction.

    public int sentToNetworkCount = 0;
    public int sentToNetworkMinimum = 2;


    // This constructor will create a new transaction based on all passed in values.
    // Three types of Txs: normal, miner's reward (no inputs), and genesis block creation (no inputs).
    public Transaction(String blockchainTitle, String sendersPublicKey, String recipientsPublicKey,
                       float amountToRecipient, String itemIdToSender, ArrayList<TransactionInput> inputs,
                       String sendersPrivateKey) {

        this.blockchainTitle = blockchainTitle;

        if(!verifySameBlockchain()) {
            return;
        }

        this.timestamp = System.currentTimeMillis();

        this.sendersPublicKey = sendersPublicKey;
        this.recipientsPublicKey = recipientsPublicKey;
        this.amountToRecipient = amountToRecipient;
        this.itemIdToSender = itemIdToSender;
        this.inputs = inputs;

        // Make sure there are inputs, otherwise it's the miner reward/genesis block/coin minting.
        if(this.sendersPublicKey != null) {

            float fTotalInputAmount = 0;

            // Get all referenced input IDs for this tx hash ID so that it is unique in the BC.
            StringBuilder sbTxInputIDs = new StringBuilder();
            for (TransactionInput oTxInput : inputs) {
                sbTxInputIDs.append(oTxInput.transactionOutputId);

                // Keep running total of amount from inputs.
                fTotalInputAmount += oTxInput.UTXO.amount;
            }

            // Create tx ID.
            this.hash = BlockchainUtil.generateHash(sbTxInputIDs.toString());

            // Return left over amount back to sender.
            TransactionOutput oOutputToSender = new TransactionOutput(this.sendersPublicKey,
                    fTotalInputAmount - amountToRecipient, this.hash);
            this.outputs.add(oOutputToSender);
        }

        // Miner reward transaction or genesis block transaction or minting of coins by creator.
        else{
            // Need to hash on another value than inputs when miner reward or genesis block tx.
            this.hash = getNoInputsTxHash();
        }

        // Set output to recipient.
        TransactionOutput oOutputToRecipient = new TransactionOutput(this.recipientsPublicKey, this.amountToRecipient,
                this.hash);
        this.outputs.add(oOutputToRecipient);

        // Sign this transaction and simply use hash since the hash would change if tx was tampered with.
        this.sendersSignature = RsaProcessor.applyECDSASig(sendersPrivateKey, hash);
    }


    // Need to use enough fields so would be near impossible to get same values, otherwise would be dupe UTXO.
    public String getNoInputsTxHash(){

        return BlockchainUtil.generateHash(timestamp + recipientsPublicKey + amountToRecipient + itemIdToSender);
    }



    // Check that funds do exist to support this transaction (if not miner reward),
    // signature is legit, and sender owns item if included.
    public boolean verifyTransaction(){

        BlockchainUtil u = new BlockchainUtil();


        if(!verifySameBlockchain()){
            return false;
        }

        float lInputTotal = 0;
        float lOutputTotal = 0;
        float lOutputToNonMinerRecipient = 0;

        for(TransactionInput oInput: inputs){
            if(!Blockchain.containsUTXO(oInput.transactionOutputId)){

                u.p("Blockchain transaction failed: Referenced output not in mapUTXOs.");
                return false;
            }
            else{
                lInputTotal += Blockchain.getUTXO(oInput.transactionOutputId).amount;
            }
        }

        // Sum all outputs.
        for(TransactionOutput oOutput: outputs){
            lOutputTotal += oOutput.amount;

            // Grab output to recipient if not miner.
            if(recipientsPublicKey != null){
                if(oOutput.recipientPublicKey.equals(recipientsPublicKey)){
                    lOutputToNonMinerRecipient = oOutput.amount;
                }
            }
        }

        // Miner block reward tx.
        if(inputs.size() < 1){

            // itemToSender check to ensure miner doesn't give own-self an item.
            if(getNoInputsTxHash().equals(hash) && verifySignature() && itemIdToSender == null){
                return true;
            }
            else{
                u.p("Blockchain transaction failed: genesis or miner block hash didn't match or signature failed.");
                return false;
            }
        }

        // Regular transaction.
        else if(lInputTotal == lOutputTotal && lInputTotal >= amountToRecipient && verifySignature()){

            // Only do item checks if item was included as part of tx.
            if(itemIdToSender != null) {

                // Check that recipient of funds owns item.
                if (!Blockchain.getItemOwner(itemIdToSender).equals(recipientsPublicKey)) {
                    u.p("Blockchain transaction failed: item owner doesn't match recipient's public key.");
                    return false;
                }

                // Check that item price is covered by output.
                if (!(lOutputToNonMinerRecipient >= Blockchain.getItem(itemIdToSender).getPrice())){
                    u.p("Blockchain transaction failed: Not enough in output total to recipient to cover price.");
                    return false;
                }
            }

            return true;
        }
        else{

            u.p("Blockchain transaction failed: Totals didn't match, not enough coins for purchase, " +
                    "not enough separate UTXOs because others used up by another tx in this block, or signature failure.");
            return false;
        }

    }


    private boolean verifySignature(){

        if(RsaProcessor.verifyECDSASig(sendersPublicKey, hash, sendersSignature)){
            return true;
        }
        else{

            System.out.println("Blockchain transaction failed: Signature not verified.");
            return false;
        }
    }


    private boolean verifySameBlockchain(){

        if(blockchainTitle.equals(Blockchain.getTitle())) {
            return true;
        }
        else{

            System.out.println("Blockchain transaction not verified: Title not the same as current blockchain.");
            return false;
        }
    }



    // Only getters for these since if they change, then whole tx ID and object should be recreated.
    public ArrayList<TransactionInput> getInputs(){
        return inputs;
    }
    public ArrayList<TransactionOutput> getOutputs(){
        return outputs;
    }
}
