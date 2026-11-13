package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

/** Opt-in account/origin sharing with a persistent latest-value queue and serialized removal. */
public final class StudentCCSharingManager {
    public static final class SharingState {
        public final boolean enabled, working, removalPending, setupPending, academicConsentPending, pendingAcademicSummary;
        public final int pendingContributions;
        public final String message;
        SharingState(boolean enabled, boolean working, boolean removalPending, int pending, String message,
                     boolean setupPending, boolean academicConsentPending, boolean pendingAcademicSummary) {
            this.enabled = enabled; this.working = working; this.removalPending = removalPending;
            this.pendingContributions = pending; this.message = message;
            this.setupPending = setupPending; this.academicConsentPending = academicConsentPending;
            this.pendingAcademicSummary = pendingAcademicSummary;
        }
    }
    private static volatile StudentCCSharingManager instance;
    private static final String RETRY_WORK = "studentcc_statistics_sharing_retry";
    private final Context context;
    private final StudentCCStatisticsRepository repository;
    private final StudentCCSharingStore store;
    private final SharedPreferences preferences, identities;
    private final Scheduler serial = Schedulers.from(Executors.newSingleThreadExecutor());
    private final AtomicInteger working = new AtomicInteger();
    private final Subject<SharingState> states;
    private volatile String message = "";

    public static StudentCCSharingManager getInstance(Context context) {
        if (instance == null) synchronized (StudentCCSharingManager.class) {
            if (instance == null) instance = new StudentCCSharingManager(context.getApplicationContext());
        }
        return instance;
    }
    private StudentCCSharingManager(Context context) {
        this.context = context; repository = StudentCCStatisticsRepository.getInstance(context);
        preferences = context.getSharedPreferences("studentcc_sharing_outbox_v1", Context.MODE_PRIVATE);
        store = new StudentCCSharingStore(preferences);
        SharedPreferences encrypted = null;
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            encrypted = EncryptedSharedPreferences.create(context, "studentcc_sharing_identity_v1", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception ignored) { /* Sharing remains disabled if a durable identity cannot be created. */ }
        identities = encrypted;
        states = BehaviorSubject.createDefault(getState()).toSerialized();
    }
    private static final class AccountScope {
        final String owner, endpoint, key, semester;
        AccountScope(String owner, String endpoint, String semester) {
            this.owner = owner; this.endpoint = endpoint; this.semester = semester;
            key = owner.isEmpty() || endpoint.isEmpty() ? "" : "account_" + digest(owner + "\n" + endpoint);
        }
    }
    private AccountScope active() {
        String username = SettingsRepository.getAuthorizedUsername(context);
        SharedPreferences settings = SettingsRepository.getSharedPreferences(context);
        String semester = StudentCCSemesterResolver.resolve(settings.getString("semester", ""),
                settings.getString("semestersMapJson", ""), settings.getString("semesterSubId", ""));
        return new AccountScope(username.isEmpty() ? "" : digest(username), repository.client().backendOrigin(),
                semester == null ? "" : semester.trim());
    }
    public SharingState getState() {
        return stateFor(active());
    }

    /** Lets the sign-in form show an account's saved choice without changing the active account. */
    public SharingState getStateForAccount(String username) {
        SharedPreferences settings = SettingsRepository.getSharedPreferences(context);
        String semester = StudentCCSemesterResolver.resolve(settings.getString("semester", ""),
                settings.getString("semestersMapJson", ""), settings.getString("semesterSubId", ""));
        String account = username == null ? "" : username.trim().toUpperCase(Locale.US);
        return stateFor(new AccountScope(account.isEmpty() ? "" : digest(account), repository.client().backendOrigin(),
                semester == null ? "" : semester.trim()));
    }

    private SharingState stateFor(AccountScope scope) {
        StudentCCSharingStore.Record record = store.read(scope.key);
        boolean removal = record.removalPending;
        if (!scope.owner.isEmpty()) for (String key : store.keysForOwner(scope.owner)) removal |= store.read(key).removalPending;
        boolean noPriorChoice = !record.present && !scope.key.isEmpty() && store.keysForOwner(scope.owner).isEmpty();
        return new SharingState(!scope.key.isEmpty() && record.enabled, working.get() > 0, removal,
                record.pending.size(), message, noPriorChoice && !removal,
                record.enabled && record.consentVersion < 3, record.pendingAcademicSummary != null);
    }
    public Observable<SharingState> observeState() {
        return Observable.defer(() -> { states.onNext(getState()); return states.hide(); });
    }

    /** The caller must display and receive explicit consent before invoking this method. */
    public Single<SharingState> enableSharing() {
        AccountScope scope = active();
        if (scope.owner.isEmpty()) return immediate("Sign in to share this account’s course totals.");
        if (scope.key.isEmpty()) return immediate("The StudentCC pool is being prepared.");
        try {
            if (store.read(scope.key).removalPending) return immediate("Finish removing your previous contributions before sharing again.");
            alias(scope.key, true);
            store.enable(scope.key, scope.owner, scope.endpoint, 3);
            message = ""; publish();
            return operation(scope, true);
        } catch (RuntimeException error) { return immediate("Sharing settings could not be saved. Please try again."); }
    }
    /** A deliberate refresh can restore verified submissions after the backend pool is reset. */
    public Single<SharingState> shareNow() { return operation(active(), true, true); }

    /** A disclosed first-use selection can be declined without enrolling or sending a removal request. */
    public Single<SharingState> dismissSetup() {
        AccountScope scope = active();
        if (scope.key.isEmpty()) return immediate("Sign in to save this sharing choice.");
        StudentCCSharingStore.Record existing = store.read(scope.key);
        if (existing.enabled || existing.removalPending) return immediate("Use stop sharing to remove existing shared data.");
        try {
            store.dismissSetup(scope.key, scope.owner, scope.endpoint);
            return immediate("Sharing is off. Your choice has been saved.");
        } catch (RuntimeException failure) {
            return immediate("Your sharing choice could not be saved. Please try again.");
        }
    }

    /** Called only after a successful authoritative VTOP CGPA response for this exact account. */
    public void recordVerifiedCgpa(String expectedAccount, Double cgpa) {
        AccountScope scope = active();
        String account = expectedAccount == null ? "" : expectedAccount.trim().toUpperCase(Locale.US);
        Boolean threshold = StudentCCAcademicSummary.threshold(cgpa);
        if (account.isEmpty() || !digest(account).equals(scope.owner) || threshold == null) return;
        preferences.edit().putString("verified_cgpa_owner", scope.owner)
                .putBoolean("verified_cgpa_at_least_nine", threshold)
                .putLong("verified_cgpa_at", System.currentTimeMillis()).commit();
    }

    /** A successful academic-data sync can retry threshold-only sharing without blessing cached marks. */
    public void onAcademicDataSyncCompleted(String expectedAccount) {
        AccountScope scope = active();
        String account = expectedAccount == null ? "" : expectedAccount.trim().toUpperCase(Locale.US);
        if (account.isEmpty() || !digest(account).equals(scope.owner)) return;
        StudentCCSharingStore.Record record = store.read(scope.key);
        if (record.enabled && record.consentVersion >= 2) operation(scope, true).subscribe(ignored -> {}, ignored -> {});
    }

    private Boolean academicThreshold(AccountScope scope) {
        java.util.Map<String, ?> proof = preferences.getAll();
        Object flag = proof.get("verified_cgpa_at_least_nine"), owner = proof.get("verified_cgpa_owner"), at = proof.get("verified_cgpa_at");
        return StudentCCAcademicSummary.verifiedThreshold(scope.owner,
                owner instanceof String ? (String) owner : "", flag instanceof Boolean ? (Boolean) flag : null,
                at instanceof Long ? (Long) at : 0, System.currentTimeMillis());
    }

    /** Consent and all queued uploads are disabled before the asynchronous deletion begins. */
    public Single<SharingState> withdraw() {
        AccountScope scope = active();
        if (scope.owner.isEmpty()) return immediate("Sign in to manage this account’s sharing settings.");
        try {
            List<String> keys = store.keysForOwner(scope.owner);
            if (!scope.key.isEmpty() && !keys.contains(scope.key)) keys.add(scope.key);
            for (String key : keys) {
                StudentCCSharingStore.Record record = store.read(key);
                store.withdraw(key, scope.owner, record.endpoint.isEmpty() ? scope.endpoint : record.endpoint);
            }
            message = "Sharing stopped. Removing your contributions…"; publish();
            return operation(scope, false);
        } catch (RuntimeException error) { return immediate("Sharing settings could not be saved. Please try again."); }
    }
    public void onSyncCompleted() {
        onSyncCompleted(SettingsRepository.getAuthorizedUsername(context), active().semester);
    }
    /** A late sync callback may only bless the account and semester that it actually refreshed. */
    public void onSyncCompleted(String expectedAccount, String expectedSemester) {
        AccountScope scope = active();
        String account = expectedAccount == null ? "" : expectedAccount.trim().toUpperCase(Locale.US);
        if (account.isEmpty() || !digest(account).equals(scope.owner) || expectedSemester == null
                || !expectedSemester.trim().equals(scope.semester) || scope.semester.isEmpty()) return;
        preferences.edit().putString("verified_snapshot_owner", scope.owner)
                .putString("verified_snapshot_semester", scope.semester).commit();
        StudentCCSharingStore.Record record = store.read(scope.key);
        if (record.enabled || record.removalPending) operation(scope, true).subscribe(ignored -> {}, ignored -> {});
    }
    private Single<SharingState> operation(AccountScope scope, boolean collect) {
        return operation(scope, collect, false);
    }
    private Single<SharingState> operation(AccountScope scope, boolean collect, boolean resend) {
        return Single.fromCallable(() -> {
            try {
                if (resend && sameActive(scope)) store.clearUploadAcknowledgements(scope.key, scope.owner, scope.endpoint);
                flush(scope, collect);
            }
            catch (Throwable error) {
                boolean removal = getState().removalPending;
                message = failureMessage(error, removal);
                if (temporary(error)) scheduleRetry();
            }
            return getState();
        }).subscribeOn(serial).doOnSubscribe(ignored -> { working.incrementAndGet(); publish(); })
                .doFinally(() -> { working.decrementAndGet(); publish(); });
    }
    private void flush(AccountScope scope, boolean collect) throws Exception {
        if (scope.owner.isEmpty()) return;
        boolean removed = false;
        for (String key : store.keysForOwner(scope.owner)) {
            StudentCCSharingStore.Record pending = store.read(key);
            if (!pending.removalPending) continue;
            StudentCCStatisticsClient removalClient = pending.endpoint.equals(repository.client().backendOrigin())
                    ? repository.client() : StudentCCStatisticsClient.forRemovalEndpoint(pending.endpoint);
            String participant = alias(key, false);
            if (participant == null && pending.subject == null) store.confirmWithdrawal(key, pending.revision);
            else {
                StudentCCStatisticsClient.AuthenticatedWriter writer = removalClient.writer(participant, pending.subject).blockingGet();
                writer.withdraw().blockingGet(); store.confirmWithdrawal(key, pending.revision);
                repository.clearCache();
            }
            removed = true;
        }
        if (removed) message = "Sharing is off and your contributions were removed.";
        if (scope.key.isEmpty()) { if (!removed) message = "The StudentCC pool is being prepared."; return; }
        StudentCCSharingStore.Record record = store.read(scope.key);
        if (!scope.endpoint.equals(record.endpoint) || !scope.endpoint.equals(repository.client().backendOrigin())) return;
        if (!record.enabled || record.removalPending || !sameActive(scope)) {
            if (getState().removalPending) message = "Sharing is off. Removal is pending; retry when connected.";
            return;
        }
        if (collect) {
            boolean verifiedMarks = hasVerifiedMarksSnapshot(scope.owner, scope.semester,
                    preferences.getString("verified_snapshot_owner", ""), preferences.getString("verified_snapshot_semester", ""));
            if (verifiedMarks) {
                AppDatabase database = AppDatabase.getInstance(context);
                StudentCCContributionBuilder.BuildResult collected = Single.zip(database.coursesDao().getAllCourses(),
                        database.marksDao().getAllMarks(), (courses, marks) -> StudentCCContributionBuilder.inspect(courses, marks, scope.semester)).blockingGet();
                if (!sameActive(scope)) return;
                store.queue(scope.key, collected.contributions, scope.semester);
                if (collected.contributions.isEmpty()) message = collected.emptyMessage();
            } else {
                message = "Sync your marks once before sharing this account’s totals.";
            }
        }
        // CGPA has separate account proof and consent; retries use the newest verified value too.
        if (record.consentVersion >= 2) store.queueAcademicSummary(scope.key, academicThreshold(scope));
        record = store.read(scope.key);
        if (!record.enabled || record.removalPending) return;
        if (record.pending.isEmpty() && record.pendingAcademicSummary == null) {
            if (!record.sent.isEmpty() || record.sentAcademicSummary != null) {
                message = "Your shared data is up to date.";
            } else if (message.isEmpty()) {
                message = "Complete course totals and a verified CGPA will be shared after a successful VTOP sync.";
            }
            return;
        }
        String participant = alias(scope.key, true);
        StudentCCStatisticsClient.AuthenticatedWriter writer = repository.client().writer(participant, record.subject).blockingGet();
        store.rememberSubject(scope.key, writer.subject); // Persist ownership before a request can reach the server.
        for (StudentCCContribution contribution : new ArrayList<>(record.pending.values())) {
            StudentCCSharingStore.Record current = store.read(scope.key);
            if (!current.enabled || current.removalPending || !sameActive(scope)) return;
            StudentCCContribution latest = current.pending.get(contribution.key());
            if (latest == null || !StudentCCSharingStore.fingerprint(latest).equals(StudentCCSharingStore.fingerprint(contribution))) continue;
            StudentCCClassStatistics statistics = writer.submit(contribution).blockingGet();
            store.acknowledge(scope.key, contribution); repository.recordSubmission(statistics);
        }
        StudentCCSharingStore.Record current = store.read(scope.key);
        if (!current.enabled || current.removalPending || !sameActive(scope)) return;
        Boolean academic = current.pendingAcademicSummary;
        if (current.consentVersion >= 2 && academic != null && academicThreshold(scope) != null) {
            boolean shareClassTally = current.consentVersion >= 3;
            writer.submitAcademicSummary(academic, shareClassTally).blockingGet();
            store.acknowledgeAcademicSummary(scope.key, academic, shareClassTally);
            repository.clearCache(); // Public class tallies may have changed after cached course acknowledgements.
        }
        current = store.read(scope.key);
        if (current.sentAcademicSummary != null && current.sent.isEmpty()) {
            message = "Your CGPA threshold summary is shared. Complete course totals will be shared when they are ready.";
        } else {
            message = current.sentAcademicSummary == null && current.consentVersion >= 2
                    ? "Your course totals are up to date. A verified CGPA is needed before its threshold summary can be shared."
                    : "Your shared data is up to date.";
        }
    }
    /** Returns true only for a temporary failure so WorkManager can apply bounded backoff. */
    Single<Boolean> retryPending() {
        AccountScope scope = active();
        return Single.fromCallable(() -> {
            try { flush(scope, false); return false; }
            catch (Throwable error) {
                message = failureMessage(error, getState().removalPending);
                return temporary(error);
            } finally { publish(); }
        }).subscribeOn(serial);
    }
    private boolean sameActive(AccountScope scope) {
        AccountScope current = active(); return scope.owner.equals(current.owner) && scope.endpoint.equals(current.endpoint)
                && scope.semester.equals(current.semester);
    }
    /** Choosing sharing never authorizes cached totals without an account-bound semester sync. */
    static boolean hasVerifiedMarksSnapshot(String owner, String semester, String verifiedOwner, String verifiedSemester) {
        return owner != null && !owner.isEmpty() && semester != null && !semester.isEmpty()
                && owner.equals(verifiedOwner) && semester.equals(verifiedSemester);
    }
    private synchronized String alias(String key, boolean create) {
        if (identities == null) throw new IllegalStateException("Sharing identity unavailable");
        String value = identities.getString(key, null);
        if (value == null && create) {
            value = UUID.randomUUID().toString();
            if (!identities.edit().putString(key, value).commit()) throw new IllegalStateException("Sharing identity unavailable");
        }
        return value;
    }
    private Single<SharingState> immediate(String value) { message = value; publish(); return Single.just(getState()); }
    private void publish() { if (states != null) states.onNext(getState()); }
    private void scheduleRetry() {
        try {
            OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(StudentCCSharingWorker.class)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
            WorkManager.getInstance(context).enqueueUniqueWork(RETRY_WORK, ExistingWorkPolicy.KEEP, work);
        } catch (RuntimeException ignored) { /* The next successful marks sync also retries the durable queue. */ }
    }
    private static boolean temporary(Throwable error) {
        return failureKind(error) == StudentCCStatisticsClient.FailureKind.TEMPORARY;
    }
    private static StudentCCStatisticsClient.FailureKind failureKind(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof StudentCCStatisticsClient.FetchException) return ((StudentCCStatisticsClient.FetchException) cause).kind;
            if (cause == cause.getCause()) break;
            cause = cause.getCause();
        }
        return null;
    }
    static String failureMessage(Throwable error, boolean removal) {
        StudentCCStatisticsClient.FailureKind kind = failureKind(error);
        if (kind == StudentCCStatisticsClient.FailureKind.AUTHENTICATION) return removal
                ? "Sharing is off. Removal could not be authenticated. Retry with the original sharing account."
                : "StudentCC could not authenticate sharing. Your totals remain saved; retry sharing when signed in and connected.";
        if (kind == StudentCCStatisticsClient.FailureKind.TEMPORARY) return removal
                ? "Sharing is off. Removal is pending until the connection recovers; it will retry automatically."
                : "StudentCC could not be reached. Your totals are saved and will retry automatically when connected.";
        if (kind == StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD) return removal
                ? "Sharing is off, but StudentCC could not confirm removal. Please retry removal."
                : "StudentCC returned an incompatible statistics response. Your totals remain saved; please retry sharing.";
        if (kind == StudentCCStatisticsClient.FailureKind.NOT_CONFIGURED) return "The StudentCC pool is being prepared.";
        if (kind == StudentCCStatisticsClient.FailureKind.PERMANENT) return removal
                ? "Sharing is off, but StudentCC rejected the removal request. Please retry removal."
                : "StudentCC rejected the contribution request. Your totals remain saved; please retry sharing.";
        return removal ? "Sharing is off. Removal is pending; please retry removal."
                : "Sharing could not be completed. Your totals remain saved; please retry sharing.";
    }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
