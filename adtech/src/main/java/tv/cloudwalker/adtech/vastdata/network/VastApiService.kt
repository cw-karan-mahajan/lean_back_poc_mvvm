package tv.cloudwalker.adtech.vastdata.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url



interface VastApiService {
    @GET
    suspend fun getVastXml(
        @Url path: String,
        @QueryMap queryParams: Map<String, String> = emptyMap()
    ): Response<String>
}