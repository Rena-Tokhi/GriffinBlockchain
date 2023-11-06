package edu.grossmont.blockchain;

/**
 * Created by rgillespie on 8/18/2018.
 */
public class Item {

    private String sId; // GUID
    private String sTitle;
    private String sItem; // Could be link to pic or even full json object for smart contract.

    private float fPrice;

    public Item(String title, String item, float price){

        sId = new BlockchainUtil().generateGuid();
        sTitle = title;
        sItem = item;
        fPrice = price;
    }

    public String getId() {
        return sId;
    }

    public String getTitle() { return sTitle;}

    public String getItemString() {
        return sItem;
    }

    public float getPrice(){
        return fPrice;
    }

    public void setfPrice(float fPrice) {
        this.fPrice = fPrice;
    }


}
