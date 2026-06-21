package com.example.storythere.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class LocalUserScope {
    public static final String OFFLINE_OWNER_UID = "offline";

    private LocalUserScope() {
    }

    public static String currentOwnerUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid() != null && !user.getUid().trim().isEmpty()) {
            return user.getUid();
        }
        return OFFLINE_OWNER_UID;
    }

    public static boolean isMissing(String ownerUid) {
        return ownerUid == null || ownerUid.trim().isEmpty();
    }
}
