package edu.grossmont.blockchain;

/**
 * Created by rgillespie on 10/13/2018.
 */
public class BlockConsensus implements Runnable{

    private long candidateWaitTimeMillis = 20000;

    // **********************************************
    // *** BEGIN Consensus candidate block thread ***

    // This will allow time for another confirmed block to get spread through network,
    // then lowest hash wins.

    public void startConsensusThread(){

        Thread oThread = new Thread(this, "Consensus Thread.");
        oThread.start();
    }


    public void run (){

        BlockchainUtil u = new BlockchainUtil();

        u.p("[BlockConsensus] Now waiting for network consensus on candidate block: " +
                (candidateWaitTimeMillis/1000) + " seconds.");

        u.sleep(candidateWaitTimeMillis);

        u.p("[BlockConsensus] Consensus time completed -- adding block.");

        Blockchain.addCandidateBlock();
    }



    public long getCandidateWaitTimeMillis() {
        return candidateWaitTimeMillis;
    }

}
