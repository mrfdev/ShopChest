package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdvertisingLifecyclePolicyTest {

    private static final Instant START = Instant.parse("2026-08-31T10:00:00Z");

    private final AdvertisingLifecyclePolicy policy = AdvertisingLifecyclePolicy.standard();

    @Test
    void issuesASevenDayPassWithThreeSuccessfulBroadcasts() {
        final UUID passId = UUID.randomUUID();
        final UUID ownerId = UUID.randomUUID();

        final AdvertisingPass pass = policy.issuePass(passId, ownerId, START);

        assertEquals(START, pass.startsAt());
        assertEquals(Instant.parse("2026-09-07T10:00:00Z"), pass.expiresAt());
        assertEquals(3, pass.broadcastLimit());
        assertEquals(0, pass.broadcastsUsed());
        assertEquals(3, pass.unreservedBroadcasts());
        assertNull(pass.openRequestId());
    }

    @Test
    void permitsOnlyOneOpenRequestAndReturnsItsReservationOnCancel() {
        final AdvertisingPass pass = policy.issuePass(
                UUID.randomUUID(), UUID.randomUUID(), START);
        final UUID firstRequestId = UUID.randomUUID();
        final AdvertisementTransition submitted =
                policy.submit(pass, firstRequestId, START.plusSeconds(60));

        final AdvertisingPolicyException failure = assertThrows(
                AdvertisingPolicyException.class,
                () -> policy.submit(
                        submitted.pass(), UUID.randomUUID(), START.plusSeconds(120)));

        assertEquals(AdvertisingPolicyException.Reason.OPEN_REQUEST_EXISTS, failure.reason());
        assertEquals(firstRequestId, submitted.pass().openRequestId());
        assertEquals(2, submitted.pass().unreservedBroadcasts());

        final AdvertisementTransition cancelled = policy.cancel(
                submitted.pass(), submitted.request(), START.plusSeconds(180));
        assertNull(cancelled.pass().openRequestId());
        assertEquals(3, cancelled.pass().unreservedBroadcasts());
        assertEquals(AdvertisementRequestStatus.CANCELLED, cancelled.request().status());
    }

    @Test
    void countsOnlySuccessfulBroadcastsAndEnforcesTheTwentyFourHourCooldown() {
        AdvertisingPass pass = policy.issuePass(UUID.randomUUID(), UUID.randomUUID(), START);

        AdvertisementTransition first = policy.submit(pass, UUID.randomUUID(), START);
        first = policy.broadcast(first.pass(), first.request(), START);
        pass = first.pass();
        assertEquals(1, pass.broadcastsUsed());

        final AdvertisementTransition second = policy.submit(
                pass, UUID.randomUUID(), START.plusSeconds(3_600));
        assertEquals(START.plusSeconds(86_400), second.request().eligibleAt());
        final AdvertisingPolicyException tooEarly = assertThrows(
                AdvertisingPolicyException.class,
                () -> policy.broadcast(
                        second.pass(), second.request(), START.plusSeconds(86_399)));
        assertEquals(AdvertisingPolicyException.Reason.OWNER_COOLDOWN, tooEarly.reason());

        final AdvertisementTransition secondBroadcast = policy.broadcast(
                second.pass(), second.request(), START.plusSeconds(86_400));
        final AdvertisementTransition third = policy.submit(
                secondBroadcast.pass(), UUID.randomUUID(), START.plusSeconds(90_000));
        final AdvertisementTransition thirdBroadcast = policy.broadcast(
                third.pass(), third.request(), START.plusSeconds(172_800));
        assertEquals(3, thirdBroadcast.pass().broadcastsUsed());

        final AdvertisingPolicyException spent = assertThrows(
                AdvertisingPolicyException.class,
                () -> policy.submit(
                        thirdBroadcast.pass(), UUID.randomUUID(), START.plusSeconds(176_400)));
        assertEquals(AdvertisingPolicyException.Reason.NO_ALLOWANCE, spent.reason());
    }

    @Test
    void aRequestReservedBeforePassExpiryMayBroadcastAfterExpiry() {
        AdvertisingPass pass = policy.issuePass(UUID.randomUUID(), UUID.randomUUID(), START);
        final Instant firstBroadcastAt = START.plusSeconds(6 * 86_400L + 43_200L);
        AdvertisementTransition first = policy.submit(pass, UUID.randomUUID(), firstBroadcastAt);
        first = policy.broadcast(first.pass(), first.request(), firstBroadcastAt);

        final Instant submittedBeforeExpiry = first.pass().expiresAt().minusSeconds(60);
        final AdvertisementTransition queued = policy.submit(
                first.pass(), UUID.randomUUID(), submittedBeforeExpiry);
        assertEquals(firstBroadcastAt.plusSeconds(86_400), queued.request().eligibleAt());

        final AdvertisementTransition delivered = policy.broadcast(
                queued.pass(), queued.request(), queued.request().eligibleAt());
        assertEquals(AdvertisementRequestStatus.BROADCAST, delivered.request().status());
        assertEquals(2, delivered.pass().broadcastsUsed());

        final AdvertisingPolicyException expired = assertThrows(
                AdvertisingPolicyException.class,
                () -> policy.submit(
                        delivered.pass(), UUID.randomUUID(), delivered.pass().expiresAt()));
        assertEquals(AdvertisingPolicyException.Reason.PASS_NOT_ACTIVE, expired.reason());
    }

    @Test
    void rejectsAnAggregateWhoseQueuedRequestIsNotReservedByThePass() {
        final AdvertisingPass pass = policy.issuePass(UUID.randomUUID(), UUID.randomUUID(), START);
        final AdvertisementRequest unreserved = new AdvertisementRequest(
                UUID.randomUUID(),
                pass.ownerId(),
                pass.id(),
                AdvertisementRequestStatus.QUEUED,
                START,
                START,
                null);

        assertThrows(IllegalArgumentException.class,
                () -> new AdvertisementTransition(pass, unreserved));
    }
}
