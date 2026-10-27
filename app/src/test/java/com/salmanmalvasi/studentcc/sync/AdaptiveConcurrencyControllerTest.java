package com.salmanmalvasi.studentcc.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests verifying adaptive concurrency controller feedback loop, backoff, and limits.
 */
public class AdaptiveConcurrencyControllerTest {

    @Test
    public void testInitialConcurrencyIsDefault() {
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController();
        Assert.assertEquals(AdaptiveConcurrencyController.DEFAULT_INITIAL_CONCURRENCY, controller.getCurrentConcurrency());
    }

    @Test
    public void testScaleUpOnConsecutiveFastSuccesses() {
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController(2, 1, 4, 3, 1000L);

        // Record 3 fast responses (< 1000ms)
        controller.recordSuccess(500);
        controller.recordSuccess(400);
        Assert.assertEquals(2, controller.getCurrentConcurrency());

        controller.recordSuccess(450); // 3rd consecutive fast success
        Assert.assertEquals("Should scale up to 3 after window of fast successes", 3, controller.getCurrentConcurrency());

        // Record 3 more fast responses
        controller.recordSuccess(300);
        controller.recordSuccess(350);
        controller.recordSuccess(400);
        Assert.assertEquals("Should scale up to 4", 4, controller.getCurrentConcurrency());

        // Should not exceed max (4)
        controller.recordSuccess(300);
        controller.recordSuccess(300);
        controller.recordSuccess(300);
        Assert.assertEquals("Should clamp at max concurrency 4", 4, controller.getCurrentConcurrency());
    }

    @Test
    public void testSlowSuccessDoesNotTriggerScaleUp() {
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController(2, 1, 4, 3, 1000L);

        controller.recordSuccess(500);
        controller.recordSuccess(1500); // Slower than threshold
        controller.recordSuccess(400);

        Assert.assertEquals("Slow response should reset fast streak and keep concurrency at 2",
                2, controller.getCurrentConcurrency());
    }

    @Test
    public void testThrottlingBacksOffToMinimumImmediately() {
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController(3, 1, 4, 3, 1000L);

        // Simulate HTTP 429 Too Many Requests
        controller.recordFailure(429, false);

        Assert.assertEquals("HTTP 429 must immediately throttle down to minimum concurrency 1",
                1, controller.getCurrentConcurrency());
    }

    @Test
    public void testTimeoutBacksOffToMinimumImmediately() {
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController(3, 1, 4, 3, 1000L);

        // Simulate connection timeout
        controller.recordFailure(0, true);

        Assert.assertEquals("Connection timeout must immediately throttle down to minimum concurrency 1",
                1, controller.getCurrentConcurrency());
    }
}
