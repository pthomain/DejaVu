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

package dev.pthomain.android.dejavu.interceptors

import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.cache.metadata.response.*
import dev.pthomain.android.dejavu.cache.metadata.token.CacheStatus.INSTRUCTION
import dev.pthomain.android.dejavu.cache.metadata.token.CacheStatus.NETWORK
import dev.pthomain.android.dejavu.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.cache.metadata.token.ResponseToken
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.CacheInstruction
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.Hasher
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.PlainRequestMetadata
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.di.DateFactory
import dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
import dev.pthomain.android.dejavu.serialisation.SerialisationArgumentValidator
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.NetworkErrorPredicate
import dev.pthomain.android.glitchy.core.interceptor.interceptors.outcome.Outcome
import dev.pthomain.android.glitchy.flow.interceptors.base.FlowInterceptor

/**
 * Wraps and composes with the interceptors dealing with error handling, cache and response decoration.
 *
 * @param asResult whether or not the response should be wrapped in a CacheResult
 * @param operation the cache operation for the intercepted call
 * @param requestMetadata the associated request metadata
 * @param hasher the class handling the request hashing for unicity
 * @param dateFactory the factory transforming timestamps to dates
 * @param networkInterceptorFactory the factory providing NetworkInterceptors dealing with network handling
 * @param cacheInterceptorFactory the factory providing CacheInterceptors dealing with the cache
 * @param responseInterceptorFactory the factory providing ResponseInterceptors dealing with response decoration
 *
 * @see dev.pthomain.android.dejavu.interceptors.network.NetworkInterceptor
 * @see dev.pthomain.android.dejavu.interceptors.cache.CacheInterceptor
 * @see dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
 */
class DejaVuInterceptor<E, R : Any> private constructor(
        private val asResult: Boolean,
        private val operation: Operation,
        private val requestMetadata: PlainRequestMetadata<R>,
        private val hasher: Hasher,
        private val logger: Logger,
        private val dateFactory: DateFactory,
        serialisationArgumentValidator: SerialisationArgumentValidator,
        private val networkInterceptorFactory: NetworkInterceptor.Factory<E>,
        private val cacheInterceptorFactory: CacheInterceptor.Factory<E>,
        private val responseInterceptorFactory: ResponseInterceptor.Factory<E>,
) : FlowInterceptor()
        where E : Throwable,
              E : NetworkErrorPredicate {

    init {
        if (operation is Cache && operation.serialisation.isNotBlank())
            serialisationArgumentValidator.validate(operation.serialisation)
    }

    //TODO move to the Rx adapter
//    /**
//     * Composes Observables with the wrapped interceptors and only emits the
//     * final response (if intercepted).
//     *
//     * @param upstream the call to intercept
//     * @return the call intercepted with the inner interceptors
//     */
//    override suspend fun  map(value: Any): Any {
//        upstream.toObservable()
//                .compose(this)
//                .filter { (it as? HasMetadata<*, *, *>)?.cacheToken?.status?.isFinal ?: true }
//                .firstOrError()

    /**
     * Deals with the internal composition.
     *
     * @param upstream the call to intercept
     * @return the call intercepted with the inner interceptors
     */
    @Throws(IllegalStateException::class)
    override suspend fun map(value: Any): Any {
        val requestDate = dateFactory(null)
        val hashedRequestMetadata = hasher.hash(requestMetadata)

        return if (hashedRequestMetadata != null) {
            val instruction = CacheInstruction(
                    operation,
                    hashedRequestMetadata
            )

            val instructionToken = RequestToken(
                    instruction,
                    INSTRUCTION,
                    requestDate
            )

            val cacheInterceptor = cacheInterceptorFactory.create(instructionToken)
            val responseInterceptor = responseInterceptorFactory.create<R>(asResult)

            if (operation is Remote) {
                @Suppress("UNCHECKED_CAST")
                instructionToken as RequestToken<out Remote, R>
                val networkInterceptor = networkInterceptorFactory.create(instructionToken)
                checkOutcome(value, instructionToken)
                        .apply(networkInterceptor::intercept)
                        .apply(cacheInterceptor::intercept)
                        .apply(responseInterceptor::intercept)
            } else {
                LocalOperationToken<R>()
                        .apply(cacheInterceptor::intercept)
                        .apply(responseInterceptor::intercept)
            }
        } else {
            logger.e(
                    this,
                    "The request metadata could not be hashed, this request won't be cached: $requestMetadata"
            )
            throw IllegalStateException("The request could not be hashed")
        }
    }

    private suspend fun <O : Remote> checkOutcome(
            outcome: Any,
            instructionToken: RequestToken<O, R>,
    ): DejaVuResult<R> {
        val callDuration = CallDuration(network = instructionToken.ellapsed(dateFactory))

        @Suppress("UNCHECKED_CAST") //converted to Outcome by OutcomeInterceptor (set via Glitchy)
        return when (outcome as Outcome<R>) {
            is Outcome.Success<R> -> Response(
                    (outcome as Outcome.Success<R>).response,
                    ResponseToken(
                            instructionToken.instruction,
                            NETWORK,
                            instructionToken.requestDate
                    ),
                    callDuration
            )
            is Outcome.Error<*> -> Empty(
                    (outcome as Outcome.Error<E>).exception,
                    instructionToken,
                    callDuration
            )
        }
    }

    /**
     * Factory providing instances of DejaVuInterceptor
     *
     * @param hasher the class handling the request hashing for unicity
     * @param dateFactory the factory transforming timestamps to dates
     * @param networkInterceptorFactory the factory providing NetworkInterceptors dealing with network handling
     * @param cacheInterceptorFactory the factory providing CacheInterceptors dealing with the cache
     * @param responseInterceptorFactory the factory providing ResponseInterceptors dealing with response decoration
     * @param configuration the global cache configuration
     *
     * @see dev.pthomain.android.dejavu.interceptors.network.NetworkInterceptor
     * @see dev.pthomain.android.dejavu.interceptors.cache.CacheInterceptor
     * @see dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
     */
    class Factory<E> internal constructor(
            private val hasher: Hasher,
            private val logger: Logger,
            private val dateFactory: DateFactory,
            private val serialisationArgumentValidator: SerialisationArgumentValidator,
            private val networkInterceptorFactory: NetworkInterceptor.Factory<E>,
            private val cacheInterceptorFactory: CacheInterceptor.Factory<E>,
            private val responseInterceptorFactory: ResponseInterceptor.Factory<E>
    ) where E : Throwable,
            E : NetworkErrorPredicate {

        /**
         * Provides an instance of DejaVuInterceptor
         *
         * @param asResult whether or not the response should be wrapped in a DejaVuResult
         * @param operation the cache operation for the intercepted call
         * @param requestMetadata the associated request metadata
         */
        fun <R : Any> create(
                asResult: Boolean,
                operation: Operation,
                requestMetadata: PlainRequestMetadata<R>
        ) =
                DejaVuInterceptor(
                        asResult,
                        operation,
                        requestMetadata,
                        hasher,
                        logger,
                        dateFactory,
                        serialisationArgumentValidator,
                        networkInterceptorFactory,
                        cacheInterceptorFactory,
                        responseInterceptorFactory
                )
    }

}
