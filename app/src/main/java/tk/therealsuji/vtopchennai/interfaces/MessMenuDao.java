package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.MessMenu;

@Dao
public interface MessMenuDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<MessMenu> menus);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(MessMenu menu);

    @Update
    Completable update(MessMenu menu);

    @Delete
    Completable delete(MessMenu menu);

    @Query("SELECT * FROM mess_menus WHERE mess_type = :messType ORDER BY CASE day WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6 WHEN 'Sunday' THEN 7 END")
    Single<List<MessMenu>> getMenusByType(String messType);

    @Query("SELECT * FROM mess_menus WHERE mess_type = :messType AND day = :day")
    Single<MessMenu> getMenuByTypeAndDay(String messType, String day);

    @Query("DELETE FROM mess_menus WHERE mess_type = :messType")
    Completable deleteByType(String messType);

    @Query("DELETE FROM mess_menus")
    Completable deleteAll();

    @Query("SELECT COUNT(*) FROM mess_menus WHERE mess_type = :messType")
    Single<Integer> getCountByType(String messType);

    @Query("SELECT DISTINCT mess_type FROM mess_menus")
    Single<List<String>> getAllMessTypes();
}
