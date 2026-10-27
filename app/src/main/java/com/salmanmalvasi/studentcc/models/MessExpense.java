package com.salmanmalvasi.studentcc.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mess_expenses")
public class MessExpense {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "date")
    public long date; // Timestamp of the bill

    @ColumnInfo(name = "amount")
    public double amount;

    @ColumnInfo(name = "balance_remaining")
    public double balanceRemaining;

    @ColumnInfo(name = "items_summary")
    public String itemsSummary; // JSON or comma-separated list of items

    @ColumnInfo(name = "items_count")
    public int itemsCount;

    @ColumnInfo(name = "bill_image_path")
    public String billImagePath;

    @ColumnInfo(name = "location")
    public String location; // e.g., "PROODLE"

    public MessExpense() {
    }

    public MessExpense(long date, double amount, double balanceRemaining, String itemsSummary, int itemsCount, String billImagePath, String location) {
        this.date = date;
        this.amount = amount;
        this.balanceRemaining = balanceRemaining;
        this.itemsSummary = itemsSummary;
        this.itemsCount = itemsCount;
        this.billImagePath = billImagePath;
        this.location = location;
    }
}
