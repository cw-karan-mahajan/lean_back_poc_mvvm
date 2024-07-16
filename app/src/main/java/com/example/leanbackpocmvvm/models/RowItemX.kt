package com.example.leanbackpocmvvm.models


import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class RowItemX(
    @SerializedName("alternateUrl")
    val alternateUrl: String,
    @SerializedName("background")
    val background: String,
    @SerializedName("cast")
    val cast: List<String>,
    @SerializedName("detailPage")
    val detailPage: Boolean,
    @SerializedName("director")
    val director: List<String>,
    @SerializedName("genre")
    val genre: List<String>,
    @SerializedName("package")
    val packageX: String,
    @SerializedName("playstoreUrl")
    val playstoreUrl: String?,
    @SerializedName("portrait")
    val portrait: String?,
    @SerializedName("poster")
    val poster: String,
    @SerializedName("video_url")
    val videoUrl: String? = null,
    @SerializedName("rating")
    val rating: Double,
    @SerializedName("runtime")
    val runtime: String,
    @SerializedName("showTileInfo")
    val showTileInfo: String?,
    @SerializedName("source")
    val source: String,
    @SerializedName("startIndex")
    val startIndex: String,
    @SerializedName("startTime")
    val startTime: String,
    @SerializedName("synopsis")
    val synopsis: String,
    @SerializedName("target")
    val target: List<String>,
    @SerializedName("tid")
    val tid: String,
    @SerializedName("tileHeight")
    val tileHeight: String?,
    @SerializedName("tileType")
    val tileType: String,
    @SerializedName("mediaType")
    val mediaType: String,
    @SerializedName("tileWidth")
    val tileWidth: String?,
    @SerializedName("title")
    val title: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("useAlternate")
    val useAlternate: Boolean,
    @SerializedName("year")
    val year: String,
    @SerializedName("layout")
    var layout: String
) : Serializable