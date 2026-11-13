package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.interfaces.ExamsDao;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.R;

/**
 * ┬─── Staff Hierarchy
 * ├─ {@link com.salmanmalvasi.studentcc.fragments.ViewPagerFragment}
 * ├─ {@link ExamsAdapter}      - ViewPager2 (Current File)
 * ╰→ {@link ExamsItemAdapter}  - RecyclerView
 */
public class ExamsAdapter extends RecyclerView.Adapter<ExamsAdapter.ViewHolder> {
    Context applicationContext;
    List<String> examTitles;

    public ExamsAdapter(List<String> examTitles) {
        this.examTitles = examTitles;
    }

    @NonNull
    @Override
    public ExamsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        this.applicationContext = context.getApplicationContext();

        RecyclerView examView = new RecyclerView(context);
        ViewGroup.LayoutParams examViewParams = new ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        examView.setLayoutParams(examViewParams);
        examView.setLayoutManager(new LinearLayoutManager(context));
        examView.setClipToPadding(false);
        examView.setPadding(0, 0, 0, (int) (16 * context.getResources().getDisplayMetrics().density));

        return new ViewHolder(examView);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamsAdapter.ViewHolder holder, int position) {
        RecyclerView examView = (RecyclerView) holder.itemView;
        if (holder.request != null) holder.request.dispose();
        String title = examTitles.get(position);
        holder.boundTitle = title;
        examView.setAdapter(null);

        AppDatabase appDatabase = AppDatabase.getInstance(this.applicationContext);
        ExamsDao examsDao = appDatabase.examsDao();

        examsDao
                .getExams(title)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<Exam.AllData>>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        holder.request = d;
                    }

                    @Override
                    public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull List<Exam.AllData> exams) {
                        if (!title.equals(holder.boundTitle)) return;
                        examView.setAdapter(exams.isEmpty()
                                ? new EmptyStateAdapter(EmptyStateAdapter.TYPE_NO_DATA, examView.getContext().getString(R.string.exam_schedule_page_empty))
                                : new ExamsItemAdapter(exams));
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        if (!title.equals(holder.boundTitle)) return;
                        examView.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage()));
                    }
                });
    }

    @Override
    public int getItemCount() {
        return this.examTitles.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        if (holder.request != null) holder.request.dispose();
        holder.boundTitle = null;
        super.onViewRecycled(holder);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        Disposable request;
        String boundTitle;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
