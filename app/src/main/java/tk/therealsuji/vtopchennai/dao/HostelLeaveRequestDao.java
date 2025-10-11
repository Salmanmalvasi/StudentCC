package tk.therealsuji.vtopchennai.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.HostelLeaveRequest;

@Dao
public interface HostelLeaveRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(List<HostelLeaveRequest> leaveRequests);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertSingle(HostelLeaveRequest leaveRequest);

    @Query("DELETE FROM hostel_leave_requests")
    Completable deleteAll();

    @Query("SELECT * FROM hostel_leave_requests ORDER BY from_date DESC")
    Single<List<HostelLeaveRequest>> getAllLeaveRequests();

    @Query("SELECT * FROM hostel_leave_requests WHERE leave_id = :leaveId")
    Single<HostelLeaveRequest> getLeaveRequestById(String leaveId);

    @Query("SELECT * FROM hostel_leave_requests WHERE status = :status ORDER BY from_date DESC")
    Single<List<HostelLeaveRequest>> getLeaveRequestsByStatus(String status);

    @Query("SELECT * FROM hostel_leave_requests WHERE leave_type = :leaveType ORDER BY from_date DESC")
    Single<List<HostelLeaveRequest>> getLeaveRequestsByType(String leaveType);

    @Query("SELECT COUNT(*) FROM hostel_leave_requests")
    Single<Integer> getCount();

    @Query("SELECT COUNT(*) FROM hostel_leave_requests WHERE status = :status")
    Single<Integer> getCountByStatus(String status);
}
