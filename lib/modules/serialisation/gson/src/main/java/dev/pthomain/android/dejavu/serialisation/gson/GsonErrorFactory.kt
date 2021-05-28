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

package dev.pthomain.android.dejavu.serialisation.gson


import com.google.gson.JsonParseException
import dev.pthomain.android.dejavu.configuration.error.DejaVuGlitchFactory
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.ErrorFactory
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.glitch.ErrorCode
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.glitch.Glitch
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.glitch.GlitchFactory

/**
 * Custom Glitch ErrorFactory implementation handling extra Gson specific exceptions.
 */
class GsonGlitchFactory private constructor(private val parentFactory: DejaVuGlitchFactory)
    : ErrorFactory<Glitch> by parentFactory {

    constructor() : this(DejaVuGlitchFactory(GlitchFactory()))

    override fun invoke(throwable: Throwable) =
            when (throwable) {
                is JsonParseException -> Glitch(
                        throwable,
                        Glitch.NON_HTTP_STATUS,
                        ErrorCode.UNEXPECTED_RESPONSE,
                        throwable.message
                )
                else -> parentFactory.invoke(throwable)
            }

}
