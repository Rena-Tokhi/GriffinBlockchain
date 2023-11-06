package edu.grossmont.blockchain;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by rgillespie on 8/2/2018.
 */
public class Block {

    // *********************************
    // This section all included in hash.

    private int iIndex;
    private long lTimeStamp;
    private String sPreviousHash;
    private String sMerkleRoot;
    private int iDifficulty = 6; // Seconds in testing 6: 6,10,15,17,20,32 | 7: 12,289,218
    private float fMinersRewardPercent = .75f;
    private String sMinerPublicKey = null;
    private String sMinerUsername = null;

    private int sBlocksize = 2;
    private float fPriceIncreaseAfterTx = .25f;

    private float fCoinsToNewMiner = 10;

    private boolean bOneInstancePerNode = false;

    private String sNonce = "12345";


    // *********************************
    // This section not included in hash.

    private String sHash;

    // Order doesn't matter because generated UTXO in one TX can't be spent in another TX in same block,
    // since tx not valid until block confirmed.
    private ArrayList<Transaction> lstTransactions = new ArrayList<>();

    // Allows blockchain params change by genesis block creator (difficulty, miner's percent, blocksize, OzValid.,One instance).
    private String sChangeSignature = null;

    // Block verification tracking.
    private HashMap<String, String> mapVerifierPubKeyToSigOfHash = new HashMap<>();

    // *** This is linked list node that creates chain ***
    private Block oPreviousBlock = null;

    // This tracks how many times sent through network.
    public int iSentToNetworkCount = 0;
    public int iSentToNetworkMinimum = 2;



    public synchronized void computeAndSetMerkleRoot() {

        setMerkleRoot(computeMerkleRoot(getTxHashList()));
    }



    public synchronized boolean validateMerkleTree()  {

        String sMRoot = computeMerkleRoot(getTxHashList());
        return sMRoot.equals(this.getMerkleRoot());
    }



    private synchronized String computeMerkleRoot(ArrayList<String> lstItems) {

        int size = lstItems.size();
        if ( size < 2 || size % 2 != 0){
            return "Invalid number of items";
        }

        Queue<MerkleNode> queue = new LinkedList<>();

        for (int i = 0; i < lstItems.size(); i++) {
            MerkleNode node = new MerkleNode();
            node.sHash = BlockchainUtil.generateHash(lstItems.get(i));
            queue.offer(node);
        }
        while (queue.size() > 1) {
            for (int i = 0; i < queue.size(); i += 2) {
                MerkleNode left = queue.poll();
                MerkleNode right = queue.poll();
                MerkleNode parent = new MerkleNode();
                populateMerkleNode(parent, left, right);
                queue.offer(parent);
            }
        }
        return queue.poll().sHash;
    }



    private void populateMerkleNode
            (MerkleNode oNode, MerkleNode oLeftNode, MerkleNode oRightNode){
        oNode.oLeft = oLeftNode;
        oNode.oRight = oRightNode;
        oNode.sHash = BlockchainUtil.generateHash(oNode.oLeft.sHash + oNode.oRight.sHash);
    }



    private synchronized ArrayList<String> getTxHashList(){

        ArrayList<String> tree = new ArrayList<>();

        // addTransaction all lstTransactions as leaves of the tree.
        for (Transaction t : lstTransactions) {
            tree.add(t.hash);
        }

        // Account for miner reward transaction that is added making transactions one more than a power of two.
        // This will dupe the last tx until a power of 2 for merkle tree code.
        while((tree.size() & (tree.size() - 1)) != 0){

            tree.add(lstTransactions.get(lstTransactions.size() - 1).hash);
        }

        return tree;
    }



    // Hash this block, and hash will also be next block's previous hash.
    public synchronized String computeHash() {

        return new BlockchainUtil().generateHash(iIndex + lTimeStamp + sPreviousHash + sMerkleRoot + iDifficulty +
                fMinersRewardPercent + sMinerPublicKey + sMinerUsername + sBlocksize + fPriceIncreaseAfterTx +
                fCoinsToNewMiner + sNonce);
    }


    // Add verifying signature by miner node with sig of block hash.
    public synchronized void verifyBlock(String sPublicKey, String sSigOfBlockHash){

        mapVerifierPubKeyToSigOfHash.putIfAbsent(sPublicKey, sSigOfBlockHash);
    }



    // *******************************
    // *** BEGIN Getters & Setters ***

    public String getHash() { return sHash; }
    public synchronized void setHash(String h) {
        this.sHash = h;
    }


    public long getTimeStamp() {
        return lTimeStamp;
    }
    public synchronized void setTimeStamp(long timeStamp) {
        this.lTimeStamp = timeStamp;
    }


    public int getIndex() {
        return iIndex;
    }
    public synchronized void setIndex(int index) {
        this.iIndex = index;
    }


    public String getPreviousHash() {
        return sPreviousHash;
    }
    public synchronized void setPreviousHash(String previousHash) {
        this.sPreviousHash = previousHash;
    }


    public ArrayList<Transaction> getTransactions() {
        return lstTransactions;
    }
    public synchronized void setTransactions(ArrayList<Transaction> transactions) {
        this.lstTransactions = transactions;
    }


    public String getMerkleRoot() {
        return sMerkleRoot;
    }
    public synchronized void setMerkleRoot(String merkleRoot) { this.sMerkleRoot = merkleRoot; }

    public float getMinersRewardPercent() {
        return fMinersRewardPercent;
    }
    public void setMinersRewardPercent(float fMinersRewardPercent) {
        this.fMinersRewardPercent = fMinersRewardPercent;
    }


    public String getMinerPublicKey() {
        return sMinerPublicKey;
    }

    public String getMinerUsername() {
        return sMinerUsername;
    }

    public void setMinerPublicKey(String sMinerPublicKey) {
        this.sMinerPublicKey = sMinerPublicKey;
    }

    public void setMinerUsername(String sMinerUsername) {
        this.sMinerUsername = sMinerUsername;
    }


    public String getChangeSignature() {
        return sChangeSignature;
    }

    public void setChangeSignature(String sChangeSignature) {
        this.sChangeSignature = sChangeSignature;
    }

    //public void setDifficulty(int iDifficulty) {this.iDifficulty = iDifficulty;} // Don't allow changing of this.
    public synchronized int getDifficulty() {
        return iDifficulty;
    }
    public void setDifficulty(int iDifficulty) {
        this.iDifficulty = iDifficulty;
    }

    public int getBlocksize() {
        return sBlocksize;
    }

    public void setBlocksize(int sBlocksize) {
        this.sBlocksize = sBlocksize;
    }


    public String getNonce() {
        return sNonce;
    }
    public synchronized void setNonce(String nonce) {
        this.sNonce = nonce;
    }


    public Block getPreviousBlock() {
        return oPreviousBlock;
    }
    public void setPreviousBlock(Block oPreviousBlock) {
        this.oPreviousBlock = oPreviousBlock;
    }


    public boolean isOneInstancePerNode() {
        return bOneInstancePerNode;
    }

    public void setOneInstancePerNode(boolean bOneInstancePerNode) {
        this.bOneInstancePerNode = bOneInstancePerNode;
    }

    public float getPriceIncreaseAfterTx() {
        return fPriceIncreaseAfterTx;
    }

    public void setPriceIncreaseAfterTx(float fPriceIncreaseAfterTx) {
        this.fPriceIncreaseAfterTx = fPriceIncreaseAfterTx;
    }

    public float getCoinsToNewMiner() {
        return fCoinsToNewMiner;
    }

    public void setCoinsToNewMiner(float fCoinsToNewMiner) {
        this.fCoinsToNewMiner = fCoinsToNewMiner;
    }

}

