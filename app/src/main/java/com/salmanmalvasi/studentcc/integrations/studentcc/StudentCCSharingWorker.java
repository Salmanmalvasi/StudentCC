package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Retries only pending work for the active account and the configured origin. */
public final class StudentCCSharingWorker extends Worker {
    public StudentCCSharingWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        try {
            boolean retry = StudentCCSharingManager.getInstance(getApplicationContext()).retryPending().blockingGet();
            return retry && getRunAttemptCount() < 5 ? Result.retry() : Result.success();
        } catch (RuntimeException error) { return Result.success(); }
    }
}
