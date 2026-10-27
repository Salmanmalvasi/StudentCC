package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.MessExpense;

@Dao
public interface MessExpenseDao {
    @Query("SELECT * FROM mess_expenses ORDER BY date DESC")
    Single<List<MessExpense>> getAllExpenses();

    @Query("SELECT * FROM mess_expenses ORDER BY date DESC LIMIT 1")
    Single<MessExpense> getLastExpense();

    @Query("SELECT * FROM mess_expenses WHERE date >= :starTime AND date <= :endTime ORDER BY date DESC")
    Single<List<MessExpense>> getExpensesInRange(long starTime, long endTime);

    @Insert
    Completable insert(MessExpense expense);

    @Delete
    Completable delete(MessExpense expense);

    @Query("DELETE FROM mess_expenses")
    Completable deleteAll();
}
