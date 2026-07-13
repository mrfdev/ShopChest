package de.epiceric.shopchest.nms;

import java.lang.reflect.Method;

public class PlatformLoader {

    public Platform loadPlatform() {
        final String paperMinecraftVersion = getPaperMinecraftVersion();
        if (paperMinecraftVersion == null) {
            throw new RuntimeException("ShopChest requires Paper 26.2.x.");
        }
        if (isSupportedPaper262(paperMinecraftVersion)) {
            return new de.epiceric.shopchest.nms.paper.v1_21_7.PlatformImpl();
        }
        throw new RuntimeException("Paper " + paperMinecraftVersion
                + " is not supported by this ShopChest build. Supported version: 26.2.x.");
    }

    private static boolean isSupportedPaper262(String minecraftVersionId) {
        return "26.2".equals(minecraftVersionId)
                || minecraftVersionId.startsWith("26.2.");
    }

    private static String getPaperMinecraftVersion() {
        try {
            final Class<?> paperServerBuildInfoClass = Class.forName("io.papermc.paper.ServerBuildInfo");
            final Method buildInfoMethod = paperServerBuildInfoClass.getDeclaredMethod("buildInfo");
            final Method minecraftVersionIdMethod = paperServerBuildInfoClass.getDeclaredMethod("minecraftVersionId");
            final Object buildInfo = buildInfoMethod.invoke(null);
            return (String) minecraftVersionIdMethod.invoke(buildInfo);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

}
