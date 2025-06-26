package com.example.storythere.data;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;

public class UserRepository {
    private FirebaseFirestore db;
    private static final String TAG = "UserRepository";
    private static final String USERS_COLLECTION = "users";

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void createUser(User user, OnCompleteListener<Void> listener) {
        db.collection(USERS_COLLECTION).document(user.getUid())
            .set(user)
            .addOnCompleteListener(listener);
    }

    public void updateUser(User user, OnCompleteListener<Void> listener) {
        db.collection(USERS_COLLECTION).document(user.getUid())
            .set(user)
            .addOnCompleteListener(listener);
    }

    public void getUser(String uid, OnCompleteListener<DocumentSnapshot> listener) {
        db.collection(USERS_COLLECTION).document(uid)
            .get()
            .addOnCompleteListener(listener);
    }

    public void updateLastLogin(String uid, OnCompleteListener<Void> listener) {
        db.collection(USERS_COLLECTION).document(uid)
            .update("lastLoginAt", Timestamp.now())
            .addOnCompleteListener(listener);
    }

    public void updateUserRole(String uid, String role, OnCompleteListener<Void> listener) {
        db.collection(USERS_COLLECTION).document(uid)
            .update("role", role)
            .addOnCompleteListener(listener);
    }

    public void deleteUser(String uid, OnCompleteListener<Void> listener) {
        db.collection(USERS_COLLECTION).document(uid)
            .delete()
            .addOnCompleteListener(listener);
    }
} 