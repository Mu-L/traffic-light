package com.leekleak.play_integration

import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val playModule = module {
    single<ReviewManager> { ReviewManagerFactory.create(get()) }
    single<PlayPreferenceRepo>()
    single<AppReviewManager>()
}