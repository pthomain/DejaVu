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

package dev.pthomain.android.dejavu.interceptors.internal.cache

import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.configuration.CacheInstruction.Operation.Expiring
import dev.pthomain.android.dejavu.configuration.ErrorFactory
import dev.pthomain.android.dejavu.configuration.NetworkErrorProvider
import dev.pthomain.android.dejavu.interceptors.internal.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.internal.cache.token.CacheStatus.*
import dev.pthomain.android.dejavu.interceptors.internal.cache.token.CacheToken
import dev.pthomain.android.dejavu.response.CacheMetadata
import dev.pthomain.android.dejavu.response.ResponseWrapper
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException.Type.SERIALISATION
import java.util.*

internal class CacheMetadataManager<E>(
        private val errorFactory: ErrorFactory<E>,
        private val persistenceManager: PersistenceManager<E>,
        private val dateFactory: (Long?) -> Date,
        private val defaultDurationInMillis: Long,
        private val logger: Logger
) where E : Exception,
        E : NetworkErrorProvider {

    fun setNetworkCallMetadata(responseWrapper: ResponseWrapper<E>,
                               cacheOperation: Expiring,
                               previousCachedResponse: ResponseWrapper<E>?,
                               instructionToken: CacheToken,
                               diskDuration: Int): ResponseWrapper<E> {
        val metadata = responseWrapper.metadata
        val error = responseWrapper.metadata.exception
        val hasError = error != null
        val hasCachedResponse = previousCachedResponse != null

        val previousCacheToken = previousCachedResponse?.metadata?.cacheToken
        val timeToLiveInMs = cacheOperation.durationInMillis ?: defaultDurationInMillis
        val fetchDate = dateFactory(null)

        val cacheDate = ifElse(
                hasError,
                previousCacheToken?.cacheDate,
                fetchDate
        )

        val expiryDate = ifElse(
                hasError,
                previousCacheToken?.expiryDate,
                dateFactory(fetchDate.time + timeToLiveInMs)
        )

        val (encryptData, compressData) = persistenceManager.shouldEncryptOrCompress(
                previousCachedResponse,
                cacheOperation
        )

        val status = ifElse(
                hasError,
                ifElse(
                        cacheOperation.freshOnly,
                        EMPTY,
                        ifElse(hasCachedResponse, COULD_NOT_REFRESH, EMPTY)
                ),
                ifElse(hasCachedResponse, REFRESHED, NETWORK)
        )

        val cacheToken = CacheToken(
                instructionToken.instruction,
                status,
                compressData,
                encryptData,
                instructionToken.requestMetadata,
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

    fun setSerialisationFailedMetadata(wrapper: ResponseWrapper<E>,
                                       exception: Exception): ResponseWrapper<E> {
        val message = "Could not serialise ${wrapper.responseClass.simpleName}: this response will not be cached."
        logger.e(this, message)

        val failedCacheToken = wrapper.metadata.cacheToken.copy(
                status = NOT_CACHED,
                cacheDate = null,
                expiryDate = null
        )

        val serialisationException = errorFactory.getError(
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

    private fun getRefreshCallDuration(callDuration: CacheMetadata.Duration,
                                       diskDuration: Int) =
            callDuration.copy(
                    disk = diskDuration,
                    network = callDuration.network - diskDuration,
                    total = callDuration.network
            )

}