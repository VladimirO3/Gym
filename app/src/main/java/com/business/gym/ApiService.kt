package com.business.gym

import com.business.gym.data.api.LoginResponse
import com.business.gym.data.model.Coach
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
	@FormUrlEncoded
	@POST("login")
	suspend fun login(
		@Field("email") email: String,
		@Field("password") pass: String
	): LoginResponse

	@GET("coaches")
	suspend fun getCoaches(): List<Coach>

	@Multipart
	@POST("admin/coaches")
	suspend fun uploadCoach(
		@Part("name") name: RequestBody,
		@Part("description") description: RequestBody,
		@Part image: MultipartBody.Part
	): Response<Unit>
}