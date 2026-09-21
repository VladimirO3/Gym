package com.business.gym_app.data.api

import com.business.gym_app.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isPublicRequest = original.method == "POST" && (
            path.endsWith("/login") ||
                path.endsWith("/register") ||
                path.endsWith("/auth/request-otp") ||
                path.endsWith("/auth/verify-otp") ||
                path.endsWith("/auth/refresh")
            )
        val request = original.newBuilder()
        if (!isPublicRequest) tokenManager.getToken()?.let {
            request.header("Authorization", "Bearer $it")
        }
        val response = chain.proceed(request.build())
        if (response.code == 403 && isPublicRequest) {
            response.close()
            return chain.proceed(original)
        }
        return response
    }
}
