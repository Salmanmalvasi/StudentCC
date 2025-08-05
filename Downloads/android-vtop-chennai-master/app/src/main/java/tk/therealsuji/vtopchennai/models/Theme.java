package tk.therealsuji.vtopchennai.models;

public class Theme {
    private String name;
    private String description;
    private int themeResId;
    private int primaryColorResId;
    private int primaryContainerColorResId;

    public Theme(String name, String description, int themeResId, int primaryColorResId, int primaryContainerColorResId) {
        this.name = name;
        this.description = description;
        this.themeResId = themeResId;
        this.primaryColorResId = primaryColorResId;
        this.primaryContainerColorResId = primaryContainerColorResId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getThemeResId() {
        return themeResId;
    }

    public void setThemeResId(int themeResId) {
        this.themeResId = themeResId;
    }

    public int getPrimaryColorResId() {
        return primaryColorResId;
    }

    public void setPrimaryColorResId(int primaryColorResId) {
        this.primaryColorResId = primaryColorResId;
    }

    public int getPrimaryContainerColorResId() {
        return primaryContainerColorResId;
    }

    public void setPrimaryContainerColorResId(int primaryContainerColorResId) {
        this.primaryContainerColorResId = primaryContainerColorResId;
    }
}
