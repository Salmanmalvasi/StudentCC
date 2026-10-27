package com.salmanmalvasi.studentcc.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import com.salmanmalvasi.studentcc.fragments.EnhancedMarksFragment;
import com.salmanmalvasi.studentcc.models.Mark;

/**
 * Enhanced Marks Pager Adapter for ViewPager2 with Fragment support
 */
public class EnhancedMarksPagerAdapter extends FragmentStateAdapter {
    private List<Observable<List<Mark.AllData>>> markObservables;

    public EnhancedMarksPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                   List<Observable<List<Mark.AllData>>> markObservables) {
        super(fragmentActivity);
        this.markObservables = markObservables;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return EnhancedMarksFragment.newInstance(markObservables.get(position));
    }

    @Override
    public int getItemCount() {
        return markObservables.size();
    }
}
