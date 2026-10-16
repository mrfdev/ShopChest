package de.epiceric.shopchest.external.cmi;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;

import java.util.UUID;

/** Kept separate so CMI remains optional. No getUser fallback, database read, or offline player lookup. */
public final class CmiCachedOwnerNames {
    private CmiCachedOwnerNames() {
    }

    public static String find(UUID ownerId) {
        CMIUser user = CMI.getInstance().getPlayerManager().getAllUsers().get(ownerId);
        return user != null && ownerId.equals(user.getUniqueId()) ? user.getName(false) : null;
    }
}
