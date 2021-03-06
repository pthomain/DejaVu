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

package dev.pthomain.android.dejavu.cache.metadata.response

import dev.pthomain.android.dejavu.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.di.DateFactory

/**
 * Contains information about how long the call took to execute at different stages of the caching process.
 *
 * @param disk time taken to retrieve the data from the local cache
 * @param network time taken to retrieve the data from the network
 * @param total total time for this call, including disk, network and processing time.
 */
data class CallDuration internal constructor(
        val disk: Int = 0,
        val network: Int = 0,
        val total: Int = 0,
)

internal fun RequestToken<*, *>.ellapsed(dateFactory: DateFactory) =
        (dateFactory(null).time - requestDate.time).toInt()
