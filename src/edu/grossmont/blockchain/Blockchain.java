package edu.grossmont.blockchain;

import edu.grossmont.cryptography.RsaProcessor;
import edu.grossmont.p2p.P2PMessageManager;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rgillespie on 8/2/2018.
 */
public class Blockchain{

    private static String sTitle = null; // static because should only be one blockchain per executable.

    private static String sOzPublicKey;
    private static String sOzUsername;

    private static int iHighestUserCount = 1;


    // All validated blocks in chain.
    private static volatile Block oHeadBlock; // pseudo-linked list implementation of chain.

    // All unspent transactions mapped per transaction ID.
    private static volatile ConcurrentHashMap<String, TransactionOutput> mapUTXOs;

    // All items' IDs mapped to owner's public key.
    private static volatile ConcurrentHashMap<String, String> mapItemIdToOwnersPubKey;
    // All items mapped by ID.
    private static volatile ConcurrentHashMap<String, Item> mapItemIdToItem;

    // Candidate block to allow concurrency.
    private static volatile Block candidateBlock = null;

    // Singleton so that only one instance within executable.
    private static Blockchain oBlockchain;



    // IMPORTANT: Only one blockchain should be live/used within one executable, otherwise static variables will clash.
    // Therefore, implemented as a singleton (private constructor and getInstance method).
    private Blockchain() {}


    // Singleton design pattern: Create a static method to get instance.
    public static Blockchain getInstance(){

        // Not using sychronized here so that sync performance hit is only on first instance creation.
        if(oBlockchain == null){

            // This makes singleton safe in multi-threaded environment.
            synchronized (Blockchain.class) {

                // Need to check again now that synchronized.
                if(oBlockchain == null) {
                    oBlockchain = new Blockchain();
                }
            }
        }
        return oBlockchain;
    }



    // Create a method to set instance on received blockchain json
    // (for when object deserialized via gson to ensure this is the sole instance)
    public Blockchain setInstance(){

        oBlockchain = this;

        return oBlockchain;
    }


    // Creates blockchain and genesis block. (public key inclusion will force caller to already have wallet built)
    public boolean initBlockChain(String sTitle, String sOzUsername, String sOzPublicKey, int iBlockSize,
                                  float fInitialCoinCount, Item oInitialItem, int iDifficulty,
                                  float fPriceIncreaseAfterTx, boolean bOneInstancePerNode){

        float fTxCoinCount;

        // This means instance of BC already exists in memory.
        if(this.sTitle != null){
            return false;
        }


        // This establishes singleton instance.
        setInstance();


        // Variables in Blockchain.
        setTitle(sTitle);
        setOzUsername(sOzUsername);
        setOzPublicKey(sOzPublicKey);


        // Generate Genesis Block.
        oHeadBlock = new Block();

        // Establish initial variables in Gen Block.
        oHeadBlock.setTimeStamp(System.currentTimeMillis());
        oHeadBlock.setBlocksize(iBlockSize);
        oHeadBlock.setPriceIncreaseAfterTx(fPriceIncreaseAfterTx);
        oHeadBlock.setDifficulty(iDifficulty);
        oHeadBlock.setOneInstancePerNode(bOneInstancePerNode);

        oHeadBlock.setIndex(0);
        oHeadBlock.setPreviousHash("root");

        mapUTXOs = new ConcurrentHashMap<>();
        mapItemIdToOwnersPubKey = new ConcurrentHashMap<>();
        mapItemIdToItem = new ConcurrentHashMap<>();

        String sInitialItem = null;

        if(oInitialItem != null){

            // Add it to map.
            mapItemIdToOwnersPubKey.put(oInitialItem.getId(), sOzPublicKey);
            mapItemIdToItem.put(oInitialItem.getId(), oInitialItem);

            sInitialItem = oInitialItem.getId();
        }

        // Divide initial coin count into as many as block size -- This allows multiple sending of coins on first block.
        // Otherwise, can't generate enough TXs on first block to get mined if only one existing UTXO and say 2 block size.
        fTxCoinCount = fInitialCoinCount / iBlockSize;

        // Create first transaction which is just initial coin distribution to creator and item addition if included.
        Transaction oTx = new Transaction(getTitle(), null, Wallet.getPublicKey(), fTxCoinCount,
                sInitialItem, null, Wallet.getPrivateKey());

        oHeadBlock.getTransactions().add(oTx);

        mapUTXOs.put(oTx.getOutputs().get(0).id, oTx.getOutputs().get(0));

        // Now add other TXs if block size more than 1.
        if(iBlockSize > 1){
            for(int x = 1; x < iBlockSize; x++){
                oTx = new Transaction(getTitle(), null, Wallet.getPublicKey(), fTxCoinCount,
                        null, null, Wallet.getPrivateKey());

                oHeadBlock.getTransactions().add(oTx);

                // Add UTXO from Tx.
                mapUTXOs.put(oTx.getOutputs().get(0).id, oTx.getOutputs().get(0));
            }
        }

        oHeadBlock.computeAndSetMerkleRoot();

        oHeadBlock.setHash(oHeadBlock.computeHash());

        // Save updated chain to file.
        BlockchainUtil.saveBlockchainToFile(oBlockchain);

        return true;
    }



    // Create admin block for new item, difficulty change, other changes -- NO MINING REQD.
    public boolean addNewAdminBlock(Item oInitialItem, int iDifficulty, int iBlocksize, float fPriceIncrease,
                                    float fMinersReward){

        BlockchainUtil u = new BlockchainUtil();

        // Generate Block for one item addition to market.
        Block oBlock = new Block();

        // Establish initial variables.

        oBlock.setMinerPublicKey(Wallet.getPublicKey());
        oBlock.setMinerUsername(Wallet.getsUsername());

        oBlock.setTimeStamp(System.currentTimeMillis());


        // Increment index.
        oBlock.setIndex(Blockchain.getHeadBlock().getIndex() + 1);

        // Set previousHash with current Hash.
        oBlock.setPreviousHash(Blockchain.getHeadBlock().getHash());

        // *** Possibly changed variables section ***
        if(iDifficulty > 0){
            oBlock.setDifficulty(iDifficulty);
        }
        else{
            oBlock.setDifficulty(oHeadBlock.getDifficulty());
        }
        if(iBlocksize > 0){
            oBlock.setBlocksize(iBlocksize);
        }
        else{
            oBlock.setBlocksize(Blockchain.getHeadBlock().getBlocksize());
        }
        if(fPriceIncrease > -1){
            oBlock.setPriceIncreaseAfterTx(fPriceIncrease);
        }
        else{
            oBlock.setPriceIncreaseAfterTx(oHeadBlock.getPriceIncreaseAfterTx());
        }
        if(fMinersReward > -1){
            oBlock.setMinersRewardPercent(fMinersReward);
        }
        else{
            oBlock.setMinersRewardPercent(oHeadBlock.getMinersRewardPercent());
        }


        Transaction oTx;

        if(oInitialItem != null) {
            // Add it to map.
            mapItemIdToOwnersPubKey.put(oInitialItem.getId(), sOzPublicKey);
            mapItemIdToItem.put(oInitialItem.getId(), oInitialItem);

            // Create transaction of 0 coins.
            oTx = new Transaction(getTitle(), null, Wallet.getPublicKey(), 0,
                    oInitialItem.getId(), null, Wallet.getPrivateKey());
        }

        else{
            // Create transaction of 0 coins.
            oTx = new Transaction(getTitle(), null, Wallet.getPublicKey(), 0,
                    null, null, Wallet.getPrivateKey());
        }

        oBlock.getTransactions().add(oTx);

        oBlock.computeAndSetMerkleRoot();

        oBlock.setHash(oBlock.computeHash());


        // **************************
        // *** ADD BLOCK TO CHAIN ***
        oBlock.setPreviousBlock(oHeadBlock);
        oHeadBlock = oBlock;
        // **************************

        // Notify miner to stop mining any current blocks.
        // Must do this after adding block above just in case new block comes in in between mining stop and block addition.
        Miner.setAbortPoW(true);


        // Item to update in the owners map.
        if(oTx.itemIdToSender != null){
            mapItemIdToOwnersPubKey.put(oTx.itemIdToSender, getOzPublicKey());
        }

        // Update local wallet.
        Miner.updateWallet(oBlock);

        u.p("");
        u.p("************************************");
        u.p("***** NEW BLOCK ADDED TO CHAIN *****");
        u.p("New Block index: " + oBlock.getIndex());
        u.p("New Block  hash: " + oBlock.getHash());
        u.p("************************************");
        u.p("");

        // Save updated chain to file.
        BlockchainUtil.saveBlockchainToFile(oBlockchain);

        return true;
    }




    // This is a block coming from the network that needs to be fully verified before adding to BC.
    public static synchronized boolean validateAndAddBlock(Block oBlock) {

        // List of checks (easier ones first so as not to waste cycles if not valid):

        // NOTE: Since this node is being received, then assumption is that one miner has approved it
        //       (even though that miner is probably the one who created it), so this node supplies second validation.


        BlockchainUtil u = new BlockchainUtil();

        u.p("Miner received Block (sent through network count: " + oBlock.iSentToNetworkCount + ")",
                true);


        // Check if candidate block waiting to be confirmed -- if so, then see if this block's hash is less.
        if(candidateBlock != null) {

            // If new block is a larger hash number than current candidate block, then discard.
            if (oBlock.getHash().compareTo(candidateBlock.getHash()) >= 0){

                if(oBlock.getHash().equals(candidateBlock.getHash())){
                    // Commenting out for now since appears every time a block comes back through network.
//                    u.p("[Blockchain] New block from network DISCARDED - same block as candidate block.",
//                            true);
                }
                else {
                    u.p("[Blockchain] New block from network DISCARDED - candidate block has smaller hash number.",
                            true);
                }

                return false;
            }
        }

        // - If change signature exists by Oz, then Oz created this block,
        //      and verify Oz's sig and ignore checking matches to previous block of:
        //      difficulty, miner's reward percentage, Oz Block validation, One instance per node restriction.
        if (oBlock.getChangeSignature() != null) {

            // If sig is there and invalid than entire block is not valid.
            if (!RsaProcessor.verifyECDSASig(getOzPublicKey(), oBlock.getHash(), oBlock.getChangeSignature())) {

                u.p("[Blockchain] New block from network DISCARDED - change sig not verified.",
                        true);
                return false;
            }
        }

        // - All these items should match previous block to not allow anyone but Oz to adjust.
        else if ((oBlock.getMinersRewardPercent() != getHeadBlock().getMinersRewardPercent()) ||
                (oBlock.getDifficulty() != getHeadBlock().getDifficulty()) ||
                (oBlock.isOneInstancePerNode() != getHeadBlock().isOneInstancePerNode()) ||
                (oBlock.getPriceIncreaseAfterTx() != getHeadBlock().getPriceIncreaseAfterTx())) {

            u.p("[Blockchain] New block from network DISCARDED - match w/ head block values failed.",
                    true);
            return false;
        }


        // - Check block index is one more than local head.
        // - And Check prev hash matches head block hash.
        if (oBlock.getIndex() != (oHeadBlock.getIndex() + 1) || !oBlock.getPreviousHash().equals(oHeadBlock.getHash())) {

            // TODO: Need to abort this chain and refresh from network if block received that is 2 ahead.
            u.p("[Blockchain] New block from network DISCARDED - either index or prevhash is wrong.",
                    true);

            return false;
        }

        // - Check block hash.
        if (!oBlock.computeHash().equals(oBlock.getHash())) {

            u.p("[Blockchain] New block from network DISCARDED - computing hash didn't match block's hash.",
                    true);
            return false;
        }

        // - Check no dupe TXs against mapUTXOs.
        // - And Check that only one miner reward tx and that it's accurate.
        int iMinerTx = 0;
        float fMinerReward = 0;
        for (Transaction tx : oBlock.getTransactions()) {

            if (mapUTXOs.containsKey(tx.hash)) {

                u.p("[Blockchain] New block from network DISCARDED - either index or prevhash is wrong.",
                        true);
                return false;
            }

            // This is miner reward.
            if (tx.getInputs() == null) {

                // Increment counter, which should never get beyond 1 as checked below after loop.
                iMinerTx++;

                // Verify miner's hash of that tx.
                if (!tx.getNoInputsTxHash().equals(tx.hash)) {
                    return false;
                }

                // Pull out total amount for miner to compare below.
                fMinerReward = tx.amountToRecipient;
            }
        }

        // - Bad block if more than one miner reward tx.
        if (iMinerTx > 1) {
            return false;
        }

        // - Verify miner's reward.
        if (Miner.getMinerReward(oBlock.getTransactions()) != fMinerReward) {
            return false;
        }

        // - Compute merkle root and compare.
        if (!oBlock.validateMerkleTree()) {
            return false;
        }


        // *** NOTE: Doing this before adding to chain so that whole chain is not sent over network.
        // Update block network send count and send if under amount.
        if (oBlock.iSentToNetworkCount < oBlock.iSentToNetworkMinimum) {
            oBlock.iSentToNetworkCount++;

            // *** Send off to other miners ***
            new P2PMessageManager().broadcastMessageToServers(new BlockchainMessage(oBlock).serialize());
        }


        // ************ BEGIN Consensus Management ***************
        // ADD block as candidate and wait period of time for any other block to be confirmed from network.

        if(candidateBlock == null) {

            // Need to replace with this one since already confirmed this block is lower hash #.
            candidateBlock = oBlock;

            // Notify miner to stop mining any current blocks since this or another one mined at same time is next.
            Miner.setAbortPoW(true);

            // Spin off new thread to allow other miners to send in any mined blocks for consensus.
            // (if another block comes in, then if lower hash, it will replace this block)
            BlockConsensus oBlockConsensus = new BlockConsensus();
            oBlockConsensus.startConsensusThread();
        }
        else{

            // Replace existing block since already confirmed that this one's hash is lower.
            candidateBlock = oBlock;
        }

        // ************ END Consensus Management ***************

        return true;
    }




    public static synchronized void addCandidateBlock(){

        BlockchainUtil u = new BlockchainUtil();

        Block oBlock = candidateBlock;

        // ***************************************************************
        // ***************************************************************
        // *** Block has been validated and will now be added to chain ***
        oBlock.setPreviousBlock(oHeadBlock);
        oHeadBlock = oBlock;
        // ***************************************************************
        // ***************************************************************

        // Update miner to remove any dupe TXs.
        Miner.removeDupeTransactions(oHeadBlock);

        // If this is the miner for this block, display reward.
        if(oBlock.getMinerPublicKey().equals(Wallet.getPublicKey())){

            u.p("");
            u.p("***###***###***###***");
            u.p("###***###***###***### THIS MINER MINED BLOCK: " + oBlock.getIndex());
            u.p("***###***###***###*** MINER's REWARD RECEIVED: " + Miner.getMinerReward(oBlock.getTransactions()));
            u.p("###***###***###***###");
            u.p("");
        }


        // Update UTXOs, item owners, and item prices.
        for(Transaction oTx: oBlock.getTransactions()){

            // Inputs to remove from UTXOs.
            if(oTx.getInputs() != null) {
                for (TransactionInput oInput : oTx.getInputs()) {

                    mapUTXOs.remove(oInput.transactionOutputId);
                }
            }

            // Outputs to add to UTXOs.
            for(TransactionOutput oOutput: oTx.getOutputs()){

                mapUTXOs.put(oOutput.id, oOutput);
            }

            // Item to update in the owners map.
            if(oTx.itemIdToSender != null){
                mapItemIdToOwnersPubKey.put(oTx.itemIdToSender, oTx.sendersPublicKey);

                // Adjust price.
                Item oItem = Blockchain.getItem(oTx.itemIdToSender);
                oItem.setfPrice(oItem.getPrice() + oItem.getPrice() * oBlock.getPriceIncreaseAfterTx());
            }
        }

        // Update local wallet.
        Miner.updateWallet(oBlock);

        u.p("");
        u.p("************************************");
        u.p("***** NEW BLOCK ADDED TO CHAIN *****");
        u.p("New Block index: " + oBlock.getIndex());
        u.p("New Block  hash: " + oBlock.getHash());
        u.p("************************************");
        u.p("");

        // Return candidate back to null.
        candidateBlock = null;

        // Save updated chain to file.
        BlockchainUtil.saveBlockchainToFile(oBlockchain);
    }




    public synchronized Boolean isChainValid() {

        Block oCurrentBlock = getHeadBlock();
        Block oPreviousBlock = getHeadBlock().getPreviousBlock();

        //loop through blockchain to check hashes:
        while(oPreviousBlock != null){

            //compare registered hash and calculated hash:
            if(!oCurrentBlock.getHash().equals(oCurrentBlock.computeHash()) ){
                System.out.println("Chain not valid!!! -- Hash doesn't match block hash in block " + oCurrentBlock.getIndex());
                return false;
            }
            //compare previous hash and registered previous hash
            if(!oPreviousBlock.getHash().equals(oCurrentBlock.getPreviousHash()) ) {
                System.out.println("Chain not valid!!! -- Hash of block " + oPreviousBlock.getIndex() +
                    " doesn't match prevhash of block " + oCurrentBlock);
                return false;
            }

            // Move one block back in chain for next loop...
            // if previous is genesis block, it's previous will be null and loop will exit.
            oCurrentBlock = oPreviousBlock;
            oPreviousBlock = oPreviousBlock.getPreviousBlock();
        }

        // Chain is valid if made it this far.
        return true;
    }


    // Should only be called once upon BC startup of wallet, since wallet will adjust and track after that.
    public static float getMinerBalance(String sPublicKey){

        Block oBlock = getHeadBlock();
        float fTotal = 0;

        // Loop through all transactions and calculate balance for this public key.
        while(oBlock != null){
            for(Transaction oTx: oBlock.getTransactions()){

                if(oTx.sendersPublicKey != null) {
                    if (oTx.sendersPublicKey.equals(sPublicKey)) {
                        fTotal -= oTx.amountToRecipient;
                    }
                }

                if(oTx.recipientsPublicKey.equals(sPublicKey)){
                    fTotal += oTx.amountToRecipient;
                }
            }

            oBlock = oBlock.getPreviousBlock();
        }

        return fTotal;
    }


    public static ArrayList<String> getOwnersItems(String sPubKey){

        ArrayList<String> lstItems = new ArrayList<>();

        for(String sItemID: mapItemIdToOwnersPubKey.keySet()){

            if(mapItemIdToOwnersPubKey.get(sItemID).equals(sPubKey)){
                lstItems.add(sItemID);
            }
        }

        return lstItems;
    }


    public static ArrayList<String> getOthersItems(String sPubKey){

        ArrayList<String> lstItems = new ArrayList<>();

        for(String sItemID: mapItemIdToOwnersPubKey.keySet()){

            if(!mapItemIdToOwnersPubKey.get(sItemID).equals(sPubKey)){
                lstItems.add(sItemID);
            }
        }

        return lstItems;
    }

    public static ArrayList<TransactionInput> getNewTxInputsFromUTXOs(String sPubKey, float fAmountNeeded){

        ArrayList<TransactionInput> lstInputs = new ArrayList<>();
        float fInputsAmount = 0;
        TransactionInput oTxInput;


        for(TransactionOutput oUTXO: mapUTXOs.values()){

            if(fInputsAmount >= fAmountNeeded){
                break;
            }

            // Grab as input for next Tx if this UTXO is coins for this user and not already in mining pools.
            if(oUTXO.recipientPublicKey.equals(sPubKey)){

                // Check that not in mining pool.
                if(!Miner.checkIfUTXOAlreadyInPools(oUTXO.id)) {

                    lstInputs.add(new TransactionInput(oUTXO));
                    fInputsAmount += oUTXO.amount;
                }
            }
        }

        return lstInputs;
    }


    public static int getUTXOCount(String sPubKey){

        int iReturn = 0;

        for(TransactionOutput oUTXO: mapUTXOs.values()){

            if(oUTXO.recipientPublicKey.equals(sPubKey)){
                iReturn++;
            }
        }

        return iReturn;
    }


    public static Item getItem(String sId){

        return mapItemIdToItem.get(sId);
    }


    public static TransactionOutput getUTXO(String sTransactionID){

        return mapUTXOs.get(sTransactionID);
    }

    public static boolean containsUTXO(String sTransactionID){

        return mapUTXOs.containsKey(sTransactionID);
    }

    public static String getItemOwner(String sItemID){

        return mapItemIdToOwnersPubKey.get(sItemID);
    }


    public static void setTitle(String sTitle){Blockchain.sTitle = sTitle;}
    public static String getTitle(){return sTitle;}

    public static Block getHeadBlock() {return oHeadBlock;}

    public static synchronized void setHeadBlock(Block oBlock) {
        oHeadBlock = oBlock;
    }

    public static String getOzPublicKey() {
        return sOzPublicKey;
    }

    public static void setOzPublicKey(String sOzPublicKey) {
        Blockchain.sOzPublicKey = sOzPublicKey;
    }

    public static String getOzUsername() {
        return sOzUsername;
    }

    public static void setOzUsername(String sOzUsername) {
        Blockchain.sOzUsername = sOzUsername;
    }

    public static int getiHighestUserCount() {
        return iHighestUserCount;
    }

    public static void setiHighestUserCount(int iUserCount) {

        if(iUserCount > iHighestUserCount) {
            Blockchain.iHighestUserCount = iUserCount;
        }
    }







    public void printOutBlocks(){

        BlockchainUtil u = new BlockchainUtil();


        u.p("");
        u.p("****************************");
        u.p("***** BEGIN BLOCK LIST *****");

        Block oCurrentBlock = getHeadBlock();
        ArrayList<Block> lstBlocks = new ArrayList<>();

        while(oCurrentBlock != null) {

            lstBlocks.add(oCurrentBlock);
            oCurrentBlock = oCurrentBlock.getPreviousBlock();
        }

        for(int x= lstBlocks.size() - 1; x > -1; x--){

            oCurrentBlock = lstBlocks.get(x);

            u.p("");
            u.p("--- NEW BLOCK ---");
            u.p("Index: " + oCurrentBlock.getIndex());
            u.p("Timestamp: " + oCurrentBlock.getTimeStamp());
            u.p("Hash: " + oCurrentBlock.getHash());
            u.p("Prev Hash: " + oCurrentBlock.getPreviousHash());
            u.p("Merkle Root: " + oCurrentBlock.getMerkleRoot());
            u.p("Nonce: " + oCurrentBlock.getNonce());

            for(Transaction oTransaction: oCurrentBlock.getTransactions()){

                u.p("");
                u.p("     --- TRANSACTION ---");
                u.p("     Value: " + oTransaction.amountToRecipient);
                u.p("     Product: " + oTransaction.itemIdToSender);
                u.p("     Hash: " + oTransaction.hash);
                u.p("     Recipient: " + oTransaction.recipientsPublicKey);
                u.p("     Sender: " + oTransaction.sendersPublicKey);

            }
        }

        u.p("");
        u.p("***** END BLOCK LIST *****");
        u.p("**************************");
        u.p("");

        return;
    }
}

