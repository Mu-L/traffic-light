package com.leekleak.iperfintegration

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val iPerfIntegrationModule = module {
    single<IPerf3Provider>()
}