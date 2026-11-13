package com.salmanmalvasi.studentcc.fragments.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.noties.markwon.Markwon;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.helpers.ApkUpdateManager;

public class UpdateDialogFragment extends DialogFragment {
    String versionName, releaseNotes;

    public UpdateDialogFragment() {
        // Required empty public constructor
    }

    public static UpdateDialogFragment newInstance(String versionName, String releaseNotes) {
        return newInstance(versionName, releaseNotes, "", "");
    }

    public static UpdateDialogFragment newInstance(String versionName, String releaseNotes, String downloadUrl, String sha256) {
        Bundle args = new Bundle();
        UpdateDialogFragment fragment = new UpdateDialogFragment();

        args.putString("versionName", versionName);
        args.putString("releaseNotes", releaseNotes);
        args.putString("downloadUrl", downloadUrl);
        args.putString("sha256", sha256);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogFragment = inflater.inflate(R.layout.layout_dialog_update, container, false);
        Bundle args = getArguments();

        if (args != null) {
            this.versionName = args.getString("versionName", "");
            this.releaseNotes = args.getString("releaseNotes", "");
        }

        TextView description = dialogFragment.findViewById(R.id.text_view_description);
        description.setText(Html.fromHtml(this.requireContext().getString(R.string.update_message, this.versionName), Html.FROM_HTML_MODE_LEGACY));

        TextView releaseNotes = dialogFragment.findViewById(R.id.text_view_release_notes);
        Markwon markwon = Markwon.create(this.requireContext());
        markwon.setMarkdown(releaseNotes, this.releaseNotes);

        dialogFragment.findViewById(R.id.button_cancel).setOnClickListener(view -> this.dismiss());
        dialogFragment.findViewById(R.id.button_update).setOnClickListener(view -> {
            String url = args == null ? "" : args.getString("downloadUrl", "");
            if (url.isEmpty()) SettingsRepository.openDownloadPage(requireContext());
            else ApkUpdateManager.download(requireContext(), url, args.getString("sha256", ""));
            dismiss();
        });

        return dialogFragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }
}
