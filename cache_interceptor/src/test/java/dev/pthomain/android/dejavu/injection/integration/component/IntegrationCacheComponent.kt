/*
 *
 *  Copyright (C) 2017 Pierre Thomain
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package dev.pthomain.android.dejavu.injection.integration.component

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Component
import dev.pthomain.android.dejavu.configuration.Serialiser
import dev.pthomain.android.dejavu.injection.component.CacheComponent
import dev.pthomain.android.dejavu.injection.integration.module.IntegrationCacheModule
import dev.pthomain.android.dejavu.injection.module.CacheModule
import dev.pthomain.android.dejavu.interceptors.internal.cache.CacheInterceptor
import dev.pthomain.android.dejavu.interceptors.internal.cache.CacheManager
import dev.pthomain.android.dejavu.interceptors.internal.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.internal.cache.serialisation.Hasher
import dev.pthomain.android.dejavu.interceptors.internal.cache.serialisation.SerialisationManager
import dev.pthomain.android.dejavu.interceptors.internal.cache.token.CacheToken
import dev.pthomain.android.dejavu.interceptors.internal.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.internal.error.Glitch
import dev.pthomain.android.dejavu.interceptors.internal.response.EmptyResponseFactory
import dev.pthomain.android.dejavu.interceptors.internal.response.ResponseInterceptor
import dev.pthomain.android.dejavu.response.CacheMetadata
import dev.pthomain.android.dejavu.retrofit.ProcessingErrorAdapter
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor
import dev.pthomain.android.mumbo.base.EncryptionManager
import io.reactivex.subjects.PublishSubject
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.util.*
import javax.inject.Singleton

@Singleton
@Component(modules = [IntegrationCacheModule::class])
internal interface IntegrationCacheComponent : CacheComponent<Glitch> {

    fun dateFactory(): CacheModule.Function1<Long?, Date>

    fun serialiser(): Serialiser

    fun encryptionManager(): EncryptionManager

    fun serialisationManager(): SerialisationManager<Glitch>

    fun sqlOpenHelperCallback(): SupportSQLiteOpenHelper.Callback?

    fun sqlOpenHelper(): SupportSQLiteOpenHelper?

    fun database(): SupportSQLiteDatabase?

    fun hasher(): Hasher

    fun persistenceManager(): PersistenceManager<Glitch>

    fun cacheManager(): CacheManager<Glitch>

    fun errorInterceptorFactory(): CacheModule.Function3<Context, CacheToken, Long, ErrorInterceptor<Glitch>>

    fun cacheInterceptorFactory(): CacheModule.Function2<CacheToken, Long, CacheInterceptor<Glitch>>

    fun responseInterceptorFactory(): CacheModule.Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<Glitch>>

    fun defaultAdapterFactory(): RxJava2CallAdapterFactory

    fun processingErrorAdapterFactory(): ProcessingErrorAdapter.Factory<Glitch>

    fun cacheMetadataSubject(): PublishSubject<CacheMetadata<Glitch>>

    fun annotationProcessor(): AnnotationProcessor<Glitch>

    fun emptyResponseFactory(): EmptyResponseFactory<Glitch>

    fun supportSQLiteOpenHelper(): SupportSQLiteOpenHelper?

}