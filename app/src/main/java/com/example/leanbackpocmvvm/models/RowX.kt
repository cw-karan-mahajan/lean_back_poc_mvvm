package com.example.leanbackpocmvvm.models


import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class RowX(
    @SerializedName("rowHeader")
    val rowHeader: String,
    @SerializedName("rowIndex")
    val rowIndex: Int,
    @SerializedName("rowItems")
    val rowItems: List<RowItemX>,
    @SerializedName("rowLayout")
    val rowLayout: String
) : Serializable