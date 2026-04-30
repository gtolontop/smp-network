package fr.smp.core.order;

import java.util.Comparator;

public enum OrderSort {

    PRICE_DESC  ("Prix ↓",      (a, b) -> Double.compare(b.getPricePerUnit(),    a.getPricePerUnit())),
    PRICE_ASC   ("Prix ↑",      Comparator.comparingDouble(Order::getPricePerUnit)),
    QUANTITY_DESC("Quantité ↓", (a, b) -> Integer.compare(b.getRemainingQuantity(), a.getRemainingQuantity())),
    QUANTITY_ASC ("Quantité ↑", Comparator.comparingInt(Order::getRemainingQuantity)),
    DATE_DESC   ("Récent",      (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt())),
    DATE_ASC    ("Ancien",      Comparator.comparingLong(Order::getCreatedAt));

    private final String label;
    private final Comparator<Order> comparator;

    OrderSort(String label, Comparator<Order> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public String getLabel()                 { return label; }
    public Comparator<Order> getComparator() { return comparator; }

    public OrderSort next() {
        OrderSort[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
