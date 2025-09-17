package tk.therealsuji.vtopchennai.adapters;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.Timetable;

/**
 * ┬─── Timetable Hierarchy
 * ├─ {@link tk.therealsuji.vtopchennai.fragments.HomeFragment}
 * ├─ {@link TimetableAdapter}      - ViewPager2
 * ╰→ {@link TimetableItemAdapter}  - RecyclerView(Current File)
 */
public class TimetableItemAdapter extends RecyclerView.Adapter<TimetableItemAdapter.ViewHolder> {
    public static final int STATUS_PAST = 1;
    public static final int STATUS_PRESENT = 2;
    public static final int STATUS_FUTURE = 3;

    private final List<CombinedTimetableItem> timetable;
    private final int status;

    public TimetableItemAdapter(List<Timetable.AllData> originalTimetable, int status) {
        this.timetable = combineTimetableItems(originalTimetable);
        this.status = status;
    }

    // Class to represent combined timetable items
    public static class CombinedTimetableItem {
        public int slotId;
        public String startTime;
        public String endTime;
        public String courseType;
        public String courseCode;
        public String courseTitle;
        public String venue;
        public Integer attendancePercentage;
        public List<Integer> slotIds; // For combined items

        public CombinedTimetableItem(Timetable.AllData original) {
            this.slotId = original.slotId;
            this.startTime = original.startTime;
            this.endTime = original.endTime;
            this.courseType = original.courseType;
            this.courseCode = original.courseCode;
            this.courseTitle = original.courseTitle;
            this.venue = original.venue;
            this.attendancePercentage = original.attendancePercentage;
            this.slotIds = new ArrayList<>();
            this.slotIds.add(original.slotId);
        }
    }

    // Method to combine consecutive courses with the same course code
    private List<CombinedTimetableItem> combineTimetableItems(List<Timetable.AllData> originalTimetable) {
        List<CombinedTimetableItem> combinedList = new ArrayList<>();
        
        if (originalTimetable.isEmpty()) {
            return combinedList;
        }

        android.util.Log.d("TimetableAdapter", "Original timetable size: " + originalTimetable.size());
        CombinedTimetableItem currentItem = new CombinedTimetableItem(originalTimetable.get(0));
        
        for (int i = 1; i < originalTimetable.size(); i++) {
            Timetable.AllData nextItem = originalTimetable.get(i);
            
            // Check if the next item has the same course code and is consecutive
            if (currentItem.courseCode != null && 
                currentItem.courseCode.equals(nextItem.courseCode) &&
                areConsecutive(currentItem.endTime, nextItem.startTime)) {
                
                android.util.Log.d("TimetableAdapter", "Combining courses: " + currentItem.courseCode + " from " + currentItem.startTime + "-" + currentItem.endTime + " with " + nextItem.startTime + "-" + nextItem.endTime);
                // Combine the items
                currentItem.endTime = nextItem.endTime;
                currentItem.slotIds.add(nextItem.slotId);
            } else {
                // Add the current item to the list and start a new one
                combinedList.add(currentItem);
                currentItem = new CombinedTimetableItem(nextItem);
            }
        }
        
        // Add the last item
        combinedList.add(currentItem);
        
        android.util.Log.d("TimetableAdapter", "Combined timetable size: " + combinedList.size());
        return combinedList;
    }

    // Check if two time slots are consecutive (allowing small gaps)
    private boolean areConsecutive(String endTime, String startTime) {
        try {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date end = timeFormat.parse(endTime);
            Date start = timeFormat.parse(startTime);
            
            if (end != null && start != null) {
                long diffInMinutes = (start.getTime() - end.getTime()) / (1000 * 60);
                android.util.Log.d("TimetableAdapter", "Time difference between " + endTime + " and " + startTime + ": " + diffInMinutes + " minutes");
                // Consider courses consecutive if gap is 30 minutes or less (to account for break time)
                return diffInMinutes >= 0 && diffInMinutes <= 30;
            }
        } catch (Exception e) {
            android.util.Log.e("TimetableAdapter", "Error parsing times: " + endTime + " -> " + startTime, e);
        }
        return false;
    }

    @NonNull
    @Override
    public TimetableItemAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RelativeLayout timetableItem = (RelativeLayout) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.layout_item_timatable, parent, false);

        return new ViewHolder(timetableItem);
    }

    @Override
    public void onBindViewHolder(@NonNull TimetableItemAdapter.ViewHolder holder, int position) {
        holder.setTimetableItem(this.timetable.get(position), this.status);
    }

    @Override
    public int getItemCount() {
        return timetable.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private static final int MULTIPLYING_FACTOR = 5;

        RelativeLayout timetableItem;
        ProgressBar classProgress;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.timetableItem = (RelativeLayout) itemView;
            this.classProgress = this.timetableItem.findViewById(R.id.progress_bar_timetable);
        }

        public void setTimetableItem(CombinedTimetableItem timetableItem, int status) {
            ImageView courseType = this.timetableItem.findViewById(R.id.image_view_course_type);
            TextView courseCode = this.timetableItem.findViewById(R.id.text_view_course_code);
            TextView courseName = this.timetableItem.findViewById(R.id.text_view_course_name);

            // Debug: Log all available data
            android.util.Log.d("TimetableAdapter", "=== Timetable Item Data ===");
            android.util.Log.d("TimetableAdapter", "Course Code: " + timetableItem.courseCode);
            android.util.Log.d("TimetableAdapter", "Course Title: " + timetableItem.courseTitle);
            android.util.Log.d("TimetableAdapter", "Course Type: " + timetableItem.courseType);

            @DrawableRes int courseTypeId = R.drawable.ic_theory;
            if (timetableItem.courseType != null && timetableItem.courseType.equals("lab")) {
                courseTypeId = R.drawable.ic_lab;
            }
            courseType.setImageDrawable(ContextCompat.getDrawable(this.timetableItem.getContext(), courseTypeId));
            
            // Hide course code, show only course name
            courseCode.setVisibility(View.GONE);
            
            // Set course name/title as the main display
            if (timetableItem.courseTitle != null && !timetableItem.courseTitle.isEmpty() && !timetableItem.courseTitle.equals("null")) {
                courseName.setText(timetableItem.courseTitle);
                courseName.setVisibility(View.VISIBLE);
                android.util.Log.d("TimetableAdapter", "✓ Using course title: " + timetableItem.courseTitle);
            } else {
                // Fallback to course code if course title is not available
                if (timetableItem.courseCode != null && !timetableItem.courseCode.isEmpty() && !timetableItem.courseCode.equals("null")) {
                    courseName.setText(timetableItem.courseCode);
                    courseName.setVisibility(View.VISIBLE);
                    android.util.Log.d("TimetableAdapter", "⚠ Using course code fallback: " + timetableItem.courseCode);
                } else {
                    courseName.setText("Unknown Course");
                    courseName.setVisibility(View.VISIBLE);
                    android.util.Log.d("TimetableAdapter", "❌ No course data available, using fallback text");
                }
            }
            
            setTimings(timetableItem.startTime, timetableItem.endTime, status);

            float cgpa = SettingsRepository.getCGPA(this.timetableItem.getContext());
            if (cgpa < 9 && timetableItem.attendancePercentage != null && timetableItem.attendancePercentage < 75) {
                ImageView endDrawable = this.timetableItem.findViewById(R.id.image_view_failed_attendance);
                endDrawable.setImageDrawable(ContextCompat.getDrawable(this.timetableItem.getContext(), R.drawable.ic_feedback));
                DrawableCompat.setTint(
                        DrawableCompat.wrap(endDrawable.getDrawable()),
                        MaterialColors.getColor(endDrawable, R.attr.colorError)
                );
            }
            this.classProgress.setOnClickListener(view -> this.onClick(timetableItem.slotId));
        }

        private void onClick(int slotId) {
            Context context = this.timetableItem.getContext();

            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            View bottomSheetLayout = View.inflate(context, R.layout.layout_bottom_sheet_course_info, null);
            bottomSheetDialog.setContentView(bottomSheetLayout);
            bottomSheetDialog.show();

            AppDatabase appDatabase = AppDatabase.getInstance(context.getApplicationContext());
            CoursesDao coursesDao = appDatabase.coursesDao();

            coursesDao
                    .getCourse(slotId)
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleObserver<Course.AllData>() {
                        @Override
                        public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        }

                        @Override
                        public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull Course.AllData course) {
                            TextView courseTitle = bottomSheetLayout.findViewById(R.id.text_view_course_title);
                            TextView courseCode = bottomSheetLayout.findViewById(R.id.text_view_course_code);
                            TextView faculty = bottomSheetLayout.findViewById(R.id.text_view_faculty);
                            TextView venue = bottomSheetLayout.findViewById(R.id.text_view_venue);
                            TextView attendanceExcessText = bottomSheetLayout.findViewById(R.id.text_view_attendance_excess);
                            TextView attendanceText = bottomSheetLayout.findViewById(R.id.text_view_attendance);

                            Chip slot = bottomSheetLayout.findViewById(R.id.chip_slot);

                            ProgressBar attendanceProgress = bottomSheetLayout.findViewById(R.id.progress_bar_attendance);

                            courseTitle.setText(course.courseTitle);
                            courseCode.setText(course.courseCode);
                            faculty.setText(Html.fromHtml(context.getString(R.string.faculty, course.faculty), Html.FROM_HTML_MODE_LEGACY));
                            venue.setText(Html.fromHtml(context.getString(R.string.venue, course.venue), Html.FROM_HTML_MODE_LEGACY));

                            if (course.courseType.equals("lab")) {
                                slot.setChipIconResource(R.drawable.ic_lab);
                            } else {
                                slot.setChipIconResource(R.drawable.ic_theory);
                            }

                            slot.setText(course.slot);

                            if (course.attendancePercentage == null) {
                                attendanceText.setText(context.getString(R.string.na));
                                attendanceProgress.setProgress(0);
                            } else {
                                attendanceText.setText(new DecimalFormat("#'%'").format(course.attendancePercentage));
                                attendanceProgress.setProgress(course.attendancePercentage);

                                // Add click listener to toggle between percentage and attended/total format
                                attendanceText.setOnClickListener(view -> {
                                    TextView attendanceText1 = (TextView) view;

                                    if (attendanceText1.getText().toString().contains("%")) {
                                        // Switch to attended/total format
                                        attendanceText1.setText(String.format(Locale.ENGLISH, "%d/%d", course.attendanceAttended, course.attendanceTotal));
                                        // Add visual feedback
                                        attendanceText1.setAlpha(0.7f);
                                        attendanceText1.postDelayed(() -> attendanceText1.setAlpha(1.0f), 150);
                                    } else {
                                        // Switch back to percentage format
                                        attendanceText1.setText(new DecimalFormat("#'%'").format(course.attendancePercentage));
                                        // Add visual feedback
                                        attendanceText1.setAlpha(0.7f);
                                        attendanceText1.postDelayed(() -> attendanceText1.setAlpha(1.0f), 150);
                                    }
                                });

                                //
                                //  percentage = attended * 100 / total
                                //
                                //  CALCULATING POSITIVE EXCESS
                                //  (attended) * 100 / (total + x) = 75
                                //  100 * attended = 75 * total + 75 * x
                                //  x = (100 * attended - 75 * total) / 75
                                //  positiveExcess = floor(x)   <-- can afford these many days off
                                //
                                //  CALCULATING NEGATIVE EXCESS
                                //  (attended + x) * 100 / (total + x) = 75
                                //  100 * attended + 100 * x = 75 * total + 75 * x
                                //  25 * x = 75 * total - 100 * attended
                                //  x = (75 * total - 100 * attended) / 25
                                //  negativeExcess = ceil(x)    <-- requires these many extra classes
                                //
                                if (SettingsRepository.getCGPA(timetableItem.getContext()) < 9) {
                                    double attendanceExcess = 100 * course.attendanceAttended - 75 * course.attendanceTotal;

                                    if (course.attendancePercentage < 75) {
                                        // We use floor() instead of ceil() here because the argument is negative
                                        attendanceExcess = Math.floor(attendanceExcess / 25);
                                        attendanceProgress.setSecondaryProgress(75);
                                    } else {
                                        attendanceExcess = Math.floor(attendanceExcess / 75);
                                    }

                                    attendanceExcessText.setVisibility(View.VISIBLE);
                                    attendanceExcessText.setText(new DecimalFormat("+#;-#").format(attendanceExcess));

                                    if (attendanceExcess < 0) {
                                        attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorError));
                                    } else if (attendanceExcess == 0) {
                                        attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorSecondary));
                                    }
                                }
                            }

                            bottomSheetLayout.findViewById(R.id.progress_bar_loading).setVisibility(View.GONE);
                            bottomSheetLayout.findViewById(R.id.linear_layout_container).setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        }
                    });
        }

        @SuppressLint("SetTextI18n")
        private void setTimings(String startTime, String endTime, int status) {
            try {
                ((TextView) this.timetableItem.findViewById(R.id.text_view_timings)).setText(
                        SettingsRepository.getSystemFormattedTime(this.timetableItem.getContext(), startTime) +
                                " - " + SettingsRepository.getSystemFormattedTime(this.timetableItem.getContext(), endTime)
                );
            } catch (Exception ignored) {
            }

            Calendar calendarFirstHourToday = Calendar.getInstance();
            Calendar calendarLastHourToday = Calendar.getInstance();
            calendarFirstHourToday.set(Calendar.HOUR_OF_DAY, 0);
            calendarFirstHourToday.set(Calendar.MINUTE, 0);
            calendarLastHourToday.set(Calendar.HOUR_OF_DAY, 23);
            calendarLastHourToday.set(Calendar.MINUTE, 59);

            AppDatabase appDatabase = AppDatabase.getInstance(this.timetableItem.getContext().getApplicationContext());
            ExamsDao examsDao = appDatabase.examsDao();

            examsDao
                    .isExamsOngoing(calendarFirstHourToday.getTimeInMillis(), calendarLastHourToday.getTimeInMillis())
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleObserver<Boolean>() {
                        @Override
                        public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        }

                        @Override
                        public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull Boolean isOngoing) {
                            if (isOngoing) {
                                return;
                            }

                            SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

                            try {
                                Date startTimeDate = hour24.parse(startTime);
                                Date endTimeDate = hour24.parse(endTime);

                                if (startTimeDate != null && endTimeDate != null) {
                                    if (status == STATUS_PAST) {
                                        classProgress.setProgress(100);
                                    } else if (status == STATUS_PRESENT) {
                                        Date now = hour24.parse(hour24.format(Calendar.getInstance().getTime()));

                                        if (now == null) {
                                            return;
                                        }

                                        if (now.after(endTimeDate)) {
                                            classProgress.setProgress(100);
                                        } else if (now.after(startTimeDate)) {
                                            long duration = endTimeDate.getTime() - startTimeDate.getTime();
                                            long durationComplete = now.getTime() - startTimeDate.getTime();
                                            long durationPending = endTimeDate.getTime() - now.getTime();

                                            setMaxClassProgress(duration);
                                            setClassProgressComplete(durationComplete);
                                            setClassProgressPending(durationPending, 0);
                                        } else {
                                            long duration = endTimeDate.getTime() - startTimeDate.getTime();
                                            long durationPending = startTimeDate.getTime() - now.getTime();

                                            setMaxClassProgress(duration);
                                            setClassProgressPending(duration, durationPending);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        }
                    });
        }

        private void setClassProgressPending(long duration, long delay) {
            ObjectAnimator objectAnimator = ObjectAnimator.ofInt(
                    this.classProgress,
                    "progress",
                    this.classProgress.getProgress(),
                    this.classProgress.getMax()
            );
            objectAnimator.setDuration(duration);
            objectAnimator.setInterpolator(new LinearInterpolator());
            objectAnimator.setStartDelay(delay);
            objectAnimator.start();
        }

        private void setClassProgressComplete(long duration) {
            int minutes = (int) duration / (1000 * 60);
            this.classProgress.setProgress(minutes * MULTIPLYING_FACTOR, true);
        }

        private void setMaxClassProgress(long duration) {
            int minutes = (int) duration / (1000 * 60);
            this.classProgress.setMax(minutes * MULTIPLYING_FACTOR);
        }
    }
}
