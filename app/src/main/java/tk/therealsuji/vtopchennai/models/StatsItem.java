package tk.therealsuji.vtopchennai.models;

public class StatsItem {
    private int iconResId;
    private String value;
    private String label;

    public StatsItem(int iconResId, String value, String label) {
        this.iconResId = iconResId;
        this.value = value;
        this.label = label;
    }

    public int getIconResId() {
        return iconResId;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
