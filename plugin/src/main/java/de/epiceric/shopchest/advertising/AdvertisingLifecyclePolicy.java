package de.epiceric.shopchest.advertising;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Pure state-transition policy for Advertising Passes and Requests. */
public final class AdvertisingLifecyclePolicy {

    private final AdvertisingPassTerms terms;

    public AdvertisingLifecyclePolicy(AdvertisingPassTerms terms) {
        this.terms = Objects.requireNonNull(terms, "terms");
    }

    public static AdvertisingLifecyclePolicy standard() {
        return new AdvertisingLifecyclePolicy(AdvertisingPassTerms.STANDARD);
    }

    public AdvertisingPass issuePass(UUID passId, UUID ownerId, Instant startsAt) {
        Objects.requireNonNull(startsAt, "startsAt");
        return new AdvertisingPass(
                passId,
                ownerId,
                startsAt,
                startsAt.plus(terms.duration()),
                terms.broadcastLimit(),
                0,
                terms.ownerCooldown(),
                null,
                null);
    }

    public AdvertisementTransition submit(
            AdvertisingPass pass,
            UUID requestId,
            Instant submittedAt
    ) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (!pass.isActiveAt(submittedAt)) {
            throw rejected(AdvertisingPolicyException.Reason.PASS_NOT_ACTIVE,
                    "Advertising Pass is not active");
        }
        if (pass.openRequestId() != null) {
            throw rejected(AdvertisingPolicyException.Reason.OPEN_REQUEST_EXISTS,
                    "Advertising Pass already has an open request");
        }
        if (pass.broadcastsUsed() >= pass.broadcastLimit()) {
            throw rejected(AdvertisingPolicyException.Reason.NO_ALLOWANCE,
                    "Advertising Pass has no broadcasts left");
        }

        final Instant eligibleAt = pass.lastBroadcastAt() == null
                ? submittedAt
                : laterOf(submittedAt, pass.lastBroadcastAt().plus(pass.ownerCooldown()));
        final AdvertisementRequest request = new AdvertisementRequest(
                requestId,
                pass.ownerId(),
                pass.id(),
                AdvertisementRequestStatus.QUEUED,
                submittedAt,
                eligibleAt,
                null);
        return new AdvertisementTransition(withOpenRequest(pass, requestId), request);
    }

    public AdvertisementTransition cancel(
            AdvertisingPass pass,
            AdvertisementRequest request,
            Instant cancelledAt
    ) {
        verifyOpenRequest(pass, request);
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        final AdvertisementRequest cancelled = new AdvertisementRequest(
                request.id(),
                request.ownerId(),
                request.passId(),
                AdvertisementRequestStatus.CANCELLED,
                request.submittedAt(),
                request.eligibleAt(),
                cancelledAt);
        return new AdvertisementTransition(withOpenRequest(pass, null), cancelled);
    }

    public AdvertisementTransition broadcast(
            AdvertisingPass pass,
            AdvertisementRequest request,
            Instant broadcastAt
    ) {
        verifyOpenRequest(pass, request);
        Objects.requireNonNull(broadcastAt, "broadcastAt");
        if (broadcastAt.isBefore(request.eligibleAt())) {
            throw rejected(AdvertisingPolicyException.Reason.OWNER_COOLDOWN,
                    "Owner broadcast cooldown has not elapsed");
        }

        final AdvertisementRequest broadcast = new AdvertisementRequest(
                request.id(),
                request.ownerId(),
                request.passId(),
                AdvertisementRequestStatus.BROADCAST,
                request.submittedAt(),
                request.eligibleAt(),
                broadcastAt);
        final AdvertisingPass spent = new AdvertisingPass(
                pass.id(),
                pass.ownerId(),
                pass.startsAt(),
                pass.expiresAt(),
                pass.broadcastLimit(),
                pass.broadcastsUsed() + 1,
                pass.ownerCooldown(),
                broadcastAt,
                null);
        return new AdvertisementTransition(spent, broadcast);
    }

    private void verifyOpenRequest(AdvertisingPass pass, AdvertisementRequest request) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(request, "request");
        if (!pass.id().equals(request.passId())
                || !pass.ownerId().equals(request.ownerId())
                || !request.id().equals(pass.openRequestId())) {
            throw rejected(AdvertisingPolicyException.Reason.REQUEST_MISMATCH,
                    "Request is not reserved by this pass");
        }
        if (!request.status().isOpen()) {
            throw rejected(AdvertisingPolicyException.Reason.REQUEST_NOT_OPEN,
                    "Advertisement Request is already closed");
        }
    }

    private AdvertisingPass withOpenRequest(AdvertisingPass pass, UUID openRequestId) {
        return new AdvertisingPass(
                pass.id(),
                pass.ownerId(),
                pass.startsAt(),
                pass.expiresAt(),
                pass.broadcastLimit(),
                pass.broadcastsUsed(),
                pass.ownerCooldown(),
                pass.lastBroadcastAt(),
                openRequestId);
    }

    private static Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static AdvertisingPolicyException rejected(
            AdvertisingPolicyException.Reason reason,
            String message
    ) {
        return new AdvertisingPolicyException(reason, message);
    }
}
