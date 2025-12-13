package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.Leave;

/**
 * Data Access Object for Leave entity.
 * Provides methods to interact with leave data in Room database.
 */
@Dao
public interface LeaveDao {

    /**
     * Insert a new leave record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(Leave leave);

    /**
     * Insert multiple leave records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<Leave> leaves);

    /**
     * Update an existing leave record.
     */
    @Update
    Completable update(Leave leave);

    /**
     * Get all leave records, ordered by from_date descending (most recent first).
     */
    @Query("SELECT * FROM leave ORDER BY from_date DESC")
    Single<List<Leave>> getAll();

    /**
     * Get active leaves (approved or pending).
     */
    @Query("SELECT * FROM leave WHERE status = 'REQUEST_APPROVED' OR status = 'REQUEST_PENDING' ORDER BY from_date DESC")
    Single<List<Leave>> getActiveLeaves();

    /**
     * Get past leaves (closed, cancelled, or rejected).
     */
    @Query("SELECT * FROM leave WHERE status = 'LEAVE_CLOSED' OR status = 'REQUEST_CANCELLED' OR status = 'REJECTED' ORDER BY from_date DESC")
    Single<List<Leave>> getPastLeaves();

    /**
     * Get a specific leave by ID.
     */
    @Query("SELECT * FROM leave WHERE leave_id = :leaveId")
    Single<Leave> getById(String leaveId);

    /**
     * Get leaves by status.
     */
    @Query("SELECT * FROM leave WHERE status = :status ORDER BY from_date DESC")
    Single<List<Leave>> getByStatus(String status);

    /**
     * Get leaves within a date range.
     */
    @Query("SELECT * FROM leave WHERE from_date >= :startDate AND to_date <= :endDate ORDER BY from_date DESC")
    Single<List<Leave>> getByDateRange(String startDate, String endDate);

    /**
     * Delete a specific leave.
     */
    @Query("DELETE FROM leave WHERE leave_id = :leaveId")
    Completable delete(String leaveId);

    /**
     * Delete all leave records.
     */
    @Query("DELETE FROM leave")
    Completable deleteAll();

    /**
     * Get count of all leave records.
     */
    @Query("SELECT COUNT(*) FROM leave")
    Single<Integer> getCount();

    /**
     * Get the most recent leave.
     */
    @Query("SELECT * FROM leave ORDER BY from_date DESC LIMIT 1")
    Single<Leave> getMostRecent();
}
