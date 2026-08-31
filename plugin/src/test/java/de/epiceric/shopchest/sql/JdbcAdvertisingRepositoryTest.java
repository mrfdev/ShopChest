package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.advertising.AdvertisingPass;
import de.epiceric.shopchest.advertising.AdvertisingPassPurchase;
import de.epiceric.shopchest.advertising.AdvertisementQueueFullException;
import de.epiceric.shopchest.advertising.AdvertisingLifecyclePolicy;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseDeliveryRejectedException;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseStatus;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAdvertisingRepositoryTest {

    private static final String PREFIX = "shopchest_";
    private static final Instant START = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void issuingTheSamePurchaseNonceIsIdempotentAndCreatesOnlyOnePass() throws Exception {
        final UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        final UUID passId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        final var pass = AdvertisingLifecyclePolicy.standard().issuePass(
                passId, owner, Instant.parse("2026-08-31T10:00:00Z"));

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, "shopchest_");

            assertEquals(pass, JdbcAdvertisingRepository.issuePass(
                    connection, "shopchest_", pass, "same-confirmation-nonce"));
            assertEquals(pass, JdbcAdvertisingRepository.issuePass(
                    connection, "shopchest_", pass, "same-confirmation-nonce"));
            assertEquals(pass, JdbcAdvertisingRepository.findPass(
                    connection, "shopchest_", owner).orElseThrow());
        }
    }

    @Test
    void preparesExactEscrowBeforeDeliveryAndIsIdempotentByNonce() throws Exception {
        final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        final AdvertisingPassPurchase prepared = purchase(owner, "durable-purchase-nonce");

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.initialize(connection, PREFIX);

            assertEquals(prepared, JdbcAdvertisingRepository.preparePurchase(
                    connection, PREFIX, prepared));
            assertEquals(prepared, JdbcAdvertisingRepository.preparePurchase(
                    connection, PREFIX, prepared));
            assertEquals(prepared, JdbcAdvertisingRepository.findPurchase(
                    connection, PREFIX, prepared.nonce()).orElseThrow());
            assertEquals(prepared, JdbcAdvertisingRepository.findUnresolvedPurchase(
                    connection, PREFIX, owner).orElseThrow());
        }
    }

    @Test
    void atomicallyDeliversThePreparedPassAndClosesThePurchaseGuard() throws Exception {
        final UUID owner = UUID.fromString("22222222-2222-2222-2222-222222222222");
        final AdvertisingPassPurchase prepared = purchase(owner, "atomic-delivery-nonce");

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.preparePurchase(connection, PREFIX, prepared);

            assertEquals(prepared.pass(), JdbcAdvertisingRepository.deliverPreparedPurchase(
                    connection, PREFIX, prepared.nonce()));
            assertEquals(prepared.pass(), JdbcAdvertisingRepository.deliverPreparedPurchase(
                    connection, PREFIX, prepared.nonce()));
            assertEquals(AdvertisingPurchaseStatus.DELIVERED,
                    JdbcAdvertisingRepository.findPurchase(
                            connection, PREFIX, prepared.nonce()).orElseThrow().status());
            assertEquals(prepared.pass(), JdbcAdvertisingRepository.findPass(
                    connection, PREFIX, owner).orElseThrow());
            assertEquals(java.util.Optional.empty(),
                    JdbcAdvertisingRepository.findUnresolvedPurchase(
                            connection, PREFIX, owner));
        }
    }

    @Test
    void aDefiniteDeliveryConflictKeepsEscrowRecoverable() throws Exception {
        final UUID owner = UUID.fromString("33333333-3333-3333-3333-333333333333");
        final AdvertisingPassPurchase prepared = purchase(owner, "conflicted-delivery-nonce");
        final AdvertisingPass competingPass = AdvertisingLifecyclePolicy.standard().issuePass(
                UUID.randomUUID(), owner, START.plusSeconds(1));

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.preparePurchase(connection, PREFIX, prepared);
            JdbcAdvertisingRepository.issuePass(
                    connection, PREFIX, competingPass, "competing-nonce");

            assertThrows(AdvertisingPurchaseDeliveryRejectedException.class,
                    () -> JdbcAdvertisingRepository.deliverPreparedPurchase(
                            connection, PREFIX, prepared.nonce()));
            assertEquals(AdvertisingPurchaseStatus.PREPARED,
                    JdbcAdvertisingRepository.findPurchase(
                            connection, PREFIX, prepared.nonce()).orElseThrow().status());
            assertEquals(competingPass, JdbcAdvertisingRepository.findPass(
                    connection, PREFIX, owner).orElseThrow());
        }
    }

    @Test
    void pendingRefundRemainsOpenUntilExactRecoveryFinishes() throws Exception {
        final UUID owner = UUID.fromString("44444444-4444-4444-4444-444444444444");
        final AdvertisingPassPurchase prepared = purchase(owner, "refund-recovery-nonce");

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.preparePurchase(connection, PREFIX, prepared);

            JdbcAdvertisingRepository.markRefundPending(
                    connection, PREFIX, prepared.nonce(), "definite conflict");
            JdbcAdvertisingRepository.markRefundPending(
                    connection, PREFIX, prepared.nonce(), "definite conflict");
            assertEquals(AdvertisingPurchaseStatus.REFUND_PENDING,
                    JdbcAdvertisingRepository.findUnresolvedPurchase(
                            connection, PREFIX, owner).orElseThrow().status());

            JdbcAdvertisingRepository.markRefunded(
                    connection, PREFIX, prepared.nonce());
            assertEquals(AdvertisingPurchaseStatus.REFUNDED,
                    JdbcAdvertisingRepository.findPurchase(
                            connection, PREFIX, prepared.nonce()).orElseThrow().status());
            assertEquals(java.util.Optional.empty(),
                    JdbcAdvertisingRepository.findUnresolvedPurchase(
                            connection, PREFIX, owner));
        }
    }

    @Test
    void unresolvedOwnerGuardBlocksASecondChargeButReleasesWhenNotCharged() throws Exception {
        final UUID owner = UUID.fromString("55555555-5555-5555-5555-555555555555");
        final AdvertisingPassPurchase first = purchase(owner, "first-owner-guard-nonce");
        final AdvertisingPassPurchase second = purchase(owner, "second-owner-guard-nonce");

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.preparePurchase(connection, PREFIX, first);

            assertThrows(java.sql.SQLException.class,
                    () -> JdbcAdvertisingRepository.preparePurchase(
                            connection, PREFIX, second));

            JdbcAdvertisingRepository.markNotCharged(
                    connection, PREFIX, first.nonce());
            assertEquals(AdvertisingPurchaseStatus.NOT_CHARGED,
                    JdbcAdvertisingRepository.findPurchase(
                            connection, PREFIX, first.nonce()).orElseThrow().status());
            assertEquals(second, JdbcAdvertisingRepository.preparePurchase(
                    connection, PREFIX, second));
        }
    }

    @Test
    void queueAdmissionIsBoundedAtomicallyAndCancellationReleasesCapacity() throws Exception {
        final UUID firstOwner = UUID.fromString("66666666-6666-6666-6666-666666666666");
        final UUID secondOwner = UUID.fromString("77777777-7777-7777-7777-777777777777");
        final var policy = AdvertisingLifecyclePolicy.standard();
        final AdvertisingPass firstPass = policy.issuePass(UUID.randomUUID(), firstOwner, START);
        final AdvertisingPass secondPass = policy.issuePass(UUID.randomUUID(), secondOwner, START);
        final var firstSubmission = policy.submit(firstPass, UUID.randomUUID(), START);
        final var secondSubmission = policy.submit(secondPass, UUID.randomUUID(), START);

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.issuePass(connection, PREFIX, firstPass, "first-queue-pass");
            JdbcAdvertisingRepository.issuePass(connection, PREFIX, secondPass, "second-queue-pass");

            JdbcAdvertisingRepository.saveTransition(
                    connection, PREFIX, firstSubmission, 1);
            assertThrows(AdvertisementQueueFullException.class,
                    () -> JdbcAdvertisingRepository.saveTransition(
                            connection, PREFIX, secondSubmission, 1));
            assertEquals(null, JdbcAdvertisingRepository.findPass(
                    connection, PREFIX, secondOwner).orElseThrow().openRequestId());

            JdbcAdvertisingRepository.saveTransition(
                    connection,
                    PREFIX,
                    policy.cancel(
                            firstSubmission.pass(), firstSubmission.request(), START.plusSeconds(1)),
                    1);
            JdbcAdvertisingRepository.saveTransition(
                    connection, PREFIX, secondSubmission, 1);
            assertEquals(secondSubmission.request(), JdbcAdvertisingRepository.findOpenRequest(
                    connection, PREFIX, secondOwner).orElseThrow());
        }
    }

    @Test
    void successfulBroadcastReleasesOneQueueCapacitySlot() throws Exception {
        final UUID firstOwner = UUID.fromString("88888888-8888-8888-8888-888888888888");
        final UUID secondOwner = UUID.fromString("99999999-9999-9999-9999-999999999999");
        final var policy = AdvertisingLifecyclePolicy.standard();
        final AdvertisingPass firstPass = policy.issuePass(UUID.randomUUID(), firstOwner, START);
        final AdvertisingPass secondPass = policy.issuePass(UUID.randomUUID(), secondOwner, START);
        final var firstSubmission = policy.submit(firstPass, UUID.randomUUID(), START);
        final var secondSubmission = policy.submit(secondPass, UUID.randomUUID(), START);

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcAdvertisingRepository.initialize(connection, PREFIX);
            JdbcAdvertisingRepository.issuePass(connection, PREFIX, firstPass, "first-broadcast-pass");
            JdbcAdvertisingRepository.issuePass(connection, PREFIX, secondPass, "second-broadcast-pass");
            JdbcAdvertisingRepository.saveTransition(connection, PREFIX, firstSubmission, 1);

            final var broadcast = policy.broadcast(
                    firstSubmission.pass(), firstSubmission.request(), START.plusSeconds(1));
            assertTrue(JdbcAdvertisingRepository.commitBroadcast(
                    connection,
                    PREFIX,
                    firstSubmission.pass(),
                    firstSubmission.request(),
                    broadcast,
                    START.plusSeconds(1),
                    START.plusSeconds(31)));
            assertEquals(false, JdbcAdvertisingRepository.commitBroadcast(
                    connection,
                    PREFIX,
                    firstSubmission.pass(),
                    firstSubmission.request(),
                    broadcast,
                    START.plusSeconds(1),
                    START.plusSeconds(31)));
            assertEquals(1, JdbcAdvertisingRepository.findPass(
                    connection, PREFIX, firstOwner).orElseThrow().broadcastsUsed());

            JdbcAdvertisingRepository.saveTransition(connection, PREFIX, secondSubmission, 1);
            assertEquals(secondSubmission.request(), JdbcAdvertisingRepository.findOpenRequest(
                    connection, PREFIX, secondOwner).orElseThrow());
        }
    }

    private static AdvertisingPassPurchase purchase(UUID owner, String nonce) {
        final AdvertisingPass pass = AdvertisingLifecyclePolicy.standard().issuePass(
                UUID.nameUUIDFromBytes(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                owner,
                START);
        return AdvertisingPassPurchase.prepared(
                nonce,
                pass,
                "base64-exact-slot-evidence",
                START);
    }
}
