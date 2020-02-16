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

package dev.pthomain.android.dejavu.interceptors.network

import android.content.Context
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.boilerplate.core.utils.rx.waitForNetwork
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CallDuration
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.utils.Utils.swapLambdaWhen
import dev.pthomain.android.dejavu.utils.Utils.swapWhenDefault
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.NoSuchElementException

/**
 * This interceptor adds a connectivity timeout to the network call, which defines a maximum
 * period of time to wait for the network to become available.
 * This delay does not affect the emission of any available cached data (according to the request's
 * cache instruction) but only the network call in the case the data is STALE and the request's
 * instruction warrants FRESH data to be returned (i.e CACHE or REFRESH operation).
 *
 * @param context the application context
 * @param logger a Logger instance
 * @param errorInterceptor the interceptor dealing with network error handling
 * @param dateFactory a factory converting timestamps to Dates
 * @param operation the original request cache operation
 * @param start the time at which the request started
 */
internal class NetworkInterceptor<O : Remote, T : RequestToken<O>, E> private constructor(
        private val context: Context,
        private val logger: Logger,
        private val errorInterceptor: ErrorInterceptor<O, T, E>,
        private val dateFactory: (Long?) -> Date,
        private val operation: Cache?,
        private val start: Long
) : ObservableTransformer<Any, ResponseWrapper<O, T, E>>
        where E : Throwable,
              E : NetworkErrorPredicate {

    /**
     * The composition method converting an upstream response Observable to an Observable emitting
     * a ResponseWrapper holding the response or the converted exception.
     *
     * @param upstream the upstream response Observable, typically as emitted by a Retrofit client.
     * @return the composed Observable emitting a ResponseWrapper and optionally delayed for network availability
     */
    override fun apply(upstream: Observable<Any>): Observable<ResponseWrapper<O, T, E>> =
            upstream
                    .filter { it != null } //see https://github.com/square/retrofit/issues/2242
                    .defaultIfEmpty(Observable.error<Any>(NoSuchElementException("Response was empty")))
                    .compose {
                        with(operation?.requestTimeOutInSeconds) {
                            if (this != null && this > 0)
                                it.timeout(toLong(), SECONDS) //fixing timeout not working in OkHttp
                            else it
                        }
                    }
                    .compose(errorInterceptor)
                    .map { it.copy(metadata = it.metadata.copy(callDuration = getCallDuration())) }
                    .compose(this::addConnectivityTimeOutIfNeeded)

    /**
     * Adds an optional delay for network availability (if the value is set as more than 0).
     *
     * @param upstream the upstream response Observable, typically as emitted by a Retrofit client.
     * @return the composed Observable optionally delayed for network availability
     */
    private fun addConnectivityTimeOutIfNeeded(upstream: Observable<ResponseWrapper<O, T, E>>): Observable<ResponseWrapper<O, T, E>> =
            operation?.connectivityTimeoutInSeconds.swapWhenDefault(null)?.let { timeOut ->
                upstream.swapLambdaWhen(timeOut > 0L) {
                    upstream.waitForNetwork(context, logger)
                            .timeout(timeOut.toLong(), SECONDS)
                }
            } ?: upstream

    /**
     * @return a Duration metadata object holding the duration of the network call
     */
    private fun getCallDuration() =
            CallDuration(
                    0,
                    (dateFactory(null).time - start).toInt(),
                    0
            )

    class Factory<E>(private val context: Context,
                     private val logger: Logger,
                     private val dateFactory: (Long?) -> Date)
            where E : Throwable,
                  E : NetworkErrorPredicate {

        fun <O : Remote, T : RequestToken<O>> create(errorInterceptor: ErrorInterceptor<O, T, E>,
                                                     operation: Cache?,
                                                     start: Long) = NetworkInterceptor(
                context,
                logger,
                errorInterceptor,
                dateFactory,
                operation,
                start
        )
    }

}
