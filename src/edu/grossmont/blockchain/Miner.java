package edu.grossmont.blockchain;

/**
 * Created by rgillespie on 8/2/2018.
 */
import edu.grossmont.p2p.P2PMessageManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;


// Miner listens and gathers transactions coming from network or local user,
// and then performs Proof of Work algorithm and creates a block.
// The block is sent back to network, and validated by each a consensus of nodes.
// Also, the miner receives blocks and validates them, and if enough validations, then adds to local Blockchain.
public class Miner implements Runnable {

    private static volatile ArrayList<Transaction> lstTransactionPool = new ArrayList<>();
    private static volatile boolean bTransactionPoolLocked = false;
    private static volatile ArrayList<Transaction> lstStandbyTransactionPool = new ArrayList<>();

    private static volatile boolean bAbortPoW = false;

    private static Wallet oWallet = null;


    // Singleton so that only one instance and one thread within executable.
    private static Miner oMiner;
    private Thread m_oThread;



    // IMPORTANT: Only one miner should be live/used within one executable, otherwise static variables will clash.
    // Therefore, implemented as a singleton (private constructor and getInstance method).
    private Miner() {}


    // Singleton design pattern: Create a static method to get instance.
    public static Miner getInstance(){

        // Not using sychronized here so that sync performance hit is only on first instance creation.
        if(oMiner == null){

            // This makes singleton safe in multi-threaded environment.
            synchronized (Miner.class) {

                // Need to check again now that synchronized.
                if(oMiner == null) {
                    oMiner = new Miner();
                }
            }
        }
        return oMiner;
    }



    // Transactions that come in from network or from local user.
    // NOTE: New transactions that come in that have already been included in a confirmed block
    // elsewhere on the network are not a problem because once that foreign block is added to this node's local blockchain,
    // then when this new tx is processed for a new block, the referenced outputs will be used up and tx will be discarded.
    // NOTE 2: Needs synchronization because accessed via monitor thread or main UI thread.
    public static synchronized void addTransaction(Transaction oTransaction) {

        BlockchainUtil u = new BlockchainUtil();
        boolean bAddedToPool = false;

        u.p("Miner received Tx (sent through network count: " + oTransaction.sentToNetworkCount + ")",
                true);

        // Check if tx is already in one of the local tx pools and stop if it is.
        for(Transaction oTx: lstTransactionPool){
            if(oTx.hash.equals(oTransaction.hash)){

                u.p("Received transaction discarded since it already exists in main miner pool.",
                        true);
                return;
            }
        }
        for(Transaction oTx: lstStandbyTransactionPool){
            if(oTx.hash.equals(oTransaction.hash)){
                u.p("Received transaction discarded since it already exists in standby miner pool.",
                        true);
                return;
            }
        }

        // Make sure item being purchased isn't already in a pool tx.
        for(Transaction oTx: lstTransactionPool){
            if(oTx.itemIdToSender != null && oTx.itemIdToSender.equals(oTransaction.itemIdToSender)){

                u.p("Received transaction discarded since purchased item already exists in main miner pool.",
                        true);
                return;
            }
        }
        for(Transaction oTx: lstStandbyTransactionPool){
            if(oTx.itemIdToSender != null && oTx.itemIdToSender.equals(oTransaction.itemIdToSender)){

                u.p("Received transaction discarded since purchased item already exists in standby miner pool.",
                        true);
                return;
            }
        }

        // First do full verification before adding to pool.
        if(oTransaction.verifyTransaction()) {

            // Standby Pool Add
            if (bTransactionPoolLocked) {
                if(verifyNoDupeUTXOs(oTransaction, lstStandbyTransactionPool) &&
                        verifyNoDupeUTXOs(oTransaction, lstTransactionPool)) {

                    // Add transaction to pool.
                    lstStandbyTransactionPool.add(oTransaction);
                    bAddedToPool = true;

                    u.p("Transaction verified and added to standby miner pool.", true);
                }
                else{
                    u.p("[miner] Dupe USTXO found in transaction from: " + oTransaction.sendersPublicKey,
                            true);
                    return;
                }
            }

            // Main Pool Add
            else {
                if(verifyNoDupeUTXOs(oTransaction, lstTransactionPool)) {

                    lstTransactionPool.add(oTransaction);
                    bAddedToPool = true;

                    u.p("Transaction verified and added to main miner pool.", true);
                }
                else{
                    u.p("[miner] Dupe USTXO found in transaction from: " + oTransaction.sendersPublicKey,
                            true);
                    return;
                }
            }
        }

        // Only send to network if less than minimum send count and added to pool.
        if(bAddedToPool) {

            if (oTransaction.sentToNetworkCount < oTransaction.sentToNetworkMinimum) {

                oTransaction.sentToNetworkCount++;

                u.p("Transaction being sent to other miners.");

                // *** Send off to other miners ***
                new P2PMessageManager().broadcastMessageToServers(new BlockchainMessage(oTransaction).serialize());
            }
        }
    }


    // CURRENTLY NOT BEING USED because HANDLED DIRECTLY BY BLOCKCHAIN CLASS FROM MANAGER.
    // This will be a block sent from other nodes that have confirmed,
    // So check if already enough confirms to add,
    // Or if needs to be still broadcast to network for more confirmations.
//    public static void confirmBlock(Block oBlock){
//
//        System.out.println("Miner received Block.");
//
//        boolean bAdded = Blockchain.validateAndAddBlock(oBlock);
//
//        // Check if added.
//        if(!bAdded) {
//            return;
//        }
//
//        // Block added.
//        else{
//
//            // Remove dupes is synchronized.
//            if(removeDupeTransactions(oBlock) && Miner.bTransactionPoolLocked){
//
//                // Need to abort current mining since there were dupe TXs in pool.
//                setAbortPoW(true);
//
//                if(lstStandbyTransactionPool.size() > 0){
//
//                    // Merge with existing pool so none are lost.
//                    lstTransactionPool.addAll(lstStandbyTransactionPool);
//                    lstStandbyTransactionPool = new ArrayList<>();
//                }
//            }
//        }
//    }



    // Update wallet -- this should be only place wallet is ever updated (since block has been confirmed here).
    public static void updateWallet(Block oBlock){

        // ***** BEGIN WALLET UPDATE *****
        for(Transaction oTx: oBlock.getTransactions()){

            // Check if user is sender of this tx.
            if(oTx.sendersPublicKey != null) {
                if (oTx.sendersPublicKey.equals(Wallet.getPublicKey())) {

                    // Add up UTXOs spent and subtract from balance.
                    for (TransactionInput oInput : oTx.getInputs()) {

                        Wallet.addToBalance(oInput.UTXO.amount * -1);
                    }
                }

                // Remove item if one was included and this user is the recipient of coins.
                if (oTx.itemIdToSender != null && oTx.recipientsPublicKey.equals(Wallet.getPublicKey())) {
                    Wallet.removeItem(oTx.itemIdToSender);
                }

                // Add item if one was included to this user (sender gets item since sender is purchasing w/ coins)
                if(oTx.sendersPublicKey.equals(Wallet.getPublicKey())){
                    if (oTx.itemIdToSender != null) {
                        Wallet.addItem(oTx.itemIdToSender);
                    }
                }
            }

            // Add any outputs to this user's balance.
            for(TransactionOutput oOutput: oTx.getOutputs()){

                if(oOutput.recipientPublicKey.equals(Wallet.getPublicKey())) {
                    Wallet.addToBalance(oOutput.amount);
                }
            }

        }
    }



    // Triggered when transaction size for block is reached and can begin to mine it.
    private boolean createBlockAndNetworkConfirm() {

        BlockchainUtil u = new BlockchainUtil();

        u.p("Miner forming and mining block from " + Miner.lstTransactionPool.size() + " transactions.");

        Block oBlock = new Block();

        oBlock.setMinerPublicKey(Wallet.getPublicKey());
        oBlock.setMinerUsername(Wallet.getsUsername());

        oBlock.setDifficulty(Blockchain.getHeadBlock().getDifficulty());

        oBlock.setTimeStamp(System.currentTimeMillis());


        // Increment index.
        oBlock.setIndex(Blockchain.getHeadBlock().getIndex() + 1);

        // Set previousHash with current Hash.
        oBlock.setPreviousHash(Blockchain.getHeadBlock().getHash());

        // Set block size.
        oBlock.setBlocksize(Blockchain.getHeadBlock().getBlocksize());

        // Set Price increase of items.
        oBlock.setPriceIncreaseAfterTx(Blockchain.getHeadBlock().getPriceIncreaseAfterTx());

        // Set Miner's reward.
        oBlock.setMinersRewardPercent(Blockchain.getHeadBlock().getMinersRewardPercent());

        // *** Create new clean list of TXs for block ***
        ArrayList<Transaction> lstBlockTransactions = new ArrayList<>();
        for(Transaction tx: lstTransactionPool){
            lstBlockTransactions.add(tx);
        }

        // add miner reward tx.
        lstBlockTransactions.add(createMinerRewardTx());

        // Package transactions into block.
        oBlock.setTransactions(lstBlockTransactions);

        // Set Merkle Root.
        oBlock.computeAndSetMerkleRoot();



        u.p("");
        u.p("        ***********************");
        u.p("[miner] *** BEGIN PoW MINING***");
        u.p("");
        u.p("        BLOCK #: " + oBlock.getIndex());
        u.p("");

        long lBeginPoWTime = System.nanoTime();

        // *************************************************
        // *************************************************
        // *** This is where miner mines and earns coins ***
        boolean bSuccess = doProofOfWork(oBlock);
        // *************************************************
        // *************************************************

        u.p("");
        u.p("[miner] ----- PoW elapsed time: " + TimeUnit.SECONDS.convert(System.nanoTime() - lBeginPoWTime,
                TimeUnit.NANOSECONDS) + " seconds -----");
        u.p("");

        // Clean up and send off new block if successful.
        if(bSuccess) {

            // Sleep here to make sure bAbortPoW is allowed to update in case of blocked during mining.
            u.sleep(500);

            if(bAbortPoW){
                u.p("");
                u.p("[miner] Aborted mining block, probably because another confirmed block received.");
                u.p("[miner] *** END PoW MINING ***");
                u.p("        **********************");
                u.p("");
                return false;
            }

            // Iterate to 1, so that block can be passed a limited amount of times through network.
            oBlock.iSentToNetworkCount++;

            // **************************************************
            // *** Broadcast block to network to be confirmed ***
            new P2PMessageManager().broadcastMessageToServers(new BlockchainMessage(oBlock).serialize());

            u.p("[miner] Block successfully mined -- sent to network for confirmation!!!");
            u.p("[miner] *** END PoW MINING ***");
            u.p("        **********************");
            u.p("");
        }
        else{

            u.p("");
            u.p("[miner] Aborted mining block, probably because another confirmed block received.");
            u.p("[miner] *** END PoW MINING ***");
            u.p("        **********************");
            u.p("");
        }

        return bSuccess;
    }



    public Transaction createMinerRewardTx() {

        // Add miner reward tx.
        return new Transaction(Blockchain.getTitle(), null, Wallet.getPublicKey(),
                Miner.getMinerReward(lstTransactionPool),null, null, Wallet.getPrivateKey());
    }



    // Synced because used in both this class
    // and also as a validation by Blockchain class when receiving new block from network.
    public static synchronized float getMinerReward(ArrayList<Transaction> lstTransactions){

        // Loop through all TXs and get total transfer for miner percent calc.
        float fBlockTxTotal = 0;
        for (Transaction poolTx : lstTransactions) {

            // Make sure we don't count the miner's reward tx that is included when validating a passed in block,
            // or include tx that's minting new coins.
            if(poolTx.sendersPublicKey != null) {
                fBlockTxTotal += poolTx.amountToRecipient;
            }
        }

        return Blockchain.getHeadBlock().getMinersRewardPercent() * fBlockTxTotal;
    }




    // PoW is where miner keeps trying random nonce until hash begins with as many 0s as the difficulty specifies.
    public boolean doProofOfWork(Block oBlock) {
        int targetDifficulty = oBlock.getDifficulty();
        String leadingZeros = "";

        while (leadingZeros.length() < targetDifficulty) {
            leadingZeros += "0";
        }

        int nonce = 0;
        while (true) {
            if (bAbortPoW)
            {
                bAbortPoW = false;
                System.out.println("[miner] Aborted mining block, probably because another confirmed block received.");
                return false;
            }
            else
            {
                // Attempt to find a valid nonce
                oBlock.setNonce(Integer.toString(nonce));
                oBlock.computeHash();
                oBlock.setHash(oBlock.computeHash());

                if (oBlock.getHash().startsWith(leadingZeros))
                {
                    return true;
                }
                nonce++;
            }
        }
    }



    // Check all transactions still valid...
    // This is for removing dupes after a received node's block has been confirmed and added.
    public static synchronized void removeDupeTransactions(Block oNewlyAddedBlock){

        // Need to clear transactions in main pool that are dupes of new block.
        for(int x=lstTransactionPool.size() - 1; x > -1; x--){
            for(Transaction oTxNewBlock: oNewlyAddedBlock.getTransactions()){
                if(lstTransactionPool.get(x).hash.equals(oTxNewBlock.hash)){

                    // Remove this tx since already exists newly added block.
                    lstTransactionPool.remove(x);
                    break;
                }
            }
        }

        // Need to clear transactions in standby pool that are dupes of new block.
        for(int x=lstStandbyTransactionPool.size() - 1; x > -1; x--){
            for(Transaction oTxNewBlock: oNewlyAddedBlock.getTransactions()){
                if(lstStandbyTransactionPool.get(x).hash.equals(oTxNewBlock.hash)){

                    // Remove this standby tx since already exists newly added block.
                    lstStandbyTransactionPool.remove(x);
                    break;
                }
            }
        }


        /*  Original implementation that just finds if there are any dupes or not.
        boolean bAtLeastOneDupeInCurrentPool = false;

        for (Iterator<Transaction> iterator = lstTransactionPool.iterator(); iterator.hasNext(); ) {
            Transaction tx = iterator.next();

            for(Transaction oTxFromNewlyAddedBlock: oNewlyAddedBlock.getTransactions()) {
                if (tx.hash.equals(oTxFromNewlyAddedBlock.hash)) {

                    iterator.remove();

                    // Only set in this loop because this one could possibly have been grouped into current mining block.
                    bAtLeastOneDupeInCurrentPool = true;
                    break;
                }
            }
        }

        // This is for case when block comes from other miner and this miner in the middle of mining a block.
        for (Iterator<Transaction> iterator = lstStandbyTransactionPool.iterator(); iterator.hasNext(); ) {
            Transaction tx = iterator.next();

            for(Transaction oTxFromNewlyAddedBlock: oNewlyAddedBlock.getTransactions()) {
                if (tx.hash.equals(oTxFromNewlyAddedBlock.hash)) {
                    iterator.remove();
                    break;
                }
            }
        }

        return bAtLeastOneDupeInCurrentPool;
        */
    }



    private static synchronized boolean verifyNoDupeUTXOs(Transaction oTransaction,
                                                          ArrayList<Transaction> lstTransactions){

        for(Transaction oTx: lstTransactions){
            if(oTx.getInputs() != null) {
                for (TransactionInput oInput : oTx.getInputs()) {
                    for (TransactionInput oNewInput : oTransaction.getInputs()) {

                        if (oInput.transactionOutputId.equals(oNewInput.transactionOutputId)) {

                            // Dupe source USTXO found in existing pool transactions.
                            new BlockchainUtil().p("[miner] dupe UTXO so discarding transaction.", true);
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }


    public static boolean checkIfUTXOAlreadyInPools(String sOutputId){

        // Check if UTXO is already in one of the local tx pools and return false if it is.
        for(Transaction oTx: lstTransactionPool){
            if(oTx.getInputs() != null) {
                for (TransactionInput oInput : oTx.getInputs()) {

                    if(oInput.UTXO.id.equals(sOutputId)){
                        return true;
                    }
                }
            }
        }
        for(Transaction oTx: lstStandbyTransactionPool){
            if(oTx.getInputs() != null) {
                for (TransactionInput oInput : oTx.getInputs()) {

                    if(oInput.UTXO.id.equals(sOutputId)){
                        return true;
                    }
                }
            }
        }

        return false;
    }




    public static void setAbortPoW(boolean bAbortPoW) {
        Miner.bAbortPoW = bAbortPoW;
    }


    public static void setWallet(Wallet oWallet){
        Miner.oWallet = oWallet;
    }


    public static void printTransactionPoolsSummary() {

        BlockchainUtil u = new BlockchainUtil();

        u.p("");
        u.p("***************** Transaction Pool Summary *****************");
        u.p("Main Tx Pool count   : " + lstTransactionPool.size());
        u.p("Standby Tx Pool count: " + lstStandbyTransactionPool.size());
        u.p("");

        u.p("*** Main Transaction Pool transactions ***");
        u.p("");
        for(Transaction tx: lstTransactionPool){
            u.p("Hash: " + tx.hash);
            u.p("Sender: " + tx.sendersPublicKey);
            u.p("Recipient: " + tx.recipientsPublicKey);
            u.p("Amount to Recipient: " + tx.amountToRecipient);
            u.p("Item: " + tx.itemIdToSender);
        }

        if(lstTransactionPool.size() == 0){
            u.p("Main transaction pool is empty.");
        }

        u.p("");
        u.p("*** Standby Transaction Pool transactions ***");
        u.p("");
        for(Transaction tx: lstStandbyTransactionPool){

            u.p("Hash: " + tx.hash);
            u.p("Sender: " + tx.sendersPublicKey);
            u.p("Recipient: " + tx.recipientsPublicKey);
            u.p("Amount to Recipient: " + tx.amountToRecipient);
            u.p("Item: " + tx.itemIdToSender);
        }

        if(lstStandbyTransactionPool.size() == 0){
            u.p("Standby transaction pool is empty.");
            u.p("");
        }

        u.p("***************** END Transaction Pool Summary *****************");
        u.p("");
    }





    // **************************
    // **************************
    // *** BEGIN Miner Thread ***
    // This thread will always loop in background waiting for enough transactions to block and mine.


    // This method will keep caller from having to instantiate Thread object first.
    public void start(){

        // This guarantees only one thread of this class.
        if(m_oThread == null){
            m_oThread = new Thread(this, "Miner thread started.");
            m_oThread.start();
        }
    }


    // Called as part of Runnable interface and shouldn't be called directly by code.
    public void run() {

        BlockchainUtil u = new BlockchainUtil();

        u.p("Miner thread started.");


        // *****************************
        // *** Eternal Mining Loop *****
        // Because miner always checking for next block to immediately work on.
        while(true){

            u.sleep(500);

            // Check if transaction pool full and lock if it is.
            if (lstTransactionPool.size() >= Blockchain.getHeadBlock().getBlocksize()) {

                // This will mean no more additions to this block until miner thread has mined or aborted it.
                Miner.bTransactionPoolLocked = true;

                // If last transaction that triggered mining is from this miner, then pause a bit to allow
                // propagation through network.
                if(lstTransactionPool.get(lstTransactionPool.size() - 1).sendersPublicKey != null &&
                        lstTransactionPool.get(lstTransactionPool.size() - 1).sendersPublicKey.equals(Wallet.getPublicKey())){

                    u.sleep(3000);
                }
            }

            // Only do when locked, because this means block is to be mined.
            if(Miner.bTransactionPoolLocked){

                // First scope TXs down to size block is expecting, and put extras in standby pool.
                if(Miner.lstTransactionPool.size() > Blockchain.getHeadBlock().getBlocksize()){

                    // Loop from end of list backwards to block size limit.
                    for(int x=Miner.lstTransactionPool.size() - 1; x > Blockchain.getHeadBlock().getBlocksize() - 1; x--){

                        lstStandbyTransactionPool.add(lstTransactionPool.get(x));
                        lstTransactionPool.remove(x);
                    }
                }

                // form block and mine.
                boolean bBlockMined = createBlockAndNetworkConfirm();

                if(bBlockMined) {

                    // Discard original pool since it's been added to Block now, and swap standby pool to live pool.
                    lstTransactionPool = lstStandbyTransactionPool;
                    lstStandbyTransactionPool = new ArrayList<>();
                }

                // Most likely cancelled because mined block received from network.
                else{

                    // Pause longer than consensus time so that head block is now updated to new block
                    // and false mining isn't started on TXs that will be confirmed shortly.
                    u.sleep(new BlockConsensus().getCandidateWaitTimeMillis() + 2000);

                    // Likely that mining was aborted due to block being added from network.
                    // Discarding any current pooled transactions that are in that new block will be called
                    // by addCandidateBlock just after new block is actually added to chain.
                }

                // Whether failed or not, unlock the pool.
                Miner.bTransactionPoolLocked = false;
            }
        }

    }
}
