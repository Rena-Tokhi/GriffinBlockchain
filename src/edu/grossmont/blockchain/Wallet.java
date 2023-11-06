package edu.grossmont.blockchain;

import edu.grossmont.cryptography.RsaKeyPair;
import edu.grossmont.cryptography.RsaProcessor;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rgillespie on 8/6/2018.
 */
public class Wallet {

    private static String sPrivateKey = null;
    private static String sPublicKey = null;
    private static ArrayList<String> lstItemIds;
    private static float fBalance = 0;
    private static String sUsername = null;

    // For singleton.
    private static Wallet oWallet;

    // Signifies it's been populated by items and balance from live blockchain.
    private static boolean bWalletReady = false;


    // IMPORTANT: Only one wallet should be live/used within one executable, otherwise static variables will clash.
    // Therefore, implemented as a singleton (private constructor and getInstance method).
    private Wallet(){}


    // Singleton design pattern: Create a static method to get instance.
    public static Wallet getInstance(String sBlockchainTitle, String username){

        // Not using sychronized here so that sync performance hit is only on first instance creation.
        if(oWallet == null){

            // This makes singleton safe in multi-threaded environment.
            synchronized (Wallet.class) {

                // Need to check again now that synchronized.
                if(oWallet == null) {

                    // *** Setup wallet from saved file or create new one ***

                    // NOTE: Always refresh balance and products when wallet instance is established.
                    // NOTE: Don't override keys if they are on file system for this BC.

                    // Check if wallet already exists.
                    String sWalletFileName = BlockchainUtil.getWalletFile(sBlockchainTitle);

                    try {
                        // If doesn't exist, then need to create keys and file.
                        if(sWalletFileName == null){

                            oWallet = new Wallet();

                            // Create new keys and file.
                            RsaKeyPair oKeyPair = RsaProcessor.generatePrivatePublicKeys();

                            sPublicKey = oKeyPair.publicKey;
                            sPrivateKey = oKeyPair.privateKey;

                            sUsername = username;

                            BlockchainUtil.saveWalletToFile(oWallet);
                        }
                        else {

                            oWallet = BlockchainUtil.loadWalletFromFile(sWalletFileName);
                        }
                    }
                    catch(Exception ex){
                        return null;
                    }
                }
            }
        }

        return oWallet;
    }



    // This kept as separate step than above because when creating new BC, need keys from above step,
    // then create BC, then can do below code.
    public static void initItemsAndBalance() {

        // Refresh products
        lstItemIds = new ArrayList<>();
        lstItemIds = Blockchain.getOwnersItems(sPublicKey);

        // Recalculate balance.
        fBalance = Blockchain.getMinerBalance(sPublicKey);

        // Do this before file write so that is included in file as true.
        bWalletReady = true;

        BlockchainUtil.saveWalletToFile(oWallet);
    }



    public static void addToBalance(float fAmount){

        fBalance += fAmount;

        BlockchainUtil.saveWalletToFile(oWallet);
    }



    public static ArrayList<Item> getItems(){

        ArrayList<Item> lstItems = new ArrayList<>();

        for(String sItemID: lstItemIds){

            lstItems.add(Blockchain.getItem(sItemID));
        }

        return lstItems;
    }

    public static ArrayList<Item> getMarketItems(){

        ArrayList<Item> lstItems = new ArrayList<>();

        for(String sItemID: Blockchain.getOthersItems(getPublicKey())){

            lstItems.add(Blockchain.getItem(sItemID));
        }

        return lstItems;
    }



    // Getters used here to restrict changing these at runtime.
    public static String getPrivateKey(){return sPrivateKey;}
    public static String getPublicKey(){return sPublicKey;}
    public static float getBalance(){return fBalance;}

    public static String getsUsername() {
        return sUsername;
    }

    public static void setsUsername(String sUsername) {
        Wallet.sUsername = sUsername;
    }


    // Only allows one of each key -- no dupes.
    public static synchronized void addItem(String sId){

        boolean bAlreadyExists = false;

        for(String sItemID: lstItemIds){

            if(sId.equals(sItemID)){
                bAlreadyExists = true;
            }
        }

        if(!bAlreadyExists){
            lstItemIds.add(sId);
            BlockchainUtil.saveWalletToFile(oWallet);
        }
    }


    public static synchronized void removeItem(String sId){

        lstItemIds.remove(sId);
        BlockchainUtil.saveWalletToFile(oWallet);
    }
}
