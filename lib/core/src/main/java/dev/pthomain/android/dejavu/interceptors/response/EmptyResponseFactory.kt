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

package dev.pthomain.android.dejavu.interceptors.response

import dev.pthomain.android.dejavu.cache.metadata.response.CallDuration
import dev.pthomain.android.dejavu.cache.metadata.response.Empty
import dev.pthomain.android.dejavu.cache.metadata.response.Result
import dev.pthomain.android.dejavu.cache.metadata.response.ellapsed
import dev.pthomain.android.dejavu.cache.metadata.token.CacheStatus.DONE
import dev.pthomain.android.dejavu.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Local
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.di.DateFactory
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.ErrorFactory
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.NetworkErrorPredicate
import java.util.*

/**
 * Provides empty responses for operations that do not return data (e.g. INVALIDATE or CLEAR), for
 * calls that could return data but had none (OFFLINE) or for network calls that failed.
 *
 * @param errorFactory the custom error factory used to wrap the exception
 */
//FIXME is this deprecated?
internal class EmptyResponseFactory<E>(
        private val errorFactory: ErrorFactory<E>,
        private val dateFactory: DateFactory
) where E : Throwable,
        E : NetworkErrorPredicate {

    /**
     * TODO JavaDoc
     * Returns a Single emitting a ResponseWrapper with no response and a status of
     * either DONE or EMPTY.
     *
     * @param networkToken the instruction token for this call
     * @return an empty ResponseWrapper emitting Single
     */
    fun <R : Any> createEmptyResponse(networkToken: RequestToken<Cache, R>) =
            Empty(
                    errorFactory(EmptyResponseException(NullPointerException())),
                    networkToken,
                    CallDuration(disk = networkToken.ellapsed(dateFactory))
            )

    /**
     * Returns a Single emitting a ResponseWrapper with no response and a status of
     * either DONE or EMPTY.
     *
     * @param networkToken the instruction token for this call
     * @return an empty ResponseWrapper emitting Single
     */
    fun <R : Any, O : Local> createDoneResponse(networkToken: RequestToken<O, R>) =
            Result(
                    RequestToken(
                            networkToken.instruction,
                            DONE,
                            networkToken.requestDate
                    ),
                    CallDuration(disk = networkToken.ellapsed(dateFactory))
            )

    class EmptyResponseException(override val cause: Exception) : NoSuchElementException("The response was empty")
}