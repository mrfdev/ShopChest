package de.epiceric.shopchest.sql;

public record DatabaseDiagnostics(
        boolean initialized,
        boolean connectionValid,
        long latencyMillis,
        int schemaVersion,
        int totalShops,
        int normalShops,
        int adminShops,
        int owners,
        int economyLogs,
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int waitingThreads
) {
    public static DatabaseDiagnostics unavailable(boolean initialized) {
        return new DatabaseDiagnostics(
                initialized,
                false,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1);
    }
}
