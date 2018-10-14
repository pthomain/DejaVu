/*
 * Copyright (C) 2017 Glass Software Ltd
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package uk.co.glass_software.android.cache_interceptor.injection

import android.content.ContentValues
import dagger.Module
import dagger.Provides
import io.requery.android.database.sqlite.SQLiteDatabase
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import uk.co.glass_software.android.boilerplate.Boilerplate.context
import uk.co.glass_software.android.boilerplate.utils.lambda.Provide1
import uk.co.glass_software.android.cache_interceptor.configuration.CacheConfiguration
import uk.co.glass_software.android.cache_interceptor.configuration.NetworkErrorProvider
import uk.co.glass_software.android.cache_interceptor.interceptors.RxCacheInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.ResponseInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.CacheInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.CacheManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.database.DatabaseManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.database.SqlOpenHelper
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.serialisation.GsonSerialiser
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.serialisation.SerialisationManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.token.CacheToken
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.error.ErrorInterceptor
import uk.co.glass_software.android.cache_interceptor.retrofit.RetrofitCacheAdapterFactory
import uk.co.glass_software.android.cache_interceptor.retrofit.annotations.AnnotationProcessor
import uk.co.glass_software.android.shared_preferences.StoreEntryFactory
import uk.co.glass_software.android.shared_preferences.encryption.manager.EncryptionManager
import java.util.*
import javax.inject.Singleton

@Module
internal abstract class ConfigurationModule<E>(private val configuration: CacheConfiguration<E>)
        where E : Exception,
              E : NetworkErrorProvider {

    @Provides
    @Singleton
    fun provideConfiguration() = configuration

    @Provides
    @Singleton
    fun provideGsonSerialiser() =
            GsonSerialiser(configuration.gson)

    @Provides
    @Singleton
    fun provideStoreEntryFactory(gsonSerialiser: GsonSerialiser) =
            StoreEntryFactory.builder(context)
                    .customSerialiser(gsonSerialiser)
                    .logger(configuration.logger)
                    .build()

    @Provides
    @Singleton
    fun provideEncryptionManager(storeEntryFactory: StoreEntryFactory) =
            storeEntryFactory.encryptionManager

    @Provides
    @Singleton
    fun provideSerialisationManager(encryptionManager: EncryptionManager?) =
            SerialisationManager<E>(
                    configuration.logger,
                    encryptionManager,
                    configuration.gson
            )

    @Provides
    @Singleton
    fun provideSqlOpenHelper() = SqlOpenHelper(
            context.applicationContext,
            "rx_cache_interceptor.db"
    )

    @Provides
    @Singleton
    fun provideDatabase(sqlOpenHelper: SqlOpenHelper) = object : Provide1<SQLiteDatabase> {
        override fun invoke() = sqlOpenHelper.writableDatabase!!
    }

    private val dateFactory = { timeStamp: Long? -> timeStamp?.let { Date(it) } ?: Date() }

    @Provides
    @Singleton
    fun provideDatabaseManager(databaseProvider: Provide1<SQLiteDatabase>,
                               serialisationManager: SerialisationManager<E>) =
            DatabaseManager(
                    databaseProvider,
                    serialisationManager,
                    configuration.logger,
                    configuration.compress,
                    configuration.encrypt,
                    configuration.cacheDurationInMillis,
                    dateFactory,
                    this::mapToContentValues
            )


    private fun mapToContentValues(map: Map<String, *>): ContentValues {
        val values = ContentValues()
        for ((key, value) in map) {
            when (value) {
                is Boolean -> values.put(key, value)
                is Float -> values.put(key, value)
                is Double -> values.put(key, value)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
                is Byte -> values.put(key, value)
                is ByteArray -> values.put(key, value)
                is Short -> values.put(key, value)
                is String -> values.put(key, value)
            }
        }
        return values
    }

    @Provides
    @Singleton
    fun cacheManager(databaseManager: DatabaseManager<E>) =
            CacheManager(
                    databaseManager,
                    configuration.errorFactory,
                    dateFactory,
                    configuration.cacheDurationInMillis,
                    configuration.logger
            )

    @Provides
    @Singleton
    fun provideErrorInterceptorFactory() = object : Function2<CacheToken, Long, ErrorInterceptor<E>> {
        override fun get(t1: CacheToken, t2: Long) = ErrorInterceptor(
                configuration.errorFactory,
                configuration.logger,
                t1,
                t2,
                configuration.networkTimeOutInSeconds
        )
    }

    @Provides
    @Singleton
    fun provideCacheInterceptorFactory(cacheManager: CacheManager<E>) = object : Function2<CacheToken, Long, CacheInterceptor<E>> {
        override fun get(t1: CacheToken, t2: Long) = CacheInterceptor(
                cacheManager,
                configuration.isCacheEnabled,
                configuration.logger,
                t1,
                t2
        )
    }

    @Provides
    @Singleton
    fun provideResponseInterceptor() = object : Function1<Long, ResponseInterceptor<E>> {
        override fun get(t1: Long) = ResponseInterceptor<E>(
                configuration.logger,
                t1,
                configuration.mergeOnNextOnError
        )
    }

    @Provides
    @Singleton
    fun provideRxCacheInterceptorFactory(errorInterceptorFactory: Function2<CacheToken, Long, ErrorInterceptor<E>>,
                                         cacheInterceptorFactory: Function2<CacheToken, Long, CacheInterceptor<E>>,
                                         responseInterceptor: Function1<Long, ResponseInterceptor<E>>) =
            RxCacheInterceptor.Factory(
                    { t1, t2 -> errorInterceptorFactory.get(t1, t2) },
                    { t1, t2 -> cacheInterceptorFactory.get(t1, t2) },
                    { responseInterceptor.get(it) },
                    configuration
            )

    @Provides
    @Singleton
    fun provideRetrofitCacheAdapterFactory(rxCacheInterceptorFactory: RxCacheInterceptor.Factory<E>,
                                           annotationProcessor: AnnotationProcessor<E>) =
            RetrofitCacheAdapterFactory(
                    RxJava2CallAdapterFactory.create(),
                    rxCacheInterceptorFactory,
                    annotationProcessor,
                    configuration.logger
            )

    @Provides
    @Singleton
    fun provideAnnotationProcessor() =
            AnnotationProcessor(configuration)

    interface Function1<T1, R> {
        fun get(t1: T1): R
    }

    interface Function2<T1, T2, R> {
        fun get(t1: T1, t2: T2): R
    }
}