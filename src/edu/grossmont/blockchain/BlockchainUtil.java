package edu.grossmont.blockchain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.grossmont.p2p.P2PMessageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by rgillespie on 8/2/2018.
 */
public class BlockchainUtil {

    private static boolean m_bDebugMode = false;


    public String generateGuid(){

        return UUID.randomUUID().toString();
    }



    public static synchronized String generateHash(String sOriginal){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] btEncodedhash = digest.digest(
                    sOriginal.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < btEncodedhash.length; i++) {
                sb.append(Integer.toString((btEncodedhash[i] & 0xff) + 0x100,
                        16).substring(1));
            }
            return sb.toString();
        } catch (Exception ex) {
            System.out.println("Error generating hash: " + ex.getMessage());
            return null;
        }
    }


    public static String serializeWalletToJson(Wallet oWallet, boolean bPrettyFormat){

        if(bPrettyFormat){return getGsonPrettyFormat().toJson(oWallet);}
        else {return getGson().toJson(oWallet);}
    }

    public static String serializeBlockchainMessageToJson(BlockchainMessage oBlockchainMessage, boolean bPrettyFormat){

        if(bPrettyFormat){return getGsonPrettyFormat().toJson(oBlockchainMessage);}
        else {return getGson().toJson(oBlockchainMessage);}
    }

    public static String serializeBlockchainToJson(Blockchain oBlockchain, boolean bPrettyFormat){

        if(bPrettyFormat){return getGsonPrettyFormat().toJson(oBlockchain);}
        else {return getGson().toJson(oBlockchain);}
    }



    // This is for receiving a message inside P2PMessage object and turning into BlockchainMessage object.
    public static Wallet deserializeWalletFromJson(String sJson){

        try {
            // Parse into BlockchainMessage object.
            return getGson().fromJson(sJson, Wallet.class);
        }
        catch(Exception ex){
            return null;
        }
    }

    // This is for receiving a message inside P2PMessage object and turning into BlockchainMessage object.
    public static BlockchainMessage deserializeBlockchainMessageFromJson(String sJson){

        try {
            // Parse into BlockchainMessage object.
            return getGson().fromJson(sJson, BlockchainMessage.class);
        }
        catch(Exception ex){
            return null;
        }
    }

    // Used for when blockchain json is passed from another node.
    public static Blockchain deserializeBlockchainFromJson(String sJson){

        try {
            // Parse into Blockchain object.
            return getGson().fromJson(sJson, Blockchain.class);
        }
        catch(Exception ex){
            return null;
        }
    }



    public static String[] getBlockchainFiles(){

        try{
            File folder = new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();


            String[] asFiles = folder.list();

            ArrayList<String> lstFiles = new ArrayList<>();

            for(String sFile: asFiles){

                if(sFile.endsWith("_blockchain.json")){
                    lstFiles.add(sFile);
                }
            }

            return lstFiles.toArray(new String[lstFiles.size()]);
        }
        catch(Exception ex){

            System.out.println("error reading blockchain files: " + ex.getMessage());
            return null;
        }
    }


    public static String getWalletFile(String sBlockchainTitle){

        if(sBlockchainTitle == null || sBlockchainTitle.equals("null")){
            return null;
        }

        try{
            File folder = new File(Wallet.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();

            String[] asFiles = folder.list();

            for(String sFile: asFiles){

                if(sFile.equalsIgnoreCase(sBlockchainTitle + "_wallet.json")){
                    return sFile;
                }
            }

            return null;
        }
        catch(Exception ex){

            System.out.println("error reading wallet files: " + ex.getMessage());
            return null;
        }
    }


    // NOTE: To load ArrayList of custom object from json is this:
    // logs = gson.fromJson(br, new TypeToken<List<CustomObject>>(){}.getType());
    public static Blockchain loadBlockchainFromFile(String sFileName) throws Exception{
        String sFolderPath =
                new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
        BufferedReader bufferedReader = new BufferedReader(new FileReader( sFolderPath + "/" + sFileName));

        return getGson().fromJson(bufferedReader, Blockchain.class);
    }

    // NOTE: To load ArrayList of custom object from json is this:
    // logs = gson.fromJson(br, new TypeToken<List<CustomObject>>(){}.getType());
    public static Wallet loadWalletFromFile(String sFileName) throws Exception{
        String sFolderPath =
                new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
        BufferedReader bufferedReader = new BufferedReader(new FileReader( sFolderPath + "/" + sFileName));

        return getGson().fromJson(bufferedReader, Wallet.class);
    }


    public static synchronized void saveBlockchainToFile(Blockchain oBlockchain){

        // First update connected user count plus 1 for current user.
        oBlockchain.setiHighestUserCount(new P2PMessageManager().getClientsConnectedCount() + 1);

        try{
            String sFolderPath = new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                            .getParentFile().getPath();

            FileOutputStream oStream = new FileOutputStream(sFolderPath + "/" + oBlockchain.getTitle() + "_blockchain.json");
            oStream.write(serializeBlockchainToJson(oBlockchain, true).getBytes());
            oStream.close();

        }
                catch(Exception ex){
            System.out.println("error saving blockchain: " + ex.getMessage());
        }

        createItemsForSaleHTMLFile(oBlockchain);
        createWalletHTMLFile(oBlockchain);
    }


    public static void createItemsForSaleHTMLFile(Blockchain oBlockchain){

        // Update items html file.
        try{
            String sFolderPath = new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().getPath();

            FileOutputStream oStream = new FileOutputStream(sFolderPath + "/" + oBlockchain.getTitle() + "_market.html");
            StringBuilder sHtml = new StringBuilder();
            sHtml.append("<html><head></head><body>");
            sHtml.append("<table><tr><td valign='top' style='background-color:lightgray'><br />&nbsp;&nbsp;&nbsp;<b>" +
                    "<a href='" + oBlockchain.getTitle() + "_wallet.html'>Wallet</a></b>&nbsp;&nbsp;&nbsp;</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td>");
            sHtml.append("<hr /><hr /><h1>" + oBlockchain.getTitle() + " BLOCKCHAIN: MARKET</h1><hr /><hr />");

            sHtml.append("<b>Current Users: " + (new P2PMessageManager().getClientsConnectedCount() + 1) + "</b><br />");
            sHtml.append("<b>Highest User Count: " + oBlockchain.getiHighestUserCount() + "</b><br />");

            sHtml.append("<hr /><hr /><h2>ITEMS FOR SALE</h2><hr />");
            ArrayList<String> lstItems = Blockchain.getOthersItems(Wallet.getPublicKey());
            for(String sItem: lstItems){

                sHtml.append("<h3>Title: <b>" + Blockchain.getItem(sItem).getTitle() + "</b></h3>");
                sHtml.append("ID: <b>" + Blockchain.getItem(sItem).getId() + "</b><br /><br />");
                sHtml.append("Owner: <b>" + Blockchain.getItemOwner(sItem) + "</b><br /><br />");
                sHtml.append("Price: <b>" + Blockchain.getItem(sItem).getPrice() + "</b><br /><br />");
                sHtml.append("<img src='" + Blockchain.getItem(sItem).getItemString() + "'><br /><br /><hr />");
            }

            if(lstItems.size() == 0){
                sHtml.append("There are currently no items available on the market.");
            }

            sHtml.append("</td></tr></table>");
            sHtml.append("</body></html>");

            oStream.write(sHtml.toString().getBytes());
            oStream.close();
        }
        catch(Exception ex){
            System.out.println("error writing items html: " + ex.getMessage());
        }
    }



    public static void createWalletHTMLFile(Blockchain oBlockchain){

        // Update items html file.
        try{
            String sFolderPath = new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().getPath();

            FileOutputStream oStream = new FileOutputStream(sFolderPath + "/" + oBlockchain.getTitle() + "_wallet.html");
            StringBuilder sHtml = new StringBuilder();
            sHtml.append("<html><head></head><body>");
            sHtml.append("<table><tr><td valign='top' style='background-color:lightgray'><br />&nbsp;&nbsp;&nbsp;<b>" +
                    "<a href='" + oBlockchain.getTitle() + "_market.html'>Market</a></b>&nbsp;&nbsp;&nbsp;</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td>");
            sHtml.append("<hr /><hr /><h1>" + oBlockchain.getTitle() + " BLOCKCHAIN: WALLET</h1><hr /><hr />");

            sHtml.append("<h2>DETAILS</h2>");
            sHtml.append("Username: <b>" + Wallet.getsUsername() + "</b><br /><br />");
            sHtml.append("Public Key: <b>" + Wallet.getPublicKey() + "</b><br /><br />");
            sHtml.append("COINS: <b>" + Wallet.getBalance() + "</b><br /><br /><br />");


            sHtml.append("<hr /><hr /><h2>ITEMS OWNED</h2><hr />");
            ArrayList<String> lstItems = Blockchain.getOwnersItems(Wallet.getPublicKey());
            for(String sItem: lstItems){

                sHtml.append("<h3>Title: <b>" + Blockchain.getItem(sItem).getTitle() + "</b></h3>");
                sHtml.append("ID: <b>" + Blockchain.getItem(sItem).getId() + "</b><br /><br />");
                sHtml.append("Price: <b>" + Blockchain.getItem(sItem).getPrice() + "</b><br /><br />");
                sHtml.append("<img src='" + Blockchain.getItem(sItem).getItemString() + "'><br /><br /><hr />");
            }

            if(lstItems.size() == 0){
                sHtml.append("You currently own no items.");
            }

            sHtml.append("</td></tr></table>");
            sHtml.append("</body></html>");

            oStream.write(sHtml.toString().getBytes());
            oStream.close();
        }
        catch(Exception ex){
            System.out.println("error writing items html: " + ex.getMessage());
        }
    }



    public static synchronized void saveWalletToFile(Wallet oWallet){

        try{
            String sFolderPath = new File(Blockchain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().getPath();

            FileOutputStream oStream = new FileOutputStream(sFolderPath + "/" + Blockchain.getTitle() + "_wallet.json");
            oStream.write(serializeWalletToJson(oWallet, true).getBytes());
            oStream.close();

            // This code always reached on any wallet change so update html here.
            createWalletHTMLFile(Blockchain.getInstance());
            createItemsForSaleHTMLFile(Blockchain.getInstance());
        }
        catch(Exception ex){
            System.out.println("error saving wallet: " + ex.getMessage());
        }
    }


    private static Gson getGson(){

        GsonBuilder gsonBuilder  = new GsonBuilder();

        // This allows statics to be included.
        gsonBuilder.excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT);
        gsonBuilder.serializeNulls(); // otherwise will not include fields that are null.
        return gsonBuilder.disableHtmlEscaping().create();
    }


    private static Gson getGsonPrettyFormat(){

        GsonBuilder gsonBuilder  = new GsonBuilder();

        gsonBuilder.setPrettyPrinting();

        // This allows statics to be included.
        gsonBuilder.excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT);
        gsonBuilder.serializeNulls(); // otherwise will not include fields that are null.
        return gsonBuilder.disableHtmlEscaping().create();
    }


    public void p(String sMessage){
        System.out.println(sMessage);
    }


    public void p(String sMessage, boolean bDebugOnly){

        if(bDebugOnly){
            if(m_bDebugMode){
                System.out.println(sMessage);
            }
        }
        else {
            System.out.println(sMessage);
        }
    }


    public String promptUser(String sQuestion){

        // Using input dialog:
        //return JOptionPane.showInputDialog(sQuestion);

        // Using Scanner:
        System.out.print(sQuestion);
        Scanner oCommandInput = new Scanner(System.in);

        return oCommandInput.nextLine().trim();
    }


    public int promptUserForInt(String sQuestion){

//        while(true) {
//            try {
//                int iInput = Integer.parseInt(JOptionPane.showInputDialog(sQuestion));
//
//                return iInput;
//            } catch (Exception ex) {
//                JOptionPane.showMessageDialog(null, "An integer is required as input!");
//            }
//        }

        System.out.print(sQuestion);
        Scanner oCommandInput = new Scanner(System.in);

        return oCommandInput.nextInt();
    }


    public void sleep(long lMillis){

        try{
            Thread.sleep(lMillis);
        }
        catch(Exception ex){
            // do nothing.
        }
    }


    public boolean isDebugMode() {
        return m_bDebugMode;
    }

    public void setDebugMode(boolean bDebugMode) {
        m_bDebugMode = bDebugMode;
    }




    public static void main(String[] args){

        // **********************************
        // *** BEGIN Testing mining times ***
        Block oBlock1 = new Block();
        oBlock1.setDifficulty(5);
        oBlock1.setMerkleRoot("test1" + new SecureRandom().nextInt());
        long lBeginTime = System.nanoTime();
        Miner.getInstance().doProofOfWork(oBlock1);
        long lTotalTime = System.nanoTime() - lBeginTime;
        long lTotalTimeSeconds = TimeUnit.SECONDS.convert(lTotalTime, TimeUnit.NANOSECONDS);
        System.out.println("hash 1 (" + lTotalTimeSeconds + " seconds) w/ nonce - " + oBlock1.getNonce() + ": " +
                oBlock1.getHash());


        Block oBlock2 = new Block();
        oBlock2.setDifficulty(5);
        oBlock2.setMerkleRoot("test2"  + new SecureRandom().nextInt());
        lBeginTime = System.nanoTime();
        Miner.getInstance().doProofOfWork(oBlock2);
        lTotalTime = System.nanoTime() - lBeginTime;
        lTotalTimeSeconds = TimeUnit.SECONDS.convert(lTotalTime, TimeUnit.NANOSECONDS);
        System.out.println("hash 2 (" + lTotalTimeSeconds + " seconds) w/ nonce - " + oBlock2.getNonce() + ": " +
                oBlock2.getHash());


        Block oBlock3 = new Block();
        oBlock3.setDifficulty(5);
        oBlock3.setMerkleRoot("test3"  + new SecureRandom().nextInt());
        lBeginTime = System.nanoTime();
        Miner.getInstance().doProofOfWork(oBlock3);
        lTotalTime = System.nanoTime() - lBeginTime;
        lTotalTimeSeconds = TimeUnit.SECONDS.convert(lTotalTime, TimeUnit.NANOSECONDS);
        System.out.println("hash 3 (" + lTotalTimeSeconds + " seconds) w/ nonce - " + oBlock3.getNonce() + ": " +
                oBlock3.getHash());

        // ***END Testing mining times ***
        // *******************************

        System.out.println("");
        System.out.println("Comparing block hashes as this blockchain does to reach consensus between two mined blocks:");
        if(oBlock1.getHash().compareTo(oBlock2.getHash()) < 0){
            System.out.println("hash 1 is smaller than hash 2 and would win consensus.");
        }
        else{
            System.out.println("hash 2 is smaller than hash 1 and would win consensus.");
        }

        if(oBlock2.getHash().compareTo(oBlock3.getHash()) < 0){
            System.out.println("hash 2 is smaller than hash 3 and would win consensus.");
        }
        else{
            System.out.println("hash 3 is smaller than hash 2 and would win consensus.");
        }
    }
}
