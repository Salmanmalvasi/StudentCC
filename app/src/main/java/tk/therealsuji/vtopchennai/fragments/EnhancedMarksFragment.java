package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.EnhancedMarksAdapter;
import tk.therealsuji.vtopchennai.models.Mark;

/**
 * Enhanced Marks Fragment for displaying marks with better organization
 */
public class EnhancedMarksFragment extends Fragment {
    private Observable<List<Mark.AllData>> markObservable;

    public static EnhancedMarksFragment newInstance(Observable<List<Mark.AllData>> markObservable) {
        EnhancedMarksFragment fragment = new EnhancedMarksFragment();
        fragment.markObservable = markObservable;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_enhanced_marks, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_marks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Observable<List<Mark.AllData>>> markObservables = new ArrayList<>();
        markObservables.add(markObservable);

        EnhancedMarksAdapter adapter = new EnhancedMarksAdapter(markObservables);
        recyclerView.setAdapter(adapter);

        return view;
    }
}
