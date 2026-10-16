package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.Callback;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseConnectionCallbackTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionFailureReturnsToMainThreadBeforeCallingDisableHandler(boolean throwsException) {
        ShopChest plugin = mock(ShopChest.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> workers = new ArrayList<>(), mainThread = new ArrayList<>();
        AtomicReference<Throwable> delivered = new AtomicReference<>();
        String previousPrefix = Config.databaseTablePrefix;
        try (var bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                workers.add(call.getArgument(1));
                return mock(BukkitTask.class);
            });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                mainThread.add(call.getArgument(1));
                return mock(BukkitTask.class);
            });
            Config.databaseTablePrefix = "shopchest_";
            SQLite database = spy(new SQLite(plugin));
            IllegalStateException failure = new IllegalStateException("Unavailable fixture database");
            if (throwsException) doThrow(failure).when(database).getDataSource();
            else doReturn(null).when(database).getDataSource();
            database.connect(new Callback<>(plugin) {
                @Override public void onError(Throwable error) { delivered.set(error); }
                @Override public void onResult(Integer ignored) { fail("Failed connection reported success"); }
            });
            assertEquals(1, workers.size());
            workers.removeFirst().run();
            assertNull(delivered.get(), "The SQL worker must never invoke the plugin-disable handler directly");
            assertFalse(database.isInitialized());
            assertEquals(1, mainThread.size());
            mainThread.removeFirst().run();
            assertNotNull(delivered.get());
            if (throwsException) assertSame(failure, delivered.get());
            else assertEquals("Data source is null", delivered.get().getMessage());
        } finally {
            Config.databaseTablePrefix = previousPrefix;
        }
    }
}
