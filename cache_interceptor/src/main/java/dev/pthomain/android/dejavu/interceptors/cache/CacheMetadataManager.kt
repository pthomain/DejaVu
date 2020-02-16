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

package dev.pthomain.android.dejavu.interceptors.cache

import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CallDuration
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheStatus.*
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.InstructionToken
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.ResponseToken
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException.Type.SERIALISATION
import dev.pthomain.android.glitchy.interceptor.error.ErrorFactory
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import java.util.*

/**
 * Handles the update of the ResponseWrapper's metadata.
 *
 * @param errorFactory the factory converting exceptions to the custom exception type
 * @param persistenceManager the object in charge of persisting the response
 * @param dateFactory a factory converting timestamps in Dates
 * @param logger a Logger instance
 */
internal class CacheMetadataManager<E>(
        private val errorFactory: ErrorFactory<E>,
        private val persistenceManager: PersistenceManager<E>,
        private val dateFactory: (Long?) -> Date,
        private val logger: Logger
) where E : Throwable,
        E : NetworkErrorPredicate {

    /**
     * Updates the metadata of a ResponseWrapper just after a network call.
     *
     * @param responseWrapper the wrapper returned from the network call
     * @param cacheOperation the instructed request cache operation
     * @param previousCachedResponse the optional previously cached response for this call
     * @param instructionToken the original request instruction token
     * @param diskDuration the time spent loading the previous response from cache
     *
     * @return the ResponseWrapper updated with the new metadata
     */
    fun setNetworkCallMetadata(responseWrapper: ResponseWrapper<Cache, RequestToken<Cache>, E>,
                               cacheOperation: Cache,
                               previousCachedResponse: ResponseWrapper<Cache, RequestToken<Cache>, E>?,
                               instructionToken: InstructionToken<Cache>,
                               diskDuration: Int): ResponseWrapper<Cache, RequestToken<Cache>, E> {
        val metadata = responseWrapper.metadata
        val error = responseWrapper.metadata.exception
        val hasError = error != null
        val hasCachedResponse = previousCachedResponse != null

        val previousCacheToken = previousCachedResponse?.metadata?.cacheToken as? ResponseToken
        val timeToLiveInSeconds = cacheOperation.durationInSeconds
        val fetchDate = dateFactory(null)

        val cacheDate = ifElse(
                hasError,
                previousCacheToken?.cacheDate,
                fetchDate
        )

        val expiryDate = ifElse(
                hasError,
                previousCacheToken?.expiryDate,
                dateFactory(fetchDate.time + timeToLiveInSeconds * 1000L)
        )

        val status = ifElse(
                hasError,
                ifElse(
                        cacheOperation.priority.freshness.hasSingleResponse,
                        EMPTY,
                        ifElse(hasCachedResponse, COULD_NOT_REFRESH, EMPTY)
                ),
                ifElse(hasCachedResponse, REFRESHED, NETWORK)
        )

        val cacheToken = ResponseToken(
                instructionToken.instruction,
                status,
                fetchDate,
                ifElse(status == EMPTY, null, cacheDate),
                ifElse(status == EMPTY, null, expiryDate)
        )

        val newMetadata = metadata.copy(
                cacheToken,
                callDuration = getRefreshCallDuration(metadata.callDuration, diskDuration)
        )

        return responseWrapper.copy(
                metadata = newMetadata,
                response = ifElse(status == EMPTY, null, responseWrapper.response)
        )
    }

    /**
     * Sets the metadata associated with a failure to serialise the response for caching.
     *
     * @param wrapper the wrapper to be cached
     * @param exception the exception that occurred during serialisation
     * @return the response wrapper with the udpated metadata
     */
    fun setSerialisationFailedMetadata(wrapper: ResponseWrapper<Cache, RequestToken<Cache>, E>,
                                       exception: Exception): ResponseWrapper<Cache, RequestToken<Cache>, E> {
        val message = "Could not serialise ${wrapper.responseClass.simpleName}: this response will not be cached."
        logger.e(this, message)

        val cacheToken = wrapper.metadata.cacheToken
        val failedCacheToken = ResponseToken(
                cacheToken.instruction,
                NOT_CACHED,
                cacheToken.requestDate
        )

        val serialisationException = errorFactory(
                CacheException(
                        SERIALISATION,
                        message,
                        exception
                )
        )

        return wrapper.copy(
                metadata = wrapper.metadata.copy(
                        exception = serialisationException,
                        cacheToken = failedCacheToken
                )
        )
    }

    /**
     * Refreshes the Duration metadata
     *
     * @param callDuration the duration of the network call
     * @param diskDuration the duration of the operation to retrieve the response from cache
     * @return the udpated Duration metadata
     */
    private fun getRefreshCallDuration(callDuration: CallDuration,
                                       diskDuration: Int) =
            callDuration.copy(
                    disk = diskDuration,
                    network = callDuration.network - diskDuration,
                    total = callDuration.network
            )

}
