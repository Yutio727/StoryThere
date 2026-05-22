package com.example.storythere.data;

import androidx.annotation.NonNull;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiUser;
import com.example.storythere.api.model.SyncMeRequest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
        syncCurrentUser(null, callback);
    }

    public void syncCurrentUser(User user, @NonNull UserCallback callback) {
        Call<ApiUser> call;
        SyncMeRequest request = buildSyncRequest(user);
        if (request == null) {
            call = apiService.syncMe();
        } else {
            call = apiService.syncMeWithProfile(request);
        }

        call.enqueue(new Callback<ApiUser>() {
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

    private SyncMeRequest buildSyncRequest(User user) {
        if (user == null) {
            return null;
        }

        SyncMeRequest request = new SyncMeRequest();
        request.dateOfBirth = normalizeDateOfBirth(user.getDateOfBirth());
        request.recommendationAgeBucket = user.getRecommendationAgeBucket();
        request.sex = normalizeSex(user.getSex());

        if (request.dateOfBirth == null
            && (request.recommendationAgeBucket == null || request.recommendationAgeBucket.isEmpty())
            && request.sex == null) {
            return null;
        }
        return request;
    }

    private String normalizeSex(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ("0.0".equals(trimmed) || "1.0".equals(trimmed)) {
            return trimmed;
        }
        return null;
    }

    private String normalizeDateOfBirth(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return trimmed;
        }
        if (trimmed.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
            try {
                SimpleDateFormat source = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
                source.setLenient(false);
                Date parsed = source.parse(trimmed);
                if (parsed == null) {
                    return null;
                }
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed);
            } catch (ParseException ignored) {
                return null;
            }
        }
        return null;
    }

    public void createUser(User user, @NonNull UserCallback callback) {
        syncCurrentUser(user, callback);
    }

    public void updateUser(User user, @NonNull UserCallback callback) {
        syncCurrentUser(user, callback);
    }

    public void getUser(String uid, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void updateLastLogin(String uid, @NonNull UserCallback callback) {
        syncCurrentUser(callback);
    }

    public void updateLastLogin(String uid, User user, @NonNull UserCallback callback) {
        syncCurrentUser(user, callback);
    }

    public void updateUserRole(String uid, String role, @NonNull UserCallback callback) {
        callback.onError(new UnsupportedOperationException("updateUserRole is not implemented via API yet."));
    }

    public void deleteUser(String uid, @NonNull UserCallback callback) {
        callback.onError(new UnsupportedOperationException("deleteUser is not implemented via API yet."));
    }
}
