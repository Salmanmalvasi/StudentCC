package tk.therealsuji.vtopchennai.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import tk.therealsuji.vtopchennai.fragments.EnhancedMarksFragment;
import tk.therealsuji.vtopchennai.models.Mark;

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
