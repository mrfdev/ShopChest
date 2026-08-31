package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.advertising.AdvertisementRequest;
import de.epiceric.shopchest.advertising.AdvertisementQueueFullException;
import de.epiceric.shopchest.advertising.AdvertisementRequestStatus;
import de.epiceric.shopchest.advertising.AdvertisementTransition;
import de.epiceric.shopchest.advertising.AdvertisingPass;
import de.epiceric.shopchest.advertising.AdvertisingPassPurchase;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseDeliveryRejectedException;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseStatus;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.Callback;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable Advertising Pass and FIFO request persistence shared by SQLite/MySQL. */
public final class JdbcAdvertisingRepository {

    private static final String PASSES_SUFFIX = "advertising_passes";
    private static final String REQUESTS_SUFFIX = "advertisement_requests";
    private static final String DISPATCH_SUFFIX = "advertising_dispatch_state";
    private static final String QUEUE_SUFFIX = "advertising_queue_state";
    private static final String PURCHASES_SUFFIX = "advertising_pass_purchases";
    public static final int DEFAULT_QUEUE_CAPACITY = 100;

    private final ShopChest plugin;
    private final Database database;

    public JdbcAdvertisingRepository(ShopChest plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public static void initialize(Connection connection, String prefix) throws SQLException {
        final String passes = table(prefix, PASSES_SUFFIX);
        final String requests = table(prefix, REQUESTS_SUFFIX);
        final String dispatch = table(prefix, DISPATCH_SUFFIX);
        final String queue = table(prefix, QUEUE_SUFFIX);
        final String purchases = table(prefix, PURCHASES_SUFFIX);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + passes + " ("
                    + "owner_uuid VARCHAR(36) PRIMARY KEY NOT NULL,"
                    + "pass_id VARCHAR(36) UNIQUE NOT NULL,"
                    + "purchase_nonce VARCHAR(128) UNIQUE NOT NULL,"
                    + "starts_at BIGINT NOT NULL,"
                    + "expires_at BIGINT NOT NULL,"
                    + "broadcast_limit INTEGER NOT NULL,"
                    + "broadcasts_used INTEGER NOT NULL,"
                    + "owner_cooldown_ms BIGINT NOT NULL,"
                    + "last_broadcast_at BIGINT,"
                    + "open_request_id VARCHAR(36))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + requests + " ("
                    + "request_id VARCHAR(36) PRIMARY KEY NOT NULL,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "pass_id VARCHAR(36) NOT NULL,"
                    + "status VARCHAR(16) NOT NULL,"
                    + "submitted_at BIGINT NOT NULL,"
                    + "eligible_at BIGINT NOT NULL,"
                    + "closed_at BIGINT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + dispatch + " ("
                    + "singleton_id INTEGER PRIMARY KEY NOT NULL,"
                    + "next_broadcast_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + queue + " ("
                    + "singleton_id INTEGER PRIMARY KEY NOT NULL,"
                    + "open_count INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + purchases + " ("
                    + "purchase_nonce VARCHAR(128) PRIMARY KEY NOT NULL,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "open_owner_uuid VARCHAR(36) UNIQUE,"
                    + "pass_id VARCHAR(36) NOT NULL,"
                    + "pass_starts_at BIGINT NOT NULL,"
                    + "pass_expires_at BIGINT NOT NULL,"
                    + "pass_broadcast_limit INTEGER NOT NULL,"
                    + "pass_broadcasts_used INTEGER NOT NULL,"
                    + "pass_owner_cooldown_ms BIGINT NOT NULL,"
                    + "pass_last_broadcast_at BIGINT,"
                    + "pass_open_request_id VARCHAR(36),"
                    + "status VARCHAR(32) NOT NULL,"
                    + "escrow_payload LONGTEXT NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL,"
                    + "failure LONGTEXT)");
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT singleton_id FROM " + dispatch + " WHERE singleton_id=1");
                ResultSet resultSet = select.executeQuery()) {
            if (!resultSet.next()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + dispatch
                                + " (singleton_id,next_broadcast_at) VALUES(1,0)")) {
                    insert.executeUpdate();
                } catch (SQLException race) {
                    // A second server may have initialized the singleton first.
                    try (PreparedStatement verify = connection.prepareStatement(
                            "SELECT singleton_id FROM " + dispatch + " WHERE singleton_id=1");
                            ResultSet verified = verify.executeQuery()) {
                        if (!verified.next()) {
                            throw race;
                        }
                    }
                }
            }
        }
        initializeQueueState(connection, requests, queue);
    }

    public static Optional<AdvertisingPass> findPass(
            Connection connection,
            String prefix,
            UUID ownerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pass_id,starts_at,expires_at,broadcast_limit,broadcasts_used,"
                        + "owner_cooldown_ms,last_broadcast_at,open_request_id FROM "
                        + table(prefix, PASSES_SUFFIX) + " WHERE owner_uuid=?")) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readPass(resultSet, ownerId))
                        : Optional.empty();
            }
        }
    }

    public static AdvertisingPass issuePass(
            Connection connection,
            String prefix,
            AdvertisingPass pass,
            String purchaseNonce
    ) throws SQLException {
        if (purchaseNonce == null || purchaseNonce.isBlank() || purchaseNonce.length() > 128) {
            throw new IllegalArgumentException("Invalid purchase nonce");
        }
        final String table = table(prefix, PASSES_SUFFIX);
        final boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement byNonce = connection.prepareStatement(
                    "SELECT owner_uuid,pass_id,starts_at,expires_at,broadcast_limit,"
                            + "broadcasts_used,owner_cooldown_ms,last_broadcast_at,"
                            + "open_request_id FROM " + table + " WHERE purchase_nonce=?")) {
                byNonce.setString(1, purchaseNonce);
                try (ResultSet resultSet = byNonce.executeQuery()) {
                    if (resultSet.next()) {
                        final UUID existingOwner = UUID.fromString(
                                resultSet.getString("owner_uuid"));
                        if (!existingOwner.equals(pass.ownerId())) {
                            throw new SQLException("Purchase nonce belongs to another owner");
                        }
                        final AdvertisingPass existing = readPass(resultSet, existingOwner);
                        connection.commit();
                        return existing;
                    }
                }
            }

            final Optional<AdvertisingPass> existing = findPass(connection, prefix, pass.ownerId());
            if (existing.isPresent()) {
                final AdvertisingPass current = existing.orElseThrow();
                if (current.isActiveAt(pass.startsAt()) || current.openRequestId() != null) {
                    throw new IllegalStateException("Owner already has an active Advertising Pass");
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE owner_uuid=?")) {
                    delete.setString(1, pass.ownerId().toString());
                    delete.executeUpdate();
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table
                            + " (owner_uuid,pass_id,purchase_nonce,starts_at,expires_at,"
                            + "broadcast_limit,broadcasts_used,owner_cooldown_ms,"
                            + "last_broadcast_at,open_request_id) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                bindPass(insert, pass, purchaseNonce);
                insert.executeUpdate();
            }
            connection.commit();
            return pass;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public static Optional<AdvertisingPassPurchase> findPurchase(
            Connection connection,
            String prefix,
            String nonce
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT purchase_nonce,owner_uuid,pass_id,pass_starts_at,pass_expires_at,"
                        + "pass_broadcast_limit,pass_broadcasts_used,pass_owner_cooldown_ms,"
                        + "pass_last_broadcast_at,pass_open_request_id,status,escrow_payload,"
                        + "created_at,updated_at,failure FROM "
                        + table(prefix, PURCHASES_SUFFIX) + " WHERE purchase_nonce=?")) {
            statement.setString(1, nonce);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readPurchase(resultSet))
                        : Optional.empty();
            }
        }
    }

    public static Optional<AdvertisingPassPurchase> findUnresolvedPurchase(
            Connection connection,
            String prefix,
            UUID ownerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT purchase_nonce,owner_uuid,pass_id,pass_starts_at,pass_expires_at,"
                        + "pass_broadcast_limit,pass_broadcasts_used,pass_owner_cooldown_ms,"
                        + "pass_last_broadcast_at,pass_open_request_id,status,escrow_payload,"
                        + "created_at,updated_at,failure FROM "
                        + table(prefix, PURCHASES_SUFFIX) + " WHERE open_owner_uuid=?")) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readPurchase(resultSet))
                        : Optional.empty();
            }
        }
    }

    public static AdvertisingPassPurchase preparePurchase(
            Connection connection,
            String prefix,
            AdvertisingPassPurchase purchase
    ) throws SQLException {
        if (purchase.status() != AdvertisingPurchaseStatus.PREPARED) {
            throw new IllegalArgumentException("Only PREPARED purchases may be inserted");
        }
        final boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            final Optional<AdvertisingPassPurchase> found =
                    findPurchase(connection, prefix, purchase.nonce());
            if (found.isPresent()) {
                final AdvertisingPassPurchase existing = found.orElseThrow();
                verifySamePurchase(existing, purchase);
                connection.commit();
                return existing;
            }

            final Optional<AdvertisingPass> current =
                    findPass(connection, prefix, purchase.ownerId());
            if (current.isPresent()
                    && (passesOverlap(current.orElseThrow(), purchase.pass())
                    || current.orElseThrow().openRequestId() != null)) {
                throw new AdvertisingPurchaseDeliveryRejectedException(
                        "Owner already has an overlapping Advertising Pass");
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table(prefix, PURCHASES_SUFFIX)
                            + " (purchase_nonce,owner_uuid,open_owner_uuid,pass_id,"
                            + "pass_starts_at,pass_expires_at,pass_broadcast_limit,"
                            + "pass_broadcasts_used,pass_owner_cooldown_ms,"
                            + "pass_last_broadcast_at,pass_open_request_id,status,"
                            + "escrow_payload,created_at,updated_at,failure)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                bindPurchase(insert, purchase);
                insert.executeUpdate();
            }
            connection.commit();
            return purchase;
        } catch (SQLException exception) {
            connection.rollback();
            final Optional<AdvertisingPassPurchase> raced =
                    findPurchase(connection, prefix, purchase.nonce());
            if (raced.isPresent()) {
                verifySamePurchase(raced.orElseThrow(), purchase);
                return raced.orElseThrow();
            }
            throw exception;
        } catch (RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public static AdvertisingPass deliverPreparedPurchase(
            Connection connection,
            String prefix,
            String nonce
    ) throws SQLException {
        final boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            final AdvertisingPassPurchase purchase = findPurchase(connection, prefix, nonce)
                    .orElseThrow(() -> new AdvertisingPurchaseDeliveryRejectedException(
                            "Prepared advertising purchase does not exist"));
            final Optional<AdvertisingPass> byNonce =
                    findPassByPurchaseNonce(connection, prefix, nonce);
            if (purchase.status() == AdvertisingPurchaseStatus.DELIVERED) {
                final AdvertisingPass delivered = byNonce.orElseThrow(
                        () -> new IllegalStateException(
                                "Delivered purchase is missing its Advertising Pass"));
                connection.commit();
                return delivered;
            }
            if (purchase.status() != AdvertisingPurchaseStatus.PREPARED) {
                throw new AdvertisingPurchaseDeliveryRejectedException(
                        "Purchase is no longer eligible for pass delivery");
            }
            if (byNonce.isPresent()) {
                final AdvertisingPass delivered = byNonce.orElseThrow();
                if (!delivered.id().equals(purchase.pass().id())
                        || !delivered.ownerId().equals(purchase.ownerId())) {
                    throw new AdvertisingPurchaseDeliveryRejectedException(
                            "Purchase nonce belongs to another Advertising Pass");
                }
                markDelivered(connection, prefix, nonce, Instant.now());
                connection.commit();
                return delivered;
            }

            final Optional<AdvertisingPass> current =
                    findPass(connection, prefix, purchase.ownerId());
            if (current.isPresent()) {
                final AdvertisingPass existing = current.orElseThrow();
                if (passesOverlap(existing, purchase.pass()) || existing.openRequestId() != null) {
                    throw new AdvertisingPurchaseDeliveryRejectedException(
                            "Another Advertising Pass prevents delivery");
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + table(prefix, PASSES_SUFFIX)
                                + " WHERE owner_uuid=? AND pass_id=?")) {
                    delete.setString(1, existing.ownerId().toString());
                    delete.setString(2, existing.id().toString());
                    delete.executeUpdate();
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table(prefix, PASSES_SUFFIX)
                            + " (owner_uuid,pass_id,purchase_nonce,starts_at,expires_at,"
                            + "broadcast_limit,broadcasts_used,owner_cooldown_ms,"
                            + "last_broadcast_at,open_request_id) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                bindPass(insert, purchase.pass(), nonce);
                insert.executeUpdate();
            }
            markDelivered(connection, prefix, nonce, Instant.now());
            connection.commit();
            return purchase.pass();
        } catch (SQLException exception) {
            connection.rollback();
            try {
                final Optional<AdvertisingPassPurchase> raced =
                        findPurchase(connection, prefix, nonce);
                final Optional<AdvertisingPass> delivered =
                        findPassByPurchaseNonce(connection, prefix, nonce);
                if (raced.isPresent()
                        && raced.orElseThrow().status() == AdvertisingPurchaseStatus.DELIVERED
                        && delivered.isPresent()) {
                    return delivered.orElseThrow();
                }
            } catch (SQLException recoveryFailure) {
                exception.addSuppressed(recoveryFailure);
            }
            throw exception;
        } catch (RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public static void markRefundPending(
            Connection connection,
            String prefix,
            String nonce,
            String failure
    ) throws SQLException {
        transitionPurchase(
                connection,
                prefix,
                nonce,
                AdvertisingPurchaseStatus.PREPARED,
                AdvertisingPurchaseStatus.REFUND_PENDING,
                false,
                failure);
    }

    public static void markNotCharged(
            Connection connection,
            String prefix,
            String nonce
    ) throws SQLException {
        transitionPurchase(
                connection,
                prefix,
                nonce,
                AdvertisingPurchaseStatus.PREPARED,
                AdvertisingPurchaseStatus.NOT_CHARGED,
                true,
                null);
    }

    public static void markRefunded(
            Connection connection,
            String prefix,
            String nonce
    ) throws SQLException {
        transitionPurchase(
                connection,
                prefix,
                nonce,
                AdvertisingPurchaseStatus.REFUND_PENDING,
                AdvertisingPurchaseStatus.REFUNDED,
                true,
                null);
    }

    public static Optional<AdvertisementRequest> findOpenRequest(
            Connection connection,
            String prefix,
            UUID ownerId
    ) throws SQLException {
        final String query = "SELECT r.request_id,r.owner_uuid,r.pass_id,r.status,"
                + "r.submitted_at,r.eligible_at,r.closed_at FROM "
                + table(prefix, REQUESTS_SUFFIX) + " r JOIN "
                + table(prefix, PASSES_SUFFIX)
                + " p ON p.open_request_id=r.request_id WHERE p.owner_uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readRequest(resultSet))
                        : Optional.empty();
            }
        }
    }

    public static Optional<AdvertisementRequest> findNextEligibleRequest(
            Connection connection,
            String prefix,
            Instant now
    ) throws SQLException {
        final String query = "SELECT request_id,owner_uuid,pass_id,status,submitted_at,"
                + "eligible_at,closed_at FROM " + table(prefix, REQUESTS_SUFFIX)
                + " WHERE status=? AND eligible_at<=? ORDER BY submitted_at,request_id";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, AdvertisementRequestStatus.QUEUED.name());
            statement.setLong(2, now.toEpochMilli());
            statement.setMaxRows(1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readRequest(resultSet))
                        : Optional.empty();
            }
        }
    }

    public static void saveTransition(
            Connection connection,
            String prefix,
            AdvertisementTransition transition
    ) throws SQLException {
        saveTransition(connection, prefix, transition, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Atomically admits a QUEUED request against the durable global capacity, or closes an
     * existing request and returns its capacity. No request can be persisted without its pass
     * reservation and counter mutation in the same transaction.
     */
    public static void saveTransition(
            Connection connection,
            String prefix,
            AdvertisementTransition transition,
            int queueCapacity
    ) throws SQLException {
        if (queueCapacity <= 0 || queueCapacity > 100_000) {
            throw new IllegalArgumentException("Advertising queue capacity is invalid");
        }
        final boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (transition.request().status() == AdvertisementRequestStatus.QUEUED) {
                admitQueuedTransition(connection, prefix, transition, queueCapacity);
            } else {
                closeQueuedTransition(connection, prefix, transition);
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public static void parkRequest(
            Connection connection,
            String prefix,
            AdvertisementRequest request,
            Instant eligibleAt
    ) throws SQLException {
        if (eligibleAt.isBefore(request.submittedAt())) {
            throw new IllegalArgumentException("Park time precedes request submission");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table(prefix, REQUESTS_SUFFIX)
                        + " SET eligible_at=? WHERE request_id=? AND status=?")) {
            statement.setLong(1, eligibleAt.toEpochMilli());
            statement.setString(2, request.id().toString());
            statement.setString(3, AdvertisementRequestStatus.QUEUED.name());
            statement.executeUpdate();
        }
    }

    public static Instant globalNextBroadcastAt(
            Connection connection,
            String prefix
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT next_broadcast_at FROM " + table(prefix, DISPATCH_SUFFIX)
                        + " WHERE singleton_id=1");
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Advertising dispatch singleton is missing");
            }
            return Instant.ofEpochMilli(resultSet.getLong(1));
        }
    }

    /**
     * Claims one public broadcast at most once. The request and allowance become terminal before
     * callers emit chat/title/sound side effects. A true result must never be publicly retried;
     * this deliberately prefers a rare missed ad after a crash over a duplicated global ad.
     */
    public static boolean commitBroadcast(
            Connection connection,
            String prefix,
            AdvertisingPass before,
            AdvertisementRequest request,
            AdvertisementTransition transition,
            Instant broadcastAt,
            Instant globalNext
    ) throws SQLException {
        final boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement dispatch = connection.prepareStatement(
                    "UPDATE " + table(prefix, DISPATCH_SUFFIX)
                            + " SET next_broadcast_at=? WHERE singleton_id=1"
                            + " AND next_broadcast_at<=?")) {
                dispatch.setLong(1, globalNext.toEpochMilli());
                dispatch.setLong(2, broadcastAt.toEpochMilli());
                if (dispatch.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement passUpdate = connection.prepareStatement(
                    "UPDATE " + table(prefix, PASSES_SUFFIX)
                            + " SET broadcasts_used=?,last_broadcast_at=?,open_request_id=NULL"
                            + " WHERE owner_uuid=? AND pass_id=? AND open_request_id=?"
                            + " AND broadcasts_used=?")) {
                passUpdate.setInt(1, transition.pass().broadcastsUsed());
                passUpdate.setLong(2, broadcastAt.toEpochMilli());
                passUpdate.setString(3, before.ownerId().toString());
                passUpdate.setString(4, before.id().toString());
                passUpdate.setString(5, request.id().toString());
                passUpdate.setInt(6, before.broadcastsUsed());
                if (passUpdate.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement requestUpdate = connection.prepareStatement(
                    "UPDATE " + table(prefix, REQUESTS_SUFFIX)
                            + " SET status=?,closed_at=? WHERE request_id=? AND status=?")) {
                requestUpdate.setString(1, AdvertisementRequestStatus.BROADCAST.name());
                requestUpdate.setLong(2, broadcastAt.toEpochMilli());
                requestUpdate.setString(3, request.id().toString());
                requestUpdate.setString(4, AdvertisementRequestStatus.QUEUED.name());
                if (requestUpdate.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            decrementQueueCount(connection, prefix);
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public void findPass(UUID ownerId, Callback<Optional<AdvertisingPass>> callback) {
        runAsync(callback, connection -> findPass(connection, Config.databaseTablePrefix, ownerId));
    }

    public void issuePass(
            AdvertisingPass pass,
            String nonce,
            Callback<AdvertisingPass> callback
    ) {
        runAsync(callback, connection -> issuePass(
                connection, Config.databaseTablePrefix, pass, nonce));
    }

    public void findPurchase(
            String nonce,
            Callback<Optional<AdvertisingPassPurchase>> callback
    ) {
        runAsync(callback, connection -> findPurchase(
                connection, Config.databaseTablePrefix, nonce));
    }

    public void findUnresolvedPurchase(
            UUID ownerId,
            Callback<Optional<AdvertisingPassPurchase>> callback
    ) {
        runAsync(callback, connection -> findUnresolvedPurchase(
                connection, Config.databaseTablePrefix, ownerId));
    }

    public void preparePurchase(
            AdvertisingPassPurchase purchase,
            Callback<AdvertisingPassPurchase> callback
    ) {
        runAsync(callback, connection -> preparePurchase(
                connection, Config.databaseTablePrefix, purchase));
    }

    public void deliverPreparedPurchase(
            String nonce,
            Callback<AdvertisingPass> callback
    ) {
        runAsync(callback, connection -> deliverPreparedPurchase(
                connection, Config.databaseTablePrefix, nonce));
    }

    public void markRefundPending(
            String nonce,
            String failure,
            Callback<Void> callback
    ) {
        runAsync(callback, connection -> {
            markRefundPending(connection, Config.databaseTablePrefix, nonce, failure);
            return null;
        });
    }

    public void markNotCharged(String nonce, Callback<Void> callback) {
        runAsync(callback, connection -> {
            markNotCharged(connection, Config.databaseTablePrefix, nonce);
            return null;
        });
    }

    public void markRefunded(String nonce, Callback<Void> callback) {
        runAsync(callback, connection -> {
            markRefunded(connection, Config.databaseTablePrefix, nonce);
            return null;
        });
    }

    public void findOpenRequest(
            UUID ownerId,
            Callback<Optional<AdvertisementRequest>> callback
    ) {
        runAsync(callback, connection -> findOpenRequest(
                connection, Config.databaseTablePrefix, ownerId));
    }

    public void findNextEligibleRequest(
            Instant now,
            Callback<Optional<AdvertisementRequest>> callback
    ) {
        runAsync(callback, connection -> findNextEligibleRequest(
                connection, Config.databaseTablePrefix, now));
    }

    public void saveTransition(AdvertisementTransition transition, Callback<Void> callback) {
        runAsync(callback, connection -> {
            saveTransition(connection, Config.databaseTablePrefix, transition);
            return null;
        });
    }

    public void parkRequest(
            AdvertisementRequest request,
            Instant eligibleAt,
            Callback<Void> callback
    ) {
        runAsync(callback, connection -> {
            parkRequest(connection, Config.databaseTablePrefix, request, eligibleAt);
            return null;
        });
    }

    public void globalNextBroadcastAt(Callback<Instant> callback) {
        runAsync(callback, connection -> globalNextBroadcastAt(
                connection, Config.databaseTablePrefix));
    }

    public void commitBroadcast(
            AdvertisingPass before,
            AdvertisementRequest request,
            AdvertisementTransition transition,
            Instant broadcastAt,
            Instant globalNext,
            Callback<Boolean> callback
    ) {
        runAsync(callback, connection -> commitBroadcast(
                connection,
                Config.databaseTablePrefix,
                before,
                request,
                transition,
                broadcastAt,
                globalNext));
    }

    private <T> void runAsync(Callback<T> callback, SqlOperation<T> operation) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (database.dataSource == null || database.dataSource.isClosed()) {
                    callback.callSyncError(new IllegalStateException(
                            "Shop database is not currently available"));
                    return;
                }
                try (Connection connection = database.dataSource.getConnection()) {
                    callback.callSyncResult(operation.run(connection));
                } catch (SQLException | RuntimeException exception) {
                    plugin.debug(exception);
                    callback.callSyncError(exception);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private static void bindPass(
            PreparedStatement statement,
            AdvertisingPass pass,
            String nonce
    ) throws SQLException {
        statement.setString(1, pass.ownerId().toString());
        statement.setString(2, pass.id().toString());
        statement.setString(3, nonce);
        statement.setLong(4, pass.startsAt().toEpochMilli());
        statement.setLong(5, pass.expiresAt().toEpochMilli());
        statement.setInt(6, pass.broadcastLimit());
        statement.setInt(7, pass.broadcastsUsed());
        statement.setLong(8, pass.ownerCooldown().toMillis());
        setNullableInstant(statement, 9, pass.lastBroadcastAt());
        statement.setString(10, pass.openRequestId() == null
                ? null : pass.openRequestId().toString());
    }

    private static void bindPurchase(
            PreparedStatement statement,
            AdvertisingPassPurchase purchase
    ) throws SQLException {
        final AdvertisingPass pass = purchase.pass();
        statement.setString(1, purchase.nonce());
        statement.setString(2, purchase.ownerId().toString());
        statement.setString(3, purchase.status().isTerminal()
                ? null : purchase.ownerId().toString());
        statement.setString(4, pass.id().toString());
        statement.setLong(5, pass.startsAt().toEpochMilli());
        statement.setLong(6, pass.expiresAt().toEpochMilli());
        statement.setInt(7, pass.broadcastLimit());
        statement.setInt(8, pass.broadcastsUsed());
        statement.setLong(9, pass.ownerCooldown().toMillis());
        setNullableInstant(statement, 10, pass.lastBroadcastAt());
        statement.setString(11, pass.openRequestId() == null
                ? null : pass.openRequestId().toString());
        statement.setString(12, purchase.status().name());
        statement.setString(13, purchase.escrowPayload());
        statement.setLong(14, purchase.createdAt().toEpochMilli());
        statement.setLong(15, purchase.updatedAt().toEpochMilli());
        statement.setString(16, purchase.failure());
    }

    private static AdvertisingPassPurchase readPurchase(ResultSet resultSet) throws SQLException {
        final UUID ownerId = UUID.fromString(resultSet.getString("owner_uuid"));
        final long last = resultSet.getLong("pass_last_broadcast_at");
        final boolean lastWasNull = resultSet.wasNull();
        final String openRequest = resultSet.getString("pass_open_request_id");
        final AdvertisingPass pass = new AdvertisingPass(
                UUID.fromString(resultSet.getString("pass_id")),
                ownerId,
                Instant.ofEpochMilli(resultSet.getLong("pass_starts_at")),
                Instant.ofEpochMilli(resultSet.getLong("pass_expires_at")),
                resultSet.getInt("pass_broadcast_limit"),
                resultSet.getInt("pass_broadcasts_used"),
                Duration.ofMillis(resultSet.getLong("pass_owner_cooldown_ms")),
                lastWasNull ? null : Instant.ofEpochMilli(last),
                openRequest == null ? null : UUID.fromString(openRequest));
        return new AdvertisingPassPurchase(
                resultSet.getString("purchase_nonce"),
                ownerId,
                pass,
                AdvertisingPurchaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("escrow_payload"),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                Instant.ofEpochMilli(resultSet.getLong("updated_at")),
                resultSet.getString("failure"));
    }

    private static Optional<AdvertisingPass> findPassByPurchaseNonce(
            Connection connection,
            String prefix,
            String nonce
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid,pass_id,starts_at,expires_at,broadcast_limit,"
                        + "broadcasts_used,owner_cooldown_ms,last_broadcast_at,open_request_id"
                        + " FROM " + table(prefix, PASSES_SUFFIX) + " WHERE purchase_nonce=?")) {
            statement.setString(1, nonce);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPass(
                        resultSet, UUID.fromString(resultSet.getString("owner_uuid"))));
            }
        }
    }

    private static void markDelivered(
            Connection connection,
            String prefix,
            String nonce,
            Instant updatedAt
    ) throws SQLException {
        transitionPurchase(
                connection,
                prefix,
                nonce,
                AdvertisingPurchaseStatus.PREPARED,
                AdvertisingPurchaseStatus.DELIVERED,
                true,
                null,
                updatedAt);
    }

    private static void transitionPurchase(
            Connection connection,
            String prefix,
            String nonce,
            AdvertisingPurchaseStatus from,
            AdvertisingPurchaseStatus to,
            boolean terminal,
            String failure
    ) throws SQLException {
        transitionPurchase(connection, prefix, nonce, from, to, terminal, failure, Instant.now());
    }

    private static void transitionPurchase(
            Connection connection,
            String prefix,
            String nonce,
            AdvertisingPurchaseStatus from,
            AdvertisingPurchaseStatus to,
            boolean terminal,
            String failure,
            Instant updatedAt
    ) throws SQLException {
        final String openOwnerAssignment = terminal ? "NULL" : "owner_uuid";
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table(prefix, PURCHASES_SUFFIX)
                        + " SET status=?,open_owner_uuid=" + openOwnerAssignment
                        + ",updated_at=?,failure=COALESCE(?,failure)"
                        + " WHERE purchase_nonce=? AND status=?")) {
            statement.setString(1, to.name());
            statement.setLong(2, updatedAt.toEpochMilli());
            statement.setString(3, failure);
            statement.setString(4, nonce);
            statement.setString(5, from.name());
            if (statement.executeUpdate() == 1) {
                return;
            }
        }
        final AdvertisingPassPurchase current = findPurchase(connection, prefix, nonce)
                .orElseThrow(() -> new AdvertisingPurchaseDeliveryRejectedException(
                        "Advertising purchase does not exist"));
        if (current.status() != to) {
            throw new AdvertisingPurchaseDeliveryRejectedException(
                    "Advertising purchase changed from the expected state");
        }
    }

    private static void verifySamePurchase(
            AdvertisingPassPurchase existing,
            AdvertisingPassPurchase expected
    ) {
        if (!existing.ownerId().equals(expected.ownerId())
                || !existing.pass().equals(expected.pass())
                || !existing.escrowPayload().equals(expected.escrowPayload())) {
            throw new AdvertisingPurchaseDeliveryRejectedException(
                    "Purchase nonce belongs to different escrow evidence");
        }
    }

    private static boolean passesOverlap(AdvertisingPass first, AdvertisingPass second) {
        return first.expiresAt().isAfter(second.startsAt())
                && second.expiresAt().isAfter(first.startsAt());
    }

    private static void admitQueuedTransition(
            Connection connection,
            String prefix,
            AdvertisementTransition transition,
            int queueCapacity
    ) throws SQLException {
        final AdvertisementRequest request = transition.request();
        final AdvertisingPass pass = transition.pass();
        if (!request.status().isOpen()
                || pass.openRequestId() == null
                || !pass.openRequestId().equals(request.id())) {
            throw new IllegalArgumentException("Queued request is not reserved by its pass");
        }
        final Optional<AdvertisementRequest> existing = findRequestById(
                connection, prefix, request.id());
        if (existing.isPresent()) {
            final Optional<AdvertisingPass> existingPass = findPass(
                    connection, prefix, request.ownerId());
            if (existing.orElseThrow().equals(request)
                    && existingPass.isPresent()
                    && request.id().equals(existingPass.orElseThrow().openRequestId())) {
                return;
            }
            throw new IllegalStateException(
                    "Advertisement request identity belongs to different state");
        }

        try (PreparedStatement capacity = connection.prepareStatement(
                "UPDATE " + table(prefix, QUEUE_SUFFIX)
                        + " SET open_count=open_count+1"
                        + " WHERE singleton_id=1 AND open_count<?")) {
            capacity.setInt(1, queueCapacity);
            if (capacity.executeUpdate() != 1) {
                throw new AdvertisementQueueFullException(
                        "The advertisement queue is full; try again after another ad runs");
            }
        }

        insertRequest(connection, prefix, request);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table(prefix, PASSES_SUFFIX)
                        + " SET open_request_id=?"
                        + " WHERE owner_uuid=? AND pass_id=? AND open_request_id IS NULL"
                        + " AND broadcasts_used=? AND starts_at<=? AND expires_at>?"
                        + " AND broadcast_limit>broadcasts_used")) {
            statement.setString(1, request.id().toString());
            statement.setString(2, pass.ownerId().toString());
            statement.setString(3, pass.id().toString());
            statement.setInt(4, pass.broadcastsUsed());
            statement.setLong(5, request.submittedAt().toEpochMilli());
            statement.setLong(6, request.submittedAt().toEpochMilli());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Advertising Pass changed before queue admission");
            }
        }
    }

    private static void closeQueuedTransition(
            Connection connection,
            String prefix,
            AdvertisementTransition transition
    ) throws SQLException {
        final AdvertisementRequest request = transition.request();
        final AdvertisingPass pass = transition.pass();
        if (request.status().isOpen() || pass.openRequestId() != null) {
            throw new IllegalArgumentException("Closed request transition is inconsistent");
        }
        try (PreparedStatement requestUpdate = connection.prepareStatement(
                "UPDATE " + table(prefix, REQUESTS_SUFFIX)
                        + " SET status=?,closed_at=? WHERE request_id=? AND owner_uuid=?"
                        + " AND pass_id=? AND status=?")) {
            requestUpdate.setString(1, request.status().name());
            setNullableInstant(requestUpdate, 2, request.closedAt());
            requestUpdate.setString(3, request.id().toString());
            requestUpdate.setString(4, request.ownerId().toString());
            requestUpdate.setString(5, request.passId().toString());
            requestUpdate.setString(6, AdvertisementRequestStatus.QUEUED.name());
            if (requestUpdate.executeUpdate() != 1) {
                final Optional<AdvertisementRequest> existing = findRequestById(
                        connection, prefix, request.id());
                final Optional<AdvertisingPass> existingPass = findPass(
                        connection, prefix, request.ownerId());
                if (existing.isPresent()
                        && existing.orElseThrow().equals(request)
                        && existingPass.isPresent()
                        && existingPass.orElseThrow().openRequestId() == null) {
                    return;
                }
                throw new IllegalStateException(
                        "Advertisement request changed before it could be closed");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table(prefix, PASSES_SUFFIX)
                        + " SET broadcasts_used=?,last_broadcast_at=?,open_request_id=NULL"
                        + " WHERE owner_uuid=? AND pass_id=? AND open_request_id=?")) {
            statement.setInt(1, pass.broadcastsUsed());
            setNullableInstant(statement, 2, pass.lastBroadcastAt());
            statement.setString(3, pass.ownerId().toString());
            statement.setString(4, pass.id().toString());
            statement.setString(5, request.id().toString());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Advertising Pass changed before its request could be closed");
            }
        }
        decrementQueueCount(connection, prefix);
    }

    private static void insertRequest(
            Connection connection,
            String prefix,
            AdvertisementRequest request
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table(prefix, REQUESTS_SUFFIX)
                        + " (request_id,owner_uuid,pass_id,status,submitted_at,eligible_at,closed_at)"
                        + " VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, request.id().toString());
            statement.setString(2, request.ownerId().toString());
            statement.setString(3, request.passId().toString());
            statement.setString(4, request.status().name());
            statement.setLong(5, request.submittedAt().toEpochMilli());
            statement.setLong(6, request.eligibleAt().toEpochMilli());
            setNullableInstant(statement, 7, request.closedAt());
            statement.executeUpdate();
        }
    }

    private static Optional<AdvertisementRequest> findRequestById(
            Connection connection,
            String prefix,
            UUID requestId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT request_id,owner_uuid,pass_id,status,submitted_at,eligible_at,closed_at"
                        + " FROM " + table(prefix, REQUESTS_SUFFIX)
                        + " WHERE request_id=?")) {
            statement.setString(1, requestId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readRequest(resultSet))
                        : Optional.empty();
            }
        }
    }

    private static void decrementQueueCount(
            Connection connection,
            String prefix
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table(prefix, QUEUE_SUFFIX)
                        + " SET open_count=open_count-1"
                        + " WHERE singleton_id=1 AND open_count>0")) {
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Advertising queue capacity state is inconsistent");
            }
        }
    }

    private static void initializeQueueState(
            Connection connection,
            String requestsTable,
            String queueTable
    ) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT singleton_id FROM " + queueTable + " WHERE singleton_id=1");
                ResultSet resultSet = select.executeQuery()) {
            if (resultSet.next()) {
                return;
            }
        }
        int queued;
        try (PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + requestsTable + " WHERE status=?")) {
            count.setString(1, AdvertisementRequestStatus.QUEUED.name());
            try (ResultSet resultSet = count.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Advertising queue count could not be initialized");
                }
                queued = resultSet.getInt(1);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + queueTable
                        + " (singleton_id,open_count) VALUES(1,?)")) {
            insert.setInt(1, queued);
            insert.executeUpdate();
        } catch (SQLException race) {
            try (PreparedStatement verify = connection.prepareStatement(
                    "SELECT singleton_id FROM " + queueTable + " WHERE singleton_id=1");
                    ResultSet resultSet = verify.executeQuery()) {
                if (!resultSet.next()) {
                    throw race;
                }
            }
        }
    }

    private static AdvertisingPass readPass(ResultSet resultSet, UUID ownerId) throws SQLException {
        final long last = resultSet.getLong("last_broadcast_at");
        final boolean lastWasNull = resultSet.wasNull();
        final String open = resultSet.getString("open_request_id");
        return new AdvertisingPass(
                UUID.fromString(resultSet.getString("pass_id")),
                ownerId,
                Instant.ofEpochMilli(resultSet.getLong("starts_at")),
                Instant.ofEpochMilli(resultSet.getLong("expires_at")),
                resultSet.getInt("broadcast_limit"),
                resultSet.getInt("broadcasts_used"),
                Duration.ofMillis(resultSet.getLong("owner_cooldown_ms")),
                lastWasNull ? null : Instant.ofEpochMilli(last),
                open == null ? null : UUID.fromString(open));
    }

    private static AdvertisementRequest readRequest(ResultSet resultSet) throws SQLException {
        final long closed = resultSet.getLong("closed_at");
        final boolean closedWasNull = resultSet.wasNull();
        return new AdvertisementRequest(
                UUID.fromString(resultSet.getString("request_id")),
                UUID.fromString(resultSet.getString("owner_uuid")),
                UUID.fromString(resultSet.getString("pass_id")),
                AdvertisementRequestStatus.valueOf(resultSet.getString("status")),
                Instant.ofEpochMilli(resultSet.getLong("submitted_at")),
                Instant.ofEpochMilli(resultSet.getLong("eligible_at")),
                closedWasNull ? null : Instant.ofEpochMilli(closed));
    }

    private static void setNullableInstant(
            PreparedStatement statement,
            int index,
            Instant instant
    ) throws SQLException {
        if (instant == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, instant.toEpochMilli());
        }
    }

    private static String table(String prefix, String suffix) {
        return JdbcStorefrontRepository.table(prefix, suffix);
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run(Connection connection) throws SQLException;
    }
}
