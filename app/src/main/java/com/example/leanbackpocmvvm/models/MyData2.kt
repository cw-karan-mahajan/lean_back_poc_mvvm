package com.example.leanbackpocmvvm.models


import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep
import com.example.leanbackpocmvvm.models.RowX
import java.io.Serializable

@Keep
data class MyData2(
    @SerializedName("rowCount")
    val rowCount: Int,
    @SerializedName("rows")
    val rows: List<RowX>
) : Serializable


