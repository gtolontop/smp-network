package fr.smp.core.order;

import org.bukkit.Material;

public class Order {

    private final int id;
    private final String buyerUuid;
    private final String buyerName;
    private final Material itemType;
    private final int quantity;
    private final double pricePerUnit;
    private final long createdAt;
    private int fulfilledQuantity;

    public Order(int id, String buyerUuid, String buyerName, Material itemType,
                 int quantity, double pricePerUnit, long createdAt, int fulfilledQuantity) {
        this.id = id;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.itemType = itemType;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.createdAt = createdAt;
        this.fulfilledQuantity = fulfilledQuantity;
    }

    public int getId()                { return id; }
    public String getBuyerUuid()      { return buyerUuid; }
    public String getBuyerName()      { return buyerName; }
    public Material getItemType()     { return itemType; }
    public int getQuantity()          { return quantity; }
    public double getPricePerUnit()   { return pricePerUnit; }
    public long getCreatedAt()        { return createdAt; }
    public int getFulfilledQuantity() { return fulfilledQuantity; }

    public int getRemainingQuantity() { return quantity - fulfilledQuantity; }
    public double getTotalValue()     { return quantity * pricePerUnit; }
    public double getRemainingValue() { return getRemainingQuantity() * pricePerUnit; }
    public boolean isFullyFulfilled() { return fulfilledQuantity >= quantity; }

    public void setFulfilledQuantity(int q) { this.fulfilledQuantity = q; }
}
