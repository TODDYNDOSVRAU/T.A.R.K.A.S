package com.example.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client of the app. Every service uses it, so they share the same connection pool and
 * threads instead of each keeping its own. A service that needs other limits derives from it with
 * `Http.client.newBuilder()`, which still shares both.
 */
internal object Http {
  val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()
  }
}
