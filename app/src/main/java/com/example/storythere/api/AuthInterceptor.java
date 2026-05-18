package com.example.storythere.api;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private static final long TOKEN_TIMEOUT_SECONDS = 10L;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder requestBuilder = original.newBuilder();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            try {
                String token = Tasks.await(
                    user.getIdToken(false),
                    TOKEN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                ).getToken();

                if (token != null && !token.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }
            } catch (Exception ignored) {
                // If token retrieval fails, request goes without auth header.
                // The backend will return 401 for protected endpoints.
            }
        }

        return chain.proceed(requestBuilder.build());
    }
}
