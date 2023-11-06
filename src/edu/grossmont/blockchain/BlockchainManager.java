package edu.grossmont.blockchain;

import edu.grossmont.p2p.*;

import java.util.ArrayList;

/**
 * Created by rgillespie on 8/6/2018.
 */
public class BlockchainManager implements Runnable {


    // *** This is the one blockchain instance for the executable ***
    private static volatile Blockchain m_oBlockchain = null;
    private Wallet m_oWallet = null;
    private String m_sUsername;

    long m_lMonitorSleepTime = 1000;

    int m_iLocalServerPort;

    BlockchainUtil util = new BlockchainUtil();

    // Currently used for when getting initial BC from server on separate thread.
    public static volatile boolean m_bBlockchainReceived = false;
    public static volatile String m_sBlockchainReceived;
    long m_lBlockchainRequestWaitTime = 20000;

    // This is not set to true until blockchain has been vetted and user has allowed persisting to hard drive.
    public static volatile boolean m_bBlockchainLive = false;



    public BlockchainManager(){}



    public static void main(String[] args){

        new BlockchainManager().startupBlockchainPhase1();

        System.exit(0);
    }



    private void startupBlockchainPhase1() {

        P2PMessageManager oMessageManager = new P2PMessageManager();



        // *** First stand up Server.
        System.out.println();
        System.out.println("**********************************");
        System.out.println("***** P2P BLOCKCHAIN Manager *****");
        System.out.println("**********************************");
        System.out.println();

        m_sUsername = util.promptUser("What do you want your username to be? ");


        // *******************************
        // *** 6 SEPARATE THREAD TYPES ***
        // (not counting local server > remote client threads & local client > remote server threads)
        // 1. User thread:          Manage menu/UI and user commands.
        // 2. Monitor thread:       Monitors incoming messages from network (Blocks, transactions, IMs, etc.)
        //                          And can add to command queue of miner for action.
        // 3. Miner thread:         Mines blocks, manages addition of blocks to BC, tracks new transactions, etc.
        // 4. Server thread:        Listens for clients connecting and spins off ServerSession threads for each one.
        // 5. ServerSession thread: Spun off when a client connects -- one of these for each connected client.
        // 6. ClientSession thread: Created by user or in the background to connect to servers on the network.
        // *******************************


        // Start up this node's server. Includes question of what port to start on.
        startUpLocalServer();

        // Monitor Thread: Start background thread instance of this class to monitor/interact w/ network messaging in background.
        startMonitorThread();

        // Sleep a few millis to allow "server is listening on port..." print out to happen before next menu item.
        util.sleep(300);

        boolean bChosen = false;
        while (!bChosen) {

            // Create new blockchain, load, or get from network?
            String sWhichBlockchain =
                    util.promptUser("[main]: Join a server's Blockchain, use local saved Blockchain, " +
                            "or create a new Blockchain (join, saved, new)? ");


            // Get blockchain from a server.
            // Requires kicking off client thread and getting results back from monitor thread.
            if (sWhichBlockchain.equals("join")) {

                // Join a network by contacting a node and getting their serialized blockchain.
                getNodeBlockchain();

                // Enter loop here as phase 2 will be called once blockchain is received and loaded.
                int iLoops = 0;
                while(!m_bBlockchainReceived){

                    iLoops++;
                    if(iLoops * 1000 == m_lBlockchainRequestWaitTime){

                        System.out.println("Waited " + (m_lBlockchainRequestWaitTime / 1000) +
                                " seconds but did not receive blockchain back yet.");

                        // This will loop back to start join/saved/new question again.
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    }
                    catch(Exception ex){
                        // do nothing because will sleep again.
                    }
                }

                if(m_bBlockchainReceived){

                    // Load received blockchain into this node's live one.
                    m_oBlockchain = BlockchainUtil.deserializeBlockchainFromJson(m_sBlockchainReceived);
                    m_oBlockchain.setInstance();

                    // Validate blockchain.
                    if(m_oBlockchain.isChainValid()) {

                        String sContinue = "yes";

                        // Warn if downloaded BC has same name as a currently saved one because will be overwritten.
                        for(String sBlockchainFile: BlockchainUtil.getBlockchainFiles()){

                            if(sBlockchainFile.split(":")[0].equalsIgnoreCase(Blockchain.getTitle())){

                                sContinue = util.promptUser("Overwrite existing save of " +
                                        Blockchain.getTitle() + " blockchain on this computer (yes, no)?");

                                break;
                            }
                        }

                        if(sContinue.equals("yes")) {

                            // First save blockchain locally.
                            BlockchainUtil.saveBlockchainToFile(m_oBlockchain);

                            // Update or generate wallet for this BC.

                            // Create or refresh(doesn't erase existing keys if there for this BC already) wallet.
                            m_oWallet = Wallet.getInstance(Blockchain.getTitle(), m_sUsername);
                            Wallet.initItemsAndBalance();
                        }

                        bChosen = true;
                    }
                }
                else{
                    // connection timed out so allow BC question to loop again.
                }
            }


            else if (sWhichBlockchain.equals("new")) {

                String sTitle =
                        util.promptUser("What is the title of the new Blockchain? ");
                String sBlockSize =
                        util.promptUser("What block size (MUST be power of 2: 2,4,8,16, etc.)?");
                float fInitialCoinCount =
                        util.promptUserForInt("Initial coin count?");
                float  fPercentIncreaseInPrice =
                        util.promptUserForInt("Percent price increase after each transaction (10,20,50, etc.)?");
                fPercentIncreaseInPrice *= .01;



                Item oInitialItem = getNewItemFromUser(true);

                int iDifficulty =
                        util.promptUserForInt("Miner difficulty (6 recommended which averages 20-30 seconds))?");

//                boolean bOneInstancePerNode = Boolean.parseBoolean(
//                        util.promptUser("Limit only one miner instance per machine (true, false)?"));


                // Singleton pattern, so this will be one global instance.
                m_oBlockchain = Blockchain.getInstance();


                // Generate wallet for this BC so that public key can be passed into BC startup.
                m_oWallet = Wallet.getInstance(sTitle, m_sUsername);

                // ************************
                // Stand up live Blockchain
                m_oBlockchain.initBlockChain(sTitle, m_sUsername, Wallet.getPublicKey(), Integer.parseInt(sBlockSize),
                        fInitialCoinCount, oInitialItem, iDifficulty, fPercentIncreaseInPrice, false);
                // ************************

                // Now can populate wallet.
                Wallet.initItemsAndBalance();

                // Blockchain valid and ready to go, so continue on with phase 2.
                startUpBlockchainPhase2();

                bChosen = true;
            }

            // Load from local saves.
            else if (sWhichBlockchain.equals("saved")) {

                // Load local or begin new one if doesn't exist.
                System.out.println();
                System.out.println("Options:");

                String[] oBlockchains = BlockchainUtil.getBlockchainFiles();

                if (oBlockchains.length < 1) {
                    System.out.println("No blockchain files found.");
                }
                for (int x = 0; x < oBlockchains.length; x++) {

                    System.out.println("" + (x + 1) + ". " + oBlockchains[x]);
                }

                String sChoice =
                        util.promptUser("[main]: Which number do you want to load? ");

                try {
                    m_oBlockchain = BlockchainUtil.loadBlockchainFromFile(oBlockchains[(Integer.parseInt(sChoice)) - 1]);
                    m_oBlockchain.setInstance(); // Important because a singleton.
                    BlockchainUtil.createItemsForSaleHTMLFile(m_oBlockchain);

                    // Create or refresh(doesn't erase existing keys) wallet.
                    m_oWallet = Wallet.getInstance(Blockchain.getTitle(), m_sUsername);
                    Wallet.initItemsAndBalance();

                    BlockchainUtil.createWalletHTMLFile(m_oBlockchain);
                }
                catch (Exception ex) {
                    System.out.println("Problem loading file or initiating wallet values: " + ex.getMessage());
                }

                bChosen = true;
            } else {
                System.out.println("None of the choices recognized... try again...");
            }
        }


        // Blockchain valid and ready to go, so continue on with phase 2.
        startUpBlockchainPhase2();
    }



    private void startUpBlockchainPhase2(){

        // Miner Thread: Start miner thread to allow mining of blocks on separate thread
        // and message queue to be populated from above monitor thread.
        Miner.getInstance().start();
        BlockchainUtil oUtil = new BlockchainUtil();
        // Allow miner to start and print out before main menu.
        oUtil.sleep(300);
        // *******************************

        Miner.setWallet(m_oWallet);

        // This allows incoming messages to be fielded by Monitor.
        m_bBlockchainLive = true;


        // *** Everything's ready to go so begin UI menu loop ***
        manageMainMenu();
    }



    private void manageMainMenu(){

        P2PMessageManager oMessageManager = new P2PMessageManager();
        String sCommand;
        BlockchainUtil u = new BlockchainUtil();

        // This is to visually even out title block.
        String sStars = "";
        for(int x=0; x < Blockchain.getTitle().length(); x++){
            sStars += "*";
        }
        u.p("");
        u.p("");
        u.p("***********************" + sStars);
        u.p("***********************" + sStars);
        u.p("***** " + Blockchain.getTitle() + " BLOCKCHAIN *****");
        u.p("***********************" + sStars);
        u.p("***********************" + sStars);
        u.p("");




        // *********************************************
        // *********************************************
        // *** BEGIN Constant loop for UI management ***
        while(true){

            u.p("");
            u.p("---------*****************---------");
            u.p("---------*** Main Menu ***---------");
            u.p("");
            sCommand = util.promptUser("| bc | blocks | nodes | msg | market | debug | quit | ");

            // View Blockchain
            if(sCommand.equals("bc")){

                // TODO: Print out blockchain.
                u.p(BlockchainUtil.serializeBlockchainToJson(m_oBlockchain, true));
            }

            else if(sCommand.equals("blocks")){

                m_oBlockchain.printOutBlocks();
            }

            // Send basic message
            else if(sCommand.equals("msg")){

                String sIP = util.promptUser("[main]: IP to send to (all)? ");

                if(!sIP.equals("all")){
                    String sPort = util.promptUser("[main]: Port? ");
                }

                String sMessage = m_sUsername + ": " + util.promptUser("[main]: Message? ");

                BlockchainMessage oMessage = new BlockchainMessage(BlockchainMessage.MessageType.IM, sMessage);



                if(sIP.equals("all")){
                    oMessageManager.broadcastMessageToServers(
                            BlockchainUtil.serializeBlockchainMessageToJson(oMessage,false));
                }
                else{
                    //Todo: ### send to specific server.
                    oMessageManager.sendMessageToServer(
                            BlockchainUtil.serializeBlockchainMessageToJson(oMessage,false),
                            "/" + sIP);
                }

            }

            // Show all servers & clients connected.
            else if(sCommand.equals("nodes")){

                printAllNodes(oMessageManager);
            }


            // Show all servers & clients connected.
            else if(sCommand.equals("market")){

                manageMarketMenu();
            }

            // Show all messages.
            else if(sCommand.equals("debug")){

                util.p("");
                util.p("[main]: ### WARNING - DEBUG MODE NOT RECOMMENDED if more than 3 total computers in " +
                                "network because too many messages will be printed out for every transaction & " +
                                "block passed around.");
                String sDebug = util.promptUser("[main]: (CURRENTLY DEBUG = " + u.isDebugMode() +
                        ") Change to debug mode to see more background details " +
                        "like incoming transactions and blocks (on or off)? ");

                if(sDebug.equals("on")){
                    u.setDebugMode(true);
                }
                else{
                    u.setDebugMode(false);
                }
            }

            // Quit and shutdown
            else if(sCommand.equals("quit")){

                return;
            }

            // Uncertain
            else{
                System.out.println("[main] Sorry, not one of the specified choices...");
            }
        }
    }



    private void manageMarketMenu(){

        String sCommand;
        BlockchainUtil u = new BlockchainUtil();


        // *********************************************
        // *********************************************
        // *** BEGIN loop for market menu management ***

        while(true) {

            u.p("");
            u.p("---------*******************---------");
            u.p("---------*** Market Menu ***---------");
            u.p("");

            String sMenu = "| coins | my items | items | new tx | tx pool | my utxo count | back | ";
            // Add on menu choices of admin
            if(Blockchain.getOzPublicKey().equals(Wallet.getPublicKey())){
                sMenu += "admin | ";
            }
            sCommand = util.promptUser(sMenu);


            // Show all owned items.
            if (sCommand.equals("my items")) {

                for (Item oItem : Wallet.getItems()) {

                    u.p("");
                    u.p("Title: " + oItem.getTitle());
                    u.p("   ID: " + oItem.getId());
                    u.p(" Item: " + oItem.getItemString());
                    u.p("Price: " + oItem.getPrice());
                }

                if(Wallet.getItems().size() == 0){
                    u.p("");
                    u.p("You currently own no items on this blockchain.");
                }
            }

            // Show user's balance
            else if (sCommand.equals("coins")) {

                u.p("");
                u.p("Public key: " + Wallet.getPublicKey());
                u.p("Wallet balance: " + Wallet.getBalance());
            }

            // Show all servers & clients connected.
            else if (sCommand.equals("items")) {

                for(Item oItem: Wallet.getMarketItems()){

                    u.p("");
                    u.p("Title: " + oItem.getTitle());
                    u.p("   ID: " + oItem.getId());
                    u.p(" Item: " + oItem.getItemString());
                    u.p("Price: " + oItem.getPrice());
                }

                if(Wallet.getMarketItems().size() == 0){
                    u.p("");
                    u.p("There are currently no items on this blockchain for sale that you don't own.");
                }
            }

            // Make Transaction
            else if (sCommand.equals("new tx")) {

                String blockchainTitle = Blockchain.getTitle();
                String sendersPublicKey = Wallet.getPublicKey();
                String recipientsPublicKey = null;
                float amountToRecipient = 0;
                String itemIdToSender = null;
                ArrayList<TransactionInput> inputs;
                String sendersPrivateKey = Wallet.getPrivateKey();
                boolean bValid = true;


                util.p("");

                String sType = util.promptUser("[main]: Purchase item or send coins (purchase,send)? ");


                // TX of coin transfer.
                if(sType.equals("send")){

                    recipientsPublicKey = util.promptUser("[main]: Enter recipient's public key: ");
                    amountToRecipient = Float.parseFloat(util.promptUser("[main]: How much to send (your total - " +
                            Wallet.getBalance() + "): "));
                }

                // TX of purchasing an item.
                else if(sType.equals("purchase")) {
                    for(Item oItem: Wallet.getMarketItems()){

                        u.p("");
                        u.p("   ID: " + oItem.getId());
                        u.p(" Item: " + oItem.getItemString());
                        u.p("Price: " + oItem.getPrice());
                    }

                    u.p("");

                    itemIdToSender = util.promptUser("[main]: Enter Item ID? ");
                    recipientsPublicKey = Blockchain.getItemOwner(itemIdToSender);
                    if(Blockchain.getItem(itemIdToSender) == null || recipientsPublicKey.equals(Wallet.getPublicKey())){
                        u.p("Item does not exist or you already own it.");
                        bValid = false;
                    }
                    amountToRecipient = Blockchain.getItem(itemIdToSender).getPrice();
                }

                else{
                    bValid = false;
                }

                if(bValid) {
                    // Find UTXOs to cover this Tx amount total.
                    inputs = Blockchain.getNewTxInputsFromUTXOs(sendersPublicKey, amountToRecipient);

                    if(inputs == null){
                        util.p("");
                        util.p("Not enough UTXOs (Unspent Transaction Outputs) found to cover this transaction.");
                        util.p("This could be because the miner has pending transactions in current or " +
                        "standby pools referencing certain UTXOs.");
                        util.p("You can view \"tx pool\" to see all pending transactions.");
                        util.p("");
                    }

                    Transaction oTx = new Transaction(blockchainTitle, sendersPublicKey, recipientsPublicKey, amountToRecipient,
                            itemIdToSender, inputs, sendersPrivateKey);

                    // Add to miner's pool to be hopefully included in next block.
                    Miner.addTransaction(oTx);

                    util.p("This transaction has been sent to the miner -- ID: " + oTx.hash);
                }
            }

            else if(sCommand.equals("tx pool")){
                Miner.printTransactionPoolsSummary();
            }

            else if(sCommand.equals("my utxo count")){
                util.p("");
                util.p("UTXOs are unspent transaction outputs, and they represent outputs to you " +
                "from other transactions.  You can't use the same one for two different transactions in the same block.");
                util.p("You current UTXO count: " + Blockchain.getUTXOCount(Wallet.getPublicKey()));
            }

            else if(sCommand.equals("back")){
                break;
            }


            else if(sCommand.equals("admin")){

                if(Blockchain.getOzPublicKey().equals(Wallet.getPublicKey())) {
                    manageAdminMenu();
                }
                else{
                    util.p("Sorry, you're not creator of this blockchain and thus can't access admin menu.");
                }
            }
        }
    }


    private void manageAdminMenu(){

        BlockchainUtil u = new BlockchainUtil();

        while(true) {
            u.p("");
            u.p("---------******************---------");
            u.p("---------*** Admin Menu ***---------");
            u.p("");

            String sMenu = "| new item | mining difficulty | block size | item price increase | miners reward | back | ";

            String sCommand = util.promptUser(sMenu);

            if(!sCommand.equals("back")) {
                u.p("### ### WARNING ### ### WARNING ### ### ###");
                u.p("### DISCONNECT ALL MINERS ###");
                u.p("### WARNING: Any connected users will need to diconnect and reconnect after any changes here " +
                        "because new block with new values is not sent to networked miners. So please have all miners disconnect now.");
            }
            else{
                u.p("");
                u.p("### IF YOU MADE CHANGES, YOU CAN NOW HAVE MINERS RECONNECT VIA JOIN ###");
                u.p("");
            }

            try {
                if (sCommand.equals("new item")) {

                    Item oItem = getNewItemFromUser(false);

                    m_oBlockchain.addNewAdminBlock(oItem, -1, -1, -1, -1);
                }

                else if (sCommand.equals("mining difficulty")) {

                    u.p("");
                    u.p("The mining difficulty is how many leading zeroes the block hash must have " +
                                    "to complete mining.");
                    u.p("The current value is " + m_oBlockchain.getHeadBlock().getDifficulty());
                    String sDifficulty = u.promptUser("What difficulty do you want mining to achieve " +
                            "(warning: 7 and above may take tens or hundreds of minutes) (or back)? ");

                    if(!sDifficulty.equals("back")){
                        m_oBlockchain.addNewAdminBlock(null, Integer.parseInt(sDifficulty), -1, -1,
                            -1);
                    }
                }

                else if (sCommand.equals("block size")) {

                    u.p("");
                    u.p("The block size is how many transactions must be collected by a miner before mining.");
                    u.p("The current value is " + m_oBlockchain.getHeadBlock().getBlocksize());
                    String sBlocksize = u.promptUser("What size do you want blocks to be " +
                            "(MUST BE A POWER OF 2 SUCH AS 2,4,8,16,32,etc. or back)? ");

                    if(!sBlocksize.equals("back")) {
                        m_oBlockchain.addNewAdminBlock(null, -1, Integer.parseInt(sBlocksize), -1,
                                -1);
                    }
                }

                else if (sCommand.equals("item price increase")) {

                    u.p("");
                    u.p("The item price increase is a decimal value that is multiplied times the item's current price " +
                            "when an item is transacted and the item's price is increased by that much.");
                    u.p("The current value is " + m_oBlockchain.getHeadBlock().getPriceIncreaseAfterTx());
                    String sPriceIncrease = u.promptUser("What price increase decimal do you want for " +
                                    "transacted items (or back)? ");

                    if(!sPriceIncrease.equals("back")) {
                        m_oBlockchain.addNewAdminBlock(null, -1, -1, Float.parseFloat(sPriceIncrease),
                                -1);
                    }
                }

                else if (sCommand.equals("miners reward")) {

                    u.p("");
                    u.p("The miner's reward is a decimal value that is multiplied times the block's total " +
                            "amount exchanged in all transactions and given as a reward transaction to the miner.");
                    u.p("The current value is " + m_oBlockchain.getHeadBlock().getMinersRewardPercent());
                    String sMinersReward = u.promptUser("What miner's reward decimal do you want blocks " +
                                    "to have (or back)? ");

                    if(!sMinersReward.equals("back")) {
                        m_oBlockchain.addNewAdminBlock(null, -1, -1, -1,
                                Float.parseFloat(sMinersReward));
                    }
                }

                else if (sCommand.equals("back")) {
                    return;
                }
            }
            catch(Exception ex){
                u.p("Error: " + ex.getMessage());
            }
        }
    }



    private void getNodeBlockchain(){

        // Need to connect to at least one server on network manually to get blockchain.
        util.p("");
        String sServerIP =
                util.promptUser("[main]: Connect to one server to join its Blockchain network.  Server IP Address? ");

        int iServerPort = util.promptUserForInt("[main]: Server Port? ");

        BlockchainMessage oMessage = new BlockchainMessage(BlockchainMessage.MessageType.BLOCKCHAINREQUEST,
                "/" + P2PServer.getThisServerIP() + ":" + P2PServer.getThisServerPort());


        // Make one time call to server and then close.
        //String sBlockchainReply = P2PUtil.connectForOneMessage(sServerIP, iServerPort, oMessage.serialize());

        // Starts client off onto separate thread with one message to get serialized blockchain and then quit.
        runClient(sServerIP, iServerPort, oMessage.serialize());
    }



    // Start up local server.
    private void startUpLocalServer(){

        m_iLocalServerPort = new BlockchainUtil().promptUserForInt("[main]: Start up this Server on which port #? ");

        // Sends server off onto separate thread.
        runServer(m_iLocalServerPort);
    }


    private Item getNewItemFromUser(boolean bFirstItem){

        System.out.println();
        System.out.println("Products are what can be bought/sold on this network.  " +
                "For example, a title of an item and a URL to a picture of that item.");

        String sFirst = " ";

        if(bFirstItem){
            sFirst = " initial ";
        }
        String sInitialProductTitle =
                util.promptUser("Title of" + sFirst + "market item for sale?");
        String sInitialProduct =
                util.promptUser("Market item URL?");
        String sInitialProductPrice =
                util.promptUser("At what price?");

        return new Item(sInitialProductTitle, sInitialProduct, Float.parseFloat(sInitialProductPrice));
    }



    // *********************************************************************************************************
    // *** This method kicks off monitor thread of this class and is the sole controller of local blockchain ***
    public void startMonitorThread(){

        Thread oThread = new Thread(this, "Monitor Thread.");
        oThread.start();
    }



    // Called as part of Runnable interface and shouldn't be called directly by code.
    public void run() {

        P2PMessageManager oMessageManager = new P2PMessageManager();
        P2PMessage oIncomingMessage;

        BlockchainUtil u = new BlockchainUtil();

        // Monitor incoming messages.
        while(true){

            // Loop through all messages and break if queue is empty then sleep before returning to this loop.
            String sMessage = null;
            while(true){

                oIncomingMessage = oMessageManager.getIncomingMessageObject();

                if(oIncomingMessage == null){
                    break;
                }

                u.p("", true);
                u.p("[monitor] Incoming message (" + oIncomingMessage.getSessionGuid() + "): " +
                        oIncomingMessage.getMessage(), true);
                u.p("", true);


                // Need to parse package into BlockchainMessage type.
                BlockchainMessage oMessage = BlockchainMessage.deserialize(oIncomingMessage.getMessage());

                // Now handle appropriate Blockchain, Block, Transaction, etc. depending on type.

                // Received request for this server's blockchain.
                if(oMessage.getMessageType() == BlockchainMessage.MessageType.BLOCKCHAINREQUEST){

                    // First create message object.
                    BlockchainMessage oBCMessageReply =
                            new BlockchainMessage(BlockchainMessage.MessageType.BLOCKCHAIN,
                                    BlockchainUtil.serializeBlockchainToJson(m_oBlockchain, false));

                    // Then send serialized version of message.
                    oMessageManager.sendMessageToServer( oBCMessageReply.serialize(), oMessage.getMessage());
                }

                // Received requested blockchain from a server.
                else if(oMessage.getMessageType() == BlockchainMessage.MessageType.BLOCKCHAIN){

                    m_sBlockchainReceived = oMessage.getMessage();
                    m_bBlockchainReceived = true;
                }

                // Received a confirmed/mined Block from a server.
                else if(oMessage.getMessageType() == BlockchainMessage.MessageType.BLOCK){

                    Blockchain.validateAndAddBlock(oMessage.getBlock());
                    // TODO: ***********************
                    // TODO: If blockchain should be replaced by one from network because this block's prev block ID
                    // TODO: is different or this block is two blocks ahead, need to go back up to menu join choice.
                }

                // Received a transaction from a server to be added to next block.
                else if(oMessage.getMessageType() == BlockchainMessage.MessageType.TRANSACTION){

                    Miner.addTransaction(oMessage.getTransaction());
                }

                // Received IM from another server.
                else if(oMessage.getMessageType() == BlockchainMessage.MessageType.IM){

                    u.p("");
                    u.p("[IM] " + oMessage.getMessage());
                    u.p("");
                }

                // Only go further if blockchain set for live (done after miner launched).
                else if(m_bBlockchainLive){

                    //Todo: ###############################
                    //Todo: ### Handle incoming message ###
                    //Todo: ###############################
                }
            }

            // Timeout so not eating up cycles.
            u.sleep(m_lMonitorSleepTime);
        }
    }



    private void runServer(int iListeningPort){

        P2PServer oServer = new P2PServer(iListeningPort);
        oServer.start();
    }



    private void runClient(String sIP, int iPort, String sInitialMessage){

        P2PClientSession oClient2 = new P2PClientSession(sIP, iPort, m_iLocalServerPort, sInitialMessage);

        oClient2.start();
    }



    private void printAllNodes(P2PMessageManager oMessageManager){

        int iClients = 0;
        int iServers = 0;

        String sServerNodes = oMessageManager.getServerGuidsDelimited();
        String sClientNodes = oMessageManager.getClientGuidsDelimited();

        // First group by IP Address ports.


        System.out.println();
        System.out.println("All connected nodes :");
        System.out.println("-- IP:Port --       | C | S |");
        if(!sServerNodes.equals("")) {
            String[] asNodes = sServerNodes.split(",");
            iServers = asNodes.length;
            if (asNodes.length > 0) {
                for (String sNode : asNodes) {
                    System.out.println(sNode + "  |   | X |");
                }
            }
        }

        if(!sClientNodes.equals("")) {
            String[] asNodes = sClientNodes.split(",");
            iClients = asNodes.length;
            if (asNodes.length > 0) {
                for (String sNode : asNodes) {
                    System.out.println(sNode + " | X |   |");
                }
            }
        }

        System.out.println();
        System.out.println("Clients: " + iClients);
        System.out.println("Servers: " + iServers);
        System.out.println();
    }
}
