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

package dev.pthomain.android.dejavu.interceptors.internal.cache.persistence.file

import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.dejavu.interceptors.internal.cache.persistence.CacheDataHolder
import java.util.*

//TODO test
internal class FileNameSerialiser(
        private val dateFactory: (Long?) -> Date
) {

    fun serialise(cacheDataHolder: CacheDataHolder) =
            with(cacheDataHolder) {
                listOf(
                        hash,
                        expiryDate.toString(),
                        responseClassName,
                        ifElse(isCompressed, "1", "0"),
                        ifElse(isEncrypted, "1", "0")
                ).joinToString(SEPARATOR)
            }

    fun deserialise(fileName: String) =
            with(fileName.split(SEPARATOR)) {
                if (size != 5) null
                else CacheDataHolder(
                        get(0),
                        dateFactory(null).time,
                        get(1).toLong(),
                        ByteArray(0),
                        get(2),
                        get(3) == "1",
                        get(4) == "1"
                )
            }

    companion object {
        const val SEPARATOR = "_-_"
    }
}
