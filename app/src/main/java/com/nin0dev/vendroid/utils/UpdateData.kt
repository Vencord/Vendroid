package com.nin0dev.vendroid.utils

import com.google.gson.annotations.SerializedName

data class UpdateData(
    @SerializedName("update") val update: Update?,
    @SerializedName("announcements") val announcements: List<Announcement>?
)

data class Update(
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String
)

data class Announcement(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String
)