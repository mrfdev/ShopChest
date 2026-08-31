package de.epiceric.shopchest.advertising;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Persists the complete, administrator-captured currency ItemStack template. */
public final class AdvertisingCurrencyTemplateStore {

    private static final String FILE_NAME = "advertising-currency.yml";
    private static final ItemStackBinaryCodec STACK_CODEC = new ItemStackBinaryCodec();

    private final ShopChest plugin;
    private final AtomicReference<Authority> authority = new AtomicReference<>();
    private final AuthorityMutationGate mutationGate = new AuthorityMutationGate();
    private final Object mutationMonitor = new Object();
    private final ArrayDeque<Runnable> mutationQueue = new ArrayDeque<>();
    private boolean mutationWorkerRunning;
    private volatile boolean loaded;

    public AdvertisingCurrencyTemplateStore(ShopChest plugin) {
        this.plugin = plugin;
    }

    public void loadAsync(Runnable completion) {
        mutationGate.begin();
        enqueueIo(() -> {
            String document = null;
            Throwable failure = null;
            try {
                final Path source = file().toPath();
                if (Files.isRegularFile(source)) {
                    document = Files.readString(source, StandardCharsets.UTF_8);
                }
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }
            final String loadedDocument = document;
            final Throwable loadFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (loadFailure != null) {
                        throw new IllegalStateException(
                                "Advertising currency authority could not be read", loadFailure);
                    }
                    authority.set(decodeAuthority(loadedDocument));
                } catch (RuntimeException exception) {
                    authority.set(null);
                    plugin.getLogger().warning(
                            "Advertising currency template could not be loaded; advertising fails closed");
                    plugin.debug(exception);
                } finally {
                    loaded = true;
                    mutationGate.finish();
                    completion.run();
                }
            });
        });
    }

    public void captureAsync(
            ItemStack genuineToken,
            UUID administratorId,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        final ItemStack captured = normalized(genuineToken);
        if (captured == null) {
            failure.accept(new IllegalArgumentException(
                    "Hold one genuine AFK Shrine Token in your main hand"));
            return;
        }
        final byte[] serialized;
        final String document;
        try {
            serialized = STACK_CODEC.serialize(captured);
            document = AdvertisingCurrencyAuthorityCodec.encode(
                    serialized, administratorId, Instant.now());
        } catch (RuntimeException exception) {
            failure.accept(exception);
            return;
        }
        final Authority capturedAuthority = new Authority(captured, fingerprint(serialized));
        // Invalidate old snapshots before the durable mutation can race a prepared purchase.
        mutationGate.begin();
        enqueueIo(() -> {
            Path temporary = null;
            try {
                final Path target = file().toPath();
                Files.createDirectories(target.getParent());
                temporary = Files.createTempFile(
                        target.getParent(), ".advertising-currency-", ".tmp");
                Files.writeString(temporary, document, StandardCharsets.UTF_8);
                moveAtomically(temporary, target);
                temporary = null;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    authority.set(capturedAuthority.copy());
                    loaded = true;
                    mutationGate.finish();
                    success.run();
                });
            } catch (IOException | RuntimeException exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    mutationGate.finish();
                    failure.accept(exception);
                });
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException cleanupFailure) {
                        plugin.debug(cleanupFailure);
                    }
                }
            }
        });
    }

    public void clearAsync(Runnable success, Consumer<Throwable> failure) {
        // Fail closed for purchases prepared against the old authority while clear is in flight.
        mutationGate.begin();
        enqueueIo(() -> {
            try {
                Files.deleteIfExists(file().toPath());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    authority.set(null);
                    loaded = true;
                    mutationGate.finish();
                    success.run();
                });
            } catch (IOException | RuntimeException exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    mutationGate.finish();
                    failure.accept(exception);
                });
            }
        });
    }

    public Optional<ItemStack> template() {
        return snapshot().map(AuthoritySnapshot::template);
    }

    /** Captures both the exact template and its durable-authority fingerprint. */
    public Optional<AuthoritySnapshot> snapshot() {
        final OptionalLong stableGeneration = mutationGate.stableGeneration();
        if (stableGeneration.isEmpty()) {
            return Optional.empty();
        }
        final Authority current = authority.get();
        return current == null || !mutationGate.isCurrent(stableGeneration.getAsLong())
                ? Optional.empty()
                : Optional.of(new AuthoritySnapshot(
                        current.template(), current.fingerprint(), stableGeneration.getAsLong()));
    }

    /** Revalidates that no successful administrator capture/clear replaced the authority. */
    public boolean isCurrent(AuthoritySnapshot expected) {
        if (expected == null) {
            return false;
        }
        final Authority current = authority.get();
        return current != null
                && mutationGate.isCurrent(expected.generation())
                && MessageDigest.isEqual(
                        current.fingerprint().getBytes(StandardCharsets.US_ASCII),
                        expected.fingerprint().getBytes(StandardCharsets.US_ASCII));
    }

    public boolean isLoaded() {
        return loaded;
    }

    private Authority decodeAuthority(String document) {
        if (document == null) {
            return null;
        }
        final AdvertisingCurrencyAuthorityCodec.Decoded decoded =
                AdvertisingCurrencyAuthorityCodec.decode(document);
        final ItemStack template = normalized(
                STACK_CODEC.deserialize(decoded.serializedItem()));
        if (template == null) {
            throw new IllegalArgumentException(
                    "Advertising currency authority decoded as an empty item");
        }
        return new Authority(template, fingerprint(decoded.serializedItem()));
    }

    private void enqueueIo(Runnable operation) {
        synchronized (mutationMonitor) {
            mutationQueue.addLast(operation);
            if (mutationWorkerRunning) {
                return;
            }
            mutationWorkerRunning = true;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainIoQueue);
    }

    private void drainIoQueue() {
        while (true) {
            final Runnable operation;
            synchronized (mutationMonitor) {
                operation = mutationQueue.pollFirst();
                if (operation == null) {
                    mutationWorkerRunning = false;
                    return;
                }
            }
            try {
                operation.run();
            } catch (RuntimeException exception) {
                plugin.debug(exception);
            }
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    private static ItemStack normalized(ItemStack source) {
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return null;
        }
        final ItemStack copy = source.clone();
        copy.setAmount(1);
        return copy;
    }

    private static String fingerprint(byte[] serialized) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record AuthoritySnapshot(ItemStack template, String fingerprint, long generation) {

        public AuthoritySnapshot {
            template = template.clone();
        }

        @Override
        public ItemStack template() {
            return template.clone();
        }
    }

    private record Authority(ItemStack template, String fingerprint) {

        private Authority {
            template = template.clone();
        }

        @Override
        public ItemStack template() {
            return template.clone();
        }

        private Authority copy() {
            return new Authority(template, fingerprint);
        }
    }
}
