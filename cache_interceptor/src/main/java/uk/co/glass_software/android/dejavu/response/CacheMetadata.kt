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

package uk.co.glass_software.android.dejavu.response

import uk.co.glass_software.android.dejavu.configuration.NetworkErrorProvider
import uk.co.glass_software.android.dejavu.interceptors.internal.cache.token.CacheToken

/**
 * Contains cache metadata for the given call. This metadata is used on the ResponseWrapper and is added
 * to the target response if it implements CacheMetadata.Holder.
 *
 * @param cacheToken the cache token, containing information about the cache state of this response
 * @param exception any exception caught by the generic error handling or resulting of an exception during the caching process
 * @param callDuration how long the call took to execute at different stages of the caching process
 */
data class CacheMetadata<E>(@Transient val cacheToken: CacheToken,
                            @Transient val exception: E? = null,
                            @Transient val callDuration: Duration = Duration(0, 0, 0))
        where E : Exception,
              E : NetworkErrorProvider {

    /**
     * Contains information about how long the call took to execute at different stages of the caching process.
     *
     * @param disk time taken to retrieve the data from the local cache
     * @param network time taken to retrieve the data from the network
     * @param total total time for this call, including disk, network and processing time.
     */
    data class Duration(val disk: Int,
                        val network: Int,
                        val total: Int)

    /**
     * Interface to be set on any response requiring cache metadata to be provided. Only responses
     * implementing this interface can use the mergeOnNextOnError directive.
     */
    interface Holder<E>
            where E : Exception,
                  E : NetworkErrorProvider {
        var metadata: CacheMetadata<E>
    }


    override fun toString(): String {
        return "CacheMetadata(cacheToken=$cacheToken, exception=$exception, callDuration=$callDuration)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CacheMetadata<*>

        if (cacheToken != other.cacheToken) return false
        if (exception != other.exception) return false
        if (callDuration != other.callDuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cacheToken.hashCode()
        result = 31 * result + (exception?.hashCode() ?: 0)
        result = 31 * result + callDuration.hashCode()
        return result
    }

}