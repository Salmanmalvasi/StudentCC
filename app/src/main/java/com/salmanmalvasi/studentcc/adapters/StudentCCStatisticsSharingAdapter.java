package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.salmanmalvasi.studentcc.BuildConfig;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCSharingManager;

import java.util.HashSet;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** One account-level consent control, independent of read-only statistics refresh. */
public final class StudentCCStatisticsSharingAdapter extends RecyclerView.Adapter<StudentCCStatisticsSharingAdapter.ViewHolder> {
    private final Set<ViewHolder> holders = new HashSet<>();

    public interface SharingActionRunner {
        void run(Single<StudentCCSharingManager.SharingState> action);
    }

    public StudentCCStatisticsSharingAdapter() {
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return 0; }
    @Override public int getItemCount() { return 1; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_studentcc_statistics_sharing, parent, false));
        holders.add(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.release();
        super.onViewRecycled(holder);
    }

    public void release() {
        for (ViewHolder holder : holders) holder.release();
        holders.clear();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final SwitchMaterial sharing;
        private final TextView message;
        private final View academicConsentStatus;
        private final View progress;
        private final MaterialButton shareNow;
        private final MaterialButton withdraw;
        private final MaterialButton reviewConsent;
        private final StudentCCSharingManager manager;
        private final CompositeDisposable subscriptions = new CompositeDisposable();
        private StudentCCSharingManager.SharingState state;
        private AlertDialog dialog;
        private boolean rendering;

        ViewHolder(View itemView) {
            super(itemView);
            sharing = itemView.findViewById(R.id.switch_studentcc_sharing);
            message = itemView.findViewById(R.id.text_studentcc_sharing_status);
            academicConsentStatus = itemView.findViewById(R.id.text_studentcc_sharing_academic_status);
            progress = itemView.findViewById(R.id.progress_studentcc_sharing);
            shareNow = itemView.findViewById(R.id.button_studentcc_share_now);
            withdraw = itemView.findViewById(R.id.button_studentcc_withdraw);
            reviewConsent = itemView.findViewById(R.id.button_studentcc_review_consent);
            manager = StudentCCSharingManager.getInstance(itemView.getContext());
            sharing.setOnCheckedChangeListener((button, checked) -> {
                if (rendering || state == null || state.working) return;
                rendering = true;
                sharing.setChecked(state.enabled);
                rendering = false;
                if (checked && !state.enabled) run(manager.enableSharing());
                else if (!checked && state.enabled) showWithdrawal();
            });
            shareNow.setOnClickListener(v -> {
                if (state != null && state.setupPending) run(manager.enableSharing());
                else run(manager.shareNow());
            });
            withdraw.setOnClickListener(v -> {
                if (state != null && state.setupPending) run(manager.dismissSetup());
                else if (state != null && state.removalPending) run(manager.withdraw());
                else showWithdrawal();
            });
            reviewConsent.setOnClickListener(v -> run(manager.enableSharing()));
            itemView.findViewById(R.id.button_studentcc_sharing_info).setOnClickListener(v -> {
                closeDialog();
                dialog = new MaterialAlertDialogBuilder(itemView.getContext())
                        .setTitle(R.string.studentcc_sharing_about).setMessage(R.string.studentcc_sharing_consent)
                        .setPositiveButton(R.string.got_it, null).show();
            });
        }

        void bind() {
            subscriptions.clear();
            render(manager.getState());
            subscriptions.add(manager.observeState().observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::render, error -> render(manager.getState())));
        }

        private void render(StudentCCSharingManager.SharingState state) {
            this.state = state;
            boolean configured = !BuildConfig.STUDENTCC_STATS_BASE_URL.trim().isEmpty();
            rendering = true;
            sharing.setChecked(state.enabled);
            sharing.setText(state.academicConsentPending ? R.string.studentcc_sharing_switch_totals_only
                    : R.string.studentcc_sharing_switch);
            rendering = false;
            academicConsentStatus.setVisibility(state.academicConsentPending ? View.VISIBLE : View.GONE);
            sharing.setVisibility(configured || state.enabled || state.removalPending ? View.VISIBLE : View.GONE);
            sharing.setEnabled(configured && !state.working && !state.removalPending);
            progress.setVisibility(state.working ? View.VISIBLE : View.GONE);
            shareNow.setVisibility((state.enabled || state.setupPending) && !state.removalPending ? View.VISIBLE : View.GONE);
            shareNow.setEnabled(configured && !state.working);
            shareNow.setText(state.setupPending ? R.string.studentcc_sharing_confirm
                    : state.pendingContributions > 0 || state.pendingAcademicSummary
                    ? R.string.studentcc_sharing_retry : R.string.studentcc_sharing_refresh_totals);
            reviewConsent.setVisibility(state.academicConsentPending && !state.removalPending ? View.VISIBLE : View.GONE);
            reviewConsent.setEnabled(configured && !state.working);
            withdraw.setVisibility(state.enabled || state.removalPending || state.setupPending ? View.VISIBLE : View.GONE);
            // Opting out remains available during uploads; the manager serializes removal after them.
            withdraw.setEnabled(!state.removalPending || !state.working);
            withdraw.setText(state.setupPending ? R.string.studentcc_sharing_cancel
                    : state.removalPending ? R.string.studentcc_sharing_retry_removal : R.string.studentcc_sharing_withdraw);
            if (state.removalPending) message.setText(R.string.studentcc_sharing_removal_pending);
            else if (state.working) message.setText(R.string.studentcc_sharing_working);
            else if (!configured) message.setText(R.string.studentcc_statistics_preparing);
            else if (state.setupPending) message.setText(R.string.studentcc_sharing_setup_pending);
            else if (state.message != null && !state.message.trim().isEmpty()) message.setText(state.message);
            else if (state.pendingContributions > 0) message.setText(itemView.getContext().getString(
                    R.string.studentcc_sharing_pending, state.pendingContributions));
            else message.setText(state.enabled ? state.academicConsentPending
                    ? R.string.studentcc_sharing_totals_only : R.string.studentcc_sharing_on : R.string.studentcc_sharing_off);
        }

        private void showWithdrawal() {
            closeDialog();
            dialog = new MaterialAlertDialogBuilder(itemView.getContext())
                    .setTitle(R.string.studentcc_sharing_withdraw_title)
                    .setMessage(R.string.studentcc_sharing_withdraw_message)
                    .setNegativeButton(R.string.studentcc_sharing_keep, null)
                    .setPositiveButton(R.string.studentcc_sharing_remove, (ignored, which) -> run(manager.withdraw()))
                    .show();
        }

        private void run(Single<StudentCCSharingManager.SharingState> action) {
            subscriptions.add(action.observeOn(AndroidSchedulers.mainThread()).subscribe(this::render, error -> {
                render(manager.getState());
                if (state == null || !state.removalPending) message.setText(R.string.studentcc_sharing_failed);
            }));
        }

        private void closeDialog() {
            if (dialog != null) dialog.dismiss();
            dialog = null;
        }

        void release() {
            subscriptions.clear();
            closeDialog();
        }
    }
}
