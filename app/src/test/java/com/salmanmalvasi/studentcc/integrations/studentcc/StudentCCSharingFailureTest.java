package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.Test;

import static org.junit.Assert.*;

/** Sharing errors must explain the failed operation rather than report a successful upload. */
public class StudentCCSharingFailureTest {
    @Test public void authenticationFailureDoesNotPromiseAutomaticNetworkRetry() {
        Throwable error = new RuntimeException(new StudentCCStatisticsClient.FetchException(
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION, "synthetic auth failure"));
        String message = StudentCCSharingManager.failureMessage(error, false);
        assertTrue(message.contains("authenticate sharing"));
        assertTrue(message.contains("totals remain saved"));
        assertFalse(message.contains("automatically"));
    }

    @Test public void temporaryFailurePromisesDurableRetryAndNeverClaimsItWasUploaded() {
        Throwable error = new StudentCCStatisticsClient.FetchException(
                StudentCCStatisticsClient.FailureKind.TEMPORARY, "synthetic temporary failure");
        String message = StudentCCSharingManager.failureMessage(error, false);
        assertTrue(message.contains("saved"));
        assertTrue(message.contains("retry automatically"));
        assertFalse(message.contains("up to date"));
    }

    @Test public void withdrawalAuthFailureKeepsRemovalUnconfirmedAndNamesTheRequiredIdentity() {
        Throwable error = new StudentCCStatisticsClient.FetchException(
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION, "synthetic auth failure");
        String message = StudentCCSharingManager.failureMessage(error, true);
        assertTrue(message.contains("Sharing is off"));
        assertTrue(message.contains("original sharing account"));
        assertFalse(message.contains("were removed"));
    }
}
