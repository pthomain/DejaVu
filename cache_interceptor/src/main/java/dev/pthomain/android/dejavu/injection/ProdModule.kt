/*
 *
 *  Copyright (C) 2017-2020 Pierre Thomain
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

package dev.pthomain.android.dejavu.injection

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Module
import dagger.Provides
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceModule.Companion.DATABASE_NAME
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import java.util.*
import javax.inject.Singleton

@Module
internal abstract class ProdModule<E>
        where E : Throwable,
              E : NetworkErrorPredicate {

    @Provides
    @Singleton
    fun provideDateFactory() = object : Function1<Long?, Date> {
        override fun get(t1: Long?) = t1?.let { Date(it) } ?: Date()
    }

    @Provides
    @Singleton
    fun provideSqlOpenHelper(context: Context,
                             callback: SupportSQLiteOpenHelper.Callback?): SupportSQLiteOpenHelper? =
            callback?.let {
                RequerySQLiteOpenHelperFactory().create(
                        SupportSQLiteOpenHelper.Configuration.builder(context)
                                .name(DATABASE_NAME)
                                .callback(it)
                                .build()
                )
            }

}

