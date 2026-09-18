package com.business.gym_app.data.model

import com.google.gson.annotations.SerializedName

data class Coach(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url", alternate = ["imageUrl", "url"]) val imageUrl: String? = null
)
