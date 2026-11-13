package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class StudentCCSharingStoreTest {
    private TestPreferences preferences;
    private StudentCCSharingStore store;
    private static final String KEY = "synthetic_account_origin";
    private static final String OWNER = "synthetic_owner_hash";
    private static final String ENDPOINT = "http://127.0.0.1:8766/";
    @Before public void setUp() {
        preferences = new TestPreferences(); store = new StudentCCSharingStore(preferences);
    }
    private static StudentCCContribution contribution(double total) {
        return new StudentCCContribution(new StudentCCClassScope("CH", "CH2026", "CH2026CLASS1"), "CSE1001", total);
    }
    private void enable() { store.enable(KEY, OWNER, ENDPOINT); }
    @Test public void sharingIsOffByDefaultAndQueueCannotEnableIt() {
        store.queue(KEY, Collections.singletonList(contribution(12.25)));
        assertFalse(store.read(KEY).enabled); assertTrue(store.read(KEY).pending.isEmpty());
    }
    @Test public void latestFractionalTotalReplacesPendingAndSurvivesRecreation() {
        enable(); store.queue(KEY, Collections.singletonList(contribution(12.25)));
        store.queue(KEY, Collections.singletonList(contribution(14.125)));
        store = new StudentCCSharingStore(preferences);
        assertTrue(store.read(KEY).enabled); assertEquals(1, store.read(KEY).pending.size());
        assertEquals(14.125, store.read(KEY).pending.get(contribution(0).key()).total, 0);
        assertEquals(ENDPOINT, store.read(KEY).endpoint);
    }
    @Test public void unchangedAcknowledgedTotalsAreNotResubmitted() {
        enable(); StudentCCContribution value = contribution(12.25);
        store.queue(KEY, Collections.singletonList(value)); store.acknowledge(KEY, value);
        store.queue(KEY, Collections.singletonList(value));
        assertTrue(store.read(KEY).pending.isEmpty()); assertEquals(1, store.read(KEY).sent.size());
    }
    @Test public void returningToAcknowledgedTotalCancelsStaleFailedUpdate() {
        enable(); StudentCCContribution sent = contribution(12.25);
        store.queue(KEY, Collections.singletonList(sent)); store.acknowledge(KEY, sent);
        store.queue(KEY, Collections.singletonList(contribution(10.5)));
        store.queue(KEY, Collections.singletonList(sent));
        assertTrue(store.read(KEY).pending.isEmpty());
    }
    @Test public void acknowledgmentOfOldUploadCannotRemoveNewerQueuedTotal() {
        enable(); StudentCCContribution old = contribution(12.25);
        store.queue(KEY, Collections.singletonList(old));
        store.queue(KEY, Collections.singletonList(contribution(15.5))); store.acknowledge(KEY, old);
        assertEquals(15.5, store.read(KEY).pending.get(old.key()).total, 0);
    }
    @Test public void withdrawalDisablesSynchronouslyAndLateUploadCannotUndoIt() {
        enable(); StudentCCContribution value = contribution(12.25);
        store.queue(KEY, Collections.singletonList(value)); store.rememberSubject(KEY, "synthetic_auth_subject");
        store.withdraw(KEY, OWNER, ENDPOINT); store.acknowledge(KEY, value);
        store.queue(KEY, Collections.singletonList(value)); store.enable(KEY, OWNER, ENDPOINT);
        StudentCCSharingStore.Record record = store.read(KEY);
        assertFalse(record.enabled); assertTrue(record.removalPending);
        assertTrue(record.pending.isEmpty()); assertTrue(record.sent.isEmpty());
        assertEquals("synthetic_auth_subject", record.subject); assertEquals(ENDPOINT, record.endpoint);
    }
    @Test public void onlyCurrentWithdrawalAcknowledgmentClearsPendingRemoval() {
        enable(); store.rememberSubject(KEY, "synthetic_auth_subject");
        store.withdraw(KEY, OWNER, ENDPOINT); long first = store.read(KEY).revision;
        store.withdraw(KEY, OWNER, ENDPOINT); store.confirmWithdrawal(KEY, first);
        assertTrue(store.read(KEY).removalPending);
        store.confirmWithdrawal(KEY, store.read(KEY).revision);
        assertFalse(store.read(KEY).removalPending); assertNull(store.read(KEY).subject);
        store.enable(KEY, OWNER, ENDPOINT); assertTrue(store.read(KEY).enabled);
    }
    @Test public void distinctAccountAndOriginRecordsKeepIndependentConsentQueuesAndIdentity() {
        enable(); store.queue(KEY, Collections.singletonList(contribution(12.25)));
        store.rememberSubject(KEY, "first_subject");
        store.enable("second_origin", OWNER, "https://synthetic.example/");
        store.enable("second_account", "second_owner", ENDPOINT);
        store.withdraw("second_origin", OWNER, "https://synthetic.example/");
        assertTrue(store.read(KEY).enabled); assertEquals(1, store.read(KEY).pending.size());
        assertEquals("first_subject", store.read(KEY).subject);
        assertTrue(store.read("second_account").enabled);
        assertEquals(Arrays.asList(KEY, "second_origin").size(), store.keysForOwner(OWNER).size());
    }

    @Test public void verifiedSnapshotDropsAnOlderFailedTotalThatIsNoLongerComplete() {
        enable();
        store.queue(KEY, Collections.singletonList(contribution(42.25)), "CH2026");
        assertEquals(1, store.read(KEY).pending.size());
        store.queue(KEY, Collections.emptyList(), "CH2026");
        assertTrue(store.read(KEY).pending.isEmpty());
        assertTrue(store.read(KEY).enabled);
    }

    @Test public void verifiedSnapshotPreservesPendingTotalsFromOtherSemesters() {
        enable();
        StudentCCContribution older = new StudentCCContribution(
                new StudentCCClassScope("CH", "CH2025", "CH2025CLASS1"), "CSE1001", 39.25);
        store.queue(KEY, Arrays.asList(older, contribution(42.25)));
        store.queue(KEY, Collections.emptyList(), "CH2026");
        assertEquals(1, store.read(KEY).pending.size());
        assertEquals(39.25, store.read(KEY).pending.get(older.key()).total, 0.0);
    }

    @Test public void internalAndFinalUpdatesKeepDistinctDurableRecordsAndBothAreRemoved() throws Exception {
        enable();
        StudentCCContribution internal = contribution(47.75);
        StudentCCContribution completed = new StudentCCContribution(
                new StudentCCClassScope("CH", "CH2026", "CH2026CLASS1", 100), "CSE1001", 78.875);
        store.queue(KEY, Arrays.asList(internal, completed), "CH2026");
        store = new StudentCCSharingStore(preferences);
        assertTrue(store.read(KEY).enabled);
        assertEquals(2, store.read(KEY).pending.size());
        assertEquals(100, store.read(KEY).pending.get(completed.key()).scope.marksBasis);
        assertEquals(78.875, store.read(KEY).pending.get(completed.key()).total, 0.0);
        store.acknowledge(KEY, internal);
        assertEquals(1, store.read(KEY).pending.size());
        store.acknowledge(KEY, completed);
        assertEquals(2, store.read(KEY).sent.size());
        StudentCCContribution updatedFinal = new StudentCCContribution(completed.scope, "CSE1001", 79.125);
        store.queue(KEY, Arrays.asList(internal, updatedFinal), "CH2026");
        assertEquals(1, store.read(KEY).pending.size());
        assertEquals(79.125, store.read(KEY).pending.get(completed.key()).total, 0.0);
        store.withdraw(KEY, OWNER, ENDPOINT);
        assertFalse(store.read(KEY).enabled);
        assertTrue(store.read(KEY).removalPending);
        assertTrue(store.read(KEY).pending.isEmpty());
        assertTrue(store.read(KEY).sent.isEmpty());
    }

    @Test public void newSetupAndDurableDeclineNeverEnableSharingOrQueueAcademicData() {
        assertFalse(store.read(KEY).present);
        store.queueAcademicSummary(KEY, true);
        assertNull(store.read(KEY).pendingAcademicSummary);
        store.dismissSetup(KEY, OWNER, ENDPOINT);
        store = new StudentCCSharingStore(preferences);
        assertTrue(store.read(KEY).present);
        assertFalse(store.read(KEY).enabled);
        assertEquals(0, store.read(KEY).consentVersion);
        assertNull(store.read(KEY).subject);
        store.queueAcademicSummary(KEY, false);
        assertNull(store.read(KEY).pendingAcademicSummary);
    }

    @Test public void existingTotalOnlyConsentCannotBeSilentlyExpandedToCgpaSharing() {
        preferences.edit().putString(KEY, "{\"enabled\":true,\"owner\":\"" + OWNER
                + "\",\"endpoint\":\"" + ENDPOINT + "\"}").commit();
        assertTrue(store.read(KEY).enabled);
        assertEquals(1, store.read(KEY).consentVersion);
        store.queueAcademicSummary(KEY, true);
        assertNull(store.read(KEY).pendingAcademicSummary);
        store.queue(KEY, Collections.singletonList(contribution(47.75)));
        assertEquals(1, store.read(KEY).pending.size());
        store.enable(KEY, OWNER, ENDPOINT, 2); // Models a new explicit disclosure and confirmation.
        store.queueAcademicSummary(KEY, true);
        assertEquals(Boolean.TRUE, store.read(KEY).pendingAcademicSummary);
    }

    @Test public void academicOutboxKeepsTheLatestThresholdAcrossRestartsAndOlderAcknowledgments() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        store.queueAcademicSummary(KEY, true);
        store.queueAcademicSummary(KEY, false);
        store.acknowledgeAcademicSummary(KEY, true);
        store = new StudentCCSharingStore(preferences);
        assertEquals(Boolean.FALSE, store.read(KEY).pendingAcademicSummary);
        assertEquals(Boolean.TRUE, store.read(KEY).sentAcademicSummary);
        store.acknowledgeAcademicSummary(KEY, false);
        assertNull(store.read(KEY).pendingAcademicSummary);
        assertEquals(Boolean.FALSE, store.read(KEY).sentAcademicSummary);
        store.queueAcademicSummary(KEY, false);
        assertNull(store.read(KEY).pendingAcademicSummary);
    }

    @Test public void missingCgpaNeverQueuesInventedBelowNineFlag() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        store.queueAcademicSummary(KEY, null);
        assertNull(store.read(KEY).pendingAcademicSummary);
        assertNull(store.read(KEY).sentAcademicSummary);
    }

    @Test public void withdrawingAcademicOnlyParticipantClearsBothDomainsAndBlocksLateUploads() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        store.rememberSubject(KEY, "synthetic-academic-only-subject");
        store.queueAcademicSummary(KEY, true);
        assertTrue(store.read(KEY).pending.isEmpty());
        store.withdraw(KEY, OWNER, ENDPOINT);
        store.acknowledgeAcademicSummary(KEY, true);
        store.queueAcademicSummary(KEY, true);
        assertFalse(store.read(KEY).enabled);
        assertTrue(store.read(KEY).removalPending);
        assertNull(store.read(KEY).pendingAcademicSummary);
        assertNull(store.read(KEY).sentAcademicSummary);
        assertEquals("synthetic-academic-only-subject", store.read(KEY).subject);
        store.confirmWithdrawal(KEY, store.read(KEY).revision);
        assertTrue(store.read(KEY).present);
        assertFalse(store.read(KEY).enabled);
        assertNull(store.read(KEY).subject);
    }

    @Test public void accountRecordsIgnoreTypedCgpaProofMetadata() {
        enable();
        preferences.edit().putBoolean("verified_cgpa_at_least_nine", true)
                .putLong("verified_cgpa_at", 123L).putString("verified_cgpa_owner", OWNER).commit();
        assertEquals(Collections.singletonList(KEY), store.keysForOwner(OWNER));
    }

    @Test public void confirmationBeforeFirstSemesterPersistsWithoutInventingUploadData() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        store = new StudentCCSharingStore(preferences);
        StudentCCSharingStore.Record record = store.read(KEY);
        assertTrue(record.enabled);
        assertEquals(2, record.consentVersion);
        assertEquals(OWNER, record.owner);
        assertEquals(ENDPOINT, record.endpoint);
        assertNull(record.subject);
        assertTrue(record.pending.isEmpty());
        assertNull(record.pendingAcademicSummary);
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot(OWNER, "", "", ""));
        assertNull(StudentCCAcademicSummary.verifiedThreshold(OWNER, "", null, 0L, 200L));
    }

    @Test public void explicitRefreshCanResubmitUnchangedTotalsAndAcademicFlagAfterBackendReset() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        StudentCCContribution value = contribution(47.75);
        store.queue(KEY, Collections.singletonList(value));
        store.acknowledge(KEY, value);
        store.queueAcademicSummary(KEY, true);
        store.acknowledgeAcademicSummary(KEY, true);
        store.queue(KEY, Collections.singletonList(value));
        store.queueAcademicSummary(KEY, true);
        assertTrue(store.read(KEY).pending.isEmpty());
        assertNull(store.read(KEY).pendingAcademicSummary);

        store.clearUploadAcknowledgements(KEY, OWNER, ENDPOINT);
        store = new StudentCCSharingStore(preferences);
        store.queue(KEY, Collections.singletonList(value));
        store.queueAcademicSummary(KEY, true);
        assertEquals(1, store.read(KEY).pending.size());
        assertEquals(Boolean.TRUE, store.read(KEY).pendingAcademicSummary);
        assertTrue(store.read(KEY).sent.isEmpty());
        assertNull(store.read(KEY).sentAcademicSummary);
        assertTrue(store.read(KEY).enabled);
        assertEquals(2, store.read(KEY).consentVersion);
    }

    @Test public void explicitRefreshPreservesNewerPendingValuesAndParticipantIdentity() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        StudentCCContribution sent = contribution(47.75);
        store.queue(KEY, Collections.singletonList(sent));
        store.acknowledge(KEY, sent);
        store.acknowledgeAcademicSummary(KEY, true);
        store.queue(KEY, Collections.singletonList(contribution(48.25)));
        store.queueAcademicSummary(KEY, false);
        store.rememberSubject(KEY, "synthetic-refresh-subject");
        long revision = store.read(KEY).revision;
        store.clearUploadAcknowledgements(KEY, OWNER, ENDPOINT);
        StudentCCSharingStore.Record record = store.read(KEY);
        assertEquals(48.25, record.pending.get(sent.key()).total, 0.0);
        assertEquals(Boolean.FALSE, record.pendingAcademicSummary);
        assertEquals("synthetic-refresh-subject", record.subject);
        assertEquals(revision, record.revision);
        assertEquals(OWNER, record.owner);
        assertEquals(ENDPOINT, record.endpoint);
        assertEquals(2, record.consentVersion);
    }

    @Test public void explicitRefreshCannotCrossAccountsOrOriginsOrUndoAnOptOut() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        StudentCCContribution value = contribution(47.75);
        store.acknowledge(KEY, value);
        store.acknowledgeAcademicSummary(KEY, true);
        store.clearUploadAcknowledgements(KEY, "another-owner", ENDPOINT);
        store.clearUploadAcknowledgements(KEY, OWNER, "https://another-origin.example/");
        assertEquals(1, store.read(KEY).sent.size());
        assertEquals(Boolean.TRUE, store.read(KEY).sentAcademicSummary);

        store.enable("another-key", OWNER, "https://another-origin.example/", 2);
        store.acknowledge("another-key", value);
        store.clearUploadAcknowledgements(KEY, OWNER, ENDPOINT);
        assertEquals(1, store.read("another-key").sent.size());

        store.withdraw(KEY, OWNER, ENDPOINT);
        long revision = store.read(KEY).revision;
        store.clearUploadAcknowledgements(KEY, OWNER, ENDPOINT);
        assertFalse(store.read(KEY).enabled);
        assertTrue(store.read(KEY).removalPending);
        assertEquals(revision, store.read(KEY).revision);
        store.confirmWithdrawal(KEY, revision);
        store.clearUploadAcknowledgements(KEY, OWNER, ENDPOINT);
        store = new StudentCCSharingStore(preferences);
        assertTrue(store.read(KEY).present);
        assertFalse(store.read(KEY).enabled);
        assertFalse(store.read(KEY).removalPending);
        assertNull(store.read(KEY).pendingAcademicSummary);
    }

    @Test public void privateConsentUpgradeOnlyInvalidatesAcademicReceiptUntilPublicUploadIsAcknowledged() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        StudentCCContribution value = contribution(47.75);
        store.queue(KEY, Collections.singletonList(value));
        store.acknowledge(KEY, value);
        store.queueAcademicSummary(KEY, true);
        store.acknowledgeAcademicSummary(KEY, true);
        store.rememberSubject(KEY, "synthetic-public-upgrade-subject");
        store.queue(KEY, Collections.singletonList(contribution(48.25)));

        store.enable(KEY, OWNER, ENDPOINT, 3);
        StudentCCSharingStore.Record record = store.read(KEY);
        assertEquals(3, record.consentVersion);
        assertEquals(1, record.sent.size());
        assertEquals(48.25, record.pending.get(value.key()).total, 0.0);
        assertEquals("synthetic-public-upgrade-subject", record.subject);
        assertNull(record.sentAcademicSummary);
        store.queueAcademicSummary(KEY, true);
        assertEquals(Boolean.TRUE, store.read(KEY).pendingAcademicSummary);
        store.acknowledgeAcademicSummary(KEY, true, false); // Private request already in flight.
        assertNull(store.read(KEY).sentAcademicSummary);
        assertEquals(Boolean.TRUE, store.read(KEY).pendingAcademicSummary);
        store.acknowledgeAcademicSummary(KEY, true, true);
        store.queueAcademicSummary(KEY, true);
        assertEquals(Boolean.TRUE, store.read(KEY).sentAcademicSummary);
        assertNull(store.read(KEY).pendingAcademicSummary);
    }

    @Test public void privateConsentStillDeduplicatesWithoutPermissionToSharePublicly() {
        store.enable(KEY, OWNER, ENDPOINT, 2);
        store.queueAcademicSummary(KEY, true);
        store.acknowledgeAcademicSummary(KEY, true, false);
        store.queueAcademicSummary(KEY, true);
        assertEquals(2, store.read(KEY).consentVersion);
        assertNull(store.read(KEY).pendingAcademicSummary);
        store.acknowledgeAcademicSummary(KEY, false, true);
        assertEquals(Boolean.TRUE, store.read(KEY).sentAcademicSummary);
    }

    @Test public void reconfirmingPublicConsentPreservesAcknowledgmentAndOptOutBlocksLatePublicUpload() {
        store.enable(KEY, OWNER, ENDPOINT, 3);
        store.queueAcademicSummary(KEY, true);
        store.acknowledgeAcademicSummary(KEY, true, true);
        store.enable(KEY, OWNER, ENDPOINT, 3);
        assertEquals(Boolean.TRUE, store.read(KEY).sentAcademicSummary);
        store.queueAcademicSummary(KEY, true);
        assertNull(store.read(KEY).pendingAcademicSummary);
        store.withdraw(KEY, OWNER, ENDPOINT);
        store.acknowledgeAcademicSummary(KEY, true, true);
        store.enable(KEY, OWNER, ENDPOINT, 3);
        assertFalse(store.read(KEY).enabled);
        assertTrue(store.read(KEY).removalPending);
        assertNull(store.read(KEY).sentAcademicSummary);
        assertNull(store.read(KEY).pendingAcademicSummary);
    }

    static final class TestPreferences implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<>();
        @Override public synchronized Map<String, ?> getAll() { return new HashMap<>(data); }
        @Override public synchronized String getString(String key, String fallback) {
            Object value = data.get(key); return value == null ? fallback : (String) value;
        }
        @Override public Set<String> getStringSet(String key, Set<String> fallback) { return fallback; }
        @Override public int getInt(String key, int fallback) { return fallback; }
        @Override public long getLong(String key, long fallback) { Object value = data.get(key); return value == null ? fallback : (Long) value; }
        @Override public float getFloat(String key, float fallback) { return fallback; }
        @Override public boolean getBoolean(String key, boolean fallback) { return fallback; }
        @Override public synchronized boolean contains(String key) { return data.containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> pending = new HashMap<>(); boolean clear;
                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> value) { pending.put(key, value); return this; }
                @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { pending.put(key, null); return this; }
                @Override public Editor clear() { clear = true; return this; }
                @Override public boolean commit() {
                    synchronized (TestPreferences.this) {
                        if (clear) data.clear();
                        for (Map.Entry<String, Object> entry : pending.entrySet()) {
                            if (entry.getValue() == null) data.remove(entry.getKey()); else data.put(entry.getKey(), entry.getValue());
                        }
                    } return true;
                }
                @Override public void apply() { commit(); }
            };
        }
    }
}
