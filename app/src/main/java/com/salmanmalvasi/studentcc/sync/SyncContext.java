package com.salmanmalvasi.studentcc.sync;

import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;

/**
 * Shared runtime context for a synchronization run.
 * Contains authenticated tokens, cookies, the HTTP client, and staged data.
 */
public class SyncContext {
    private String csrfToken;
    private String authorizedID;
    private String semesterID;
    private String cookieHeader;

    private final OkHttpClient okHttpClient;
    private final StagedSyncData stagedData;
    private final SyncMetrics metrics;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public SyncContext(OkHttpClient okHttpClient, String semesterID, SyncMetrics metrics) {
        this.okHttpClient = okHttpClient;
        this.stagedData = new StagedSyncData(semesterID);
        this.metrics = metrics != null ? metrics : new SyncMetrics();
        this.semesterID = semesterID;
    }

    public synchronized void setSessionTokens(String csrfToken, String authorizedID, String cookieHeader) {
        this.csrfToken = csrfToken;
        this.authorizedID = authorizedID;
        this.cookieHeader = cookieHeader;
    }

    public synchronized String getCsrfToken() {
        return csrfToken;
    }

    public synchronized String getAuthorizedID() {
        return authorizedID;
    }

    public synchronized String getSemesterID() {
        return semesterID;
    }

    public synchronized void setSemesterID(String semesterID) {
        this.semesterID = semesterID;
    }

    public synchronized String getCookieHeader() {
        return cookieHeader;
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    public StagedSyncData getStagedData() {
        return stagedData;
    }

    public SyncMetrics getMetrics() {
        return metrics;
    }

    public void cancel() {
        isCancelled.set(true);
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }
}
