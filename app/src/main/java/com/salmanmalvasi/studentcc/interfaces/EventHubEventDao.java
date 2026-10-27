package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.EventHubEvent;

/**
 * DAO for EventHub events
 */
@Dao
public interface EventHubEventDao {
    
    @Query("SELECT * FROM eventhub_events ORDER BY date ASC")
    Flowable<List<EventHubEvent>> getAll();

    @Query("SELECT * FROM eventhub_events WHERE date >= date('now') ORDER BY date ASC")
    Flowable<List<EventHubEvent>> getUpcoming();

    @Query("SELECT * FROM eventhub_events WHERE isFree = 1 ORDER BY date ASC")
    Flowable<List<EventHubEvent>> getFreeEvents();

    @Query("SELECT * FROM eventhub_events WHERE category LIKE '%VITian%' AND category NOT LIKE '%Non-VITian%' ORDER BY date ASC")
    Flowable<List<EventHubEvent>> getVitianOnlyEvents();

    @Query("SELECT * FROM eventhub_events WHERE title LIKE :query OR category LIKE :query OR venue LIKE :query ORDER BY date ASC")
    Flowable<List<EventHubEvent>> search(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<EventHubEvent> events);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(EventHubEvent event);

    @Query("DELETE FROM eventhub_events")
    Completable deleteAll();

    @Query("SELECT COUNT(*) FROM eventhub_events")
    Single<Integer> getCount();

    @Query("SELECT MAX(lastUpdated) FROM eventhub_events")
    Single<Long> getLastUpdated();
}
