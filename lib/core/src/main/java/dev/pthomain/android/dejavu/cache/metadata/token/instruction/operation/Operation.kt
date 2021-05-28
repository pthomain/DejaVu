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

package dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation

import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.STALE_ACCEPTED_FIRST
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Local.Clear.Scope.ALL
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Type.*
import dev.pthomain.android.dejavu.utils.swapWhenDefault
import java.util.Locale.UK

/**
 * Represent a cache operation.
 */
sealed class Operation(val type: Type) {

    /**
     * Represents operations returning data, either from the network or from the cache.
     */
    sealed class Remote(type: Type) : Operation(type) {

        /**
         * Expiring instructions contain a durationInMillis indicating the duration of the cached value
         * in milliseconds.
         *
         * This instruction is overridden by the operation mapper.
         * @see dev.pthomain.android.dejavu.configuration.DejaVu.Configuration.cachePredicate
         *
         * @param priority the priority instructing how the cache should behave
         * @param durationInSeconds duration of the cache for this specific call in seconds, during which the data is considered FRESH
         * @param connectivityTimeoutInSeconds maximum time to wait for the network connectivity to become available to return an online response (does not apply to cached responses)
         * @param requestTimeOutInSeconds maximum time to wait for the request to finish (does not apply to cached responses)
         */
        class Cache(
                val priority: CachePriority = STALE_ACCEPTED_FIRST,
                durationInSeconds: Int = DEFAULT_CACHE_DURATION_IN_SECONDS,
                serialisation: String = "",
                connectivityTimeoutInSeconds: Int? = null,
                requestTimeOutInSeconds: Int? = null
        ) : Remote(CACHE) {

            val durationInSeconds: Int = durationInSeconds.swapWhenDefault(DEFAULT_CACHE_DURATION_IN_SECONDS)!!
            val connectivityTimeoutInSeconds: Int? = connectivityTimeoutInSeconds.swapWhenDefault(null)
            val requestTimeOutInSeconds: Int? = requestTimeOutInSeconds.swapWhenDefault(null)
            val serialisation = serialisation
                    .replace(Regex("\\s+"), "")
                    .toUpperCase(UK)

            override fun toString() = serialise(
                    priority,
                    durationInSeconds,
                    serialisation,
                    connectivityTimeoutInSeconds,
                    requestTimeOutInSeconds
            )
        }

        /**
         * DO_NOT_CACHE operations will not cache the response.
         */
        object DoNotCache : Remote(DO_NOT_CACHE)
    }

    /**
     * Represents operations operating solely on the local cache and returning no data.
     */
    sealed class Local(type: Type) : Operation(type) {
        /**
         * INVALIDATE instructions invalidate the currently cached data if present and do not return any data.
         * They should usually be used with a Completable. However, if used with a Single or Observable,
         * they will return an empty response with cache metadata (if the response implements CacheMetadata.Holder).
         *
         * This operation will clear entries of the type defined in the associated RequestMetadata.
         * In order to clear all entries, use Any as the response class.
         * @see dev.pthomain.android.dejavu.interceptors.cache.metadata.RequestMetadata.responseClass
         */
        object Invalidate : Local(INVALIDATE)

        /**
         * CLEAR instructions clear the cached data for this call if present and do not return any data.
         * They should usually be used with a Completable. However, if used with a Single or Observable,
         * they will return an empty response with cache metadata (if the response implements CacheMetadata.Holder).
         *
         * This operation will clear entries of the type defined in the associated RequestMetadata.
         * In order to clear all entries, use Any as the response class.
         * @see dev.pthomain.android.dejavu.interceptors.cache.metadata.RequestMetadata.responseClass
         *
         * @param clearStaleEntriesOnly whether or not to clear the STALE data only. When set to true, only expired data is cleared, otherwise STALE and FRESH data is cleared.
         */
        data class Clear(
                val scope: Scope = ALL,
                val clearStaleEntriesOnly: Boolean = false
        ) : Local(CLEAR) {

            enum class Scope {
                REQUEST,    //clears the entry associated with the operation's request metadata
                CLASS,      //clears the entry associated with the operation's target class
                ALL         //clears all entries in conjunction with clearStaleEntriesOnly
            }

            override fun toString() = serialise(
                    scope,
                    clearStaleEntriesOnly
            )
        }
    }

    override fun toString() = serialise()

    enum class Type {
        CACHE,
        DO_NOT_CACHE,
        CLEAR,
        INVALIDATE
    }
}

const val DEFAULT_CACHE_DURATION_IN_SECONDS = 3600 //1h