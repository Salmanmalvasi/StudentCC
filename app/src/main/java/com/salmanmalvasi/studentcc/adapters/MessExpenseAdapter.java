package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.MessExpense;

import java.text.SimpleDateFormat;
import android.util.SparseBooleanArray;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessExpenseAdapter extends RecyclerView.Adapter<MessExpenseAdapter.ViewHolder> {

    private List<MessExpense> expenses = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final SparseBooleanArray expandedItems = new SparseBooleanArray();

    public void setExpenses(List<MessExpense> expenses) {
        this.expenses = expenses;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mess_expense, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MessExpense expense = expenses.get(position);
        boolean isExpanded = expandedItems.get(position, false);
        holder.bind(expense, isExpanded);
        
        holder.itemView.setOnClickListener(v -> {
            boolean newState = !isExpanded;
            expandedItems.put(position, newState);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView dateParams;
        TextView location;
        TextView amount;
        TextView items;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dateParams = itemView.findViewById(R.id.text_view_date);
            location = itemView.findViewById(R.id.text_view_location);
            amount = itemView.findViewById(R.id.text_view_amount);
            items = itemView.findViewById(R.id.text_view_items);
        }

        public void bind(MessExpense expense, boolean isExpanded) {
            dateParams.setText(dateFormat.format(new Date(expense.date)));
            location.setText(expense.location != null ? expense.location : "Unknown Location");
            amount.setText(String.format(Locale.getDefault(), "- ₹%.2f", expense.amount));
            items.setText(expense.itemsSummary != null ? expense.itemsSummary : "Bill scan");
            
            if (isExpanded) {
                items.setMaxLines(Integer.MAX_VALUE);
                items.setEllipsize(null);
            } else {
                items.setMaxLines(2);
                items.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }
    }
}
