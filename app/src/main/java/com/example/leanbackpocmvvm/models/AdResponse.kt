package com.example.leanbackpocmvvm.models

import com.google.gson.annotations.SerializedName

data class AdResponse(
    @SerializedName("id") val id: String,
    @SerializedName("seatbid") val seatbid: List<Seatbid>?,
    @SerializedName("cur") val cur: String
)

data class Seatbid(
    @SerializedName("bid") val bid: List<Bid>?
)

data class Bid(
    @SerializedName("id") val id: String,
    @SerializedName("impid") val impid: String,
    @SerializedName("price") val price: Double,
    @SerializedName("adid") val adid: String,
    @SerializedName("adm") val adm: String,
    @SerializedName("crid") val crid: String,
    var parsedImageUrl: String? = null
)

data class NativeAdWrapper(
    @SerializedName("native") val native: NativeAd?
)

data class NativeAd(
    @SerializedName("ver") val ver: String,
    @SerializedName("assets") val assets: List<Asset>?,
    @SerializedName("link") val link: Link?,
    @SerializedName("imptrackers") val imptrackers: List<String>?
)

data class Asset(
    @SerializedName("id") val id: Long,
    @SerializedName("img") val img: Image?
)

data class Image(
    @SerializedName("url") val url: String?,
    @SerializedName("w") val w: Int?,
    @SerializedName("h") val h: Int?
)

data class Link(
    @SerializedName("url") val url: String?,
    @SerializedName("clicktrackers") val clicktrackers: List<String>?
)