package com.example.storythere.data;

import androidx.annotation.NonNull;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiUser;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {
    private final ApiService apiService;

    public interface UserCallback {
        void onSuccess(ApiUser user);
        void onError(Throwable throwable);
    }

    public UserRepository() {
        apiService = ApiClient.getApiService();
    }

    public void syncCurrentUser(@NonNull UserCallback callback) {
        apiService.syncMe().enqueue(new Callback<ApiUser>() {
            @Override
            public void onResponse(@NonNull Call<ApiUser> call, @NonNull Response<ApiUser> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new RuntimeException("API sync failed with code: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiUser> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void createUser(User user, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void updateUser(User user, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void getUser(String uid, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void updateLastLogin(String uid, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void updateUserRole(String uid, String role, @NonNull UserCallback callback) {
        callback.onError(new UnsupportedOperationException("updateUserRole is not implemented via API yet."));
    }

    public void deleteUser(String uid, @NonNull UserCallback callback) {
        callback.onError(new UnsupportedOperationException("deleteUser is not implemented via API yet."));
    }
}
