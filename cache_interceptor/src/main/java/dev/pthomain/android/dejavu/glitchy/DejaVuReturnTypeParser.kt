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

package dev.pthomain.android.dejavu.glitchy

import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.dejavu.interceptors.response.DejaVuResult
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import dev.pthomain.android.glitchy.retrofit.RetrofitCallAdapterFactory.Companion.getFirstParameterUpperBound
import dev.pthomain.android.glitchy.retrofit.RetrofitCallAdapterFactory.Companion.rawType
import dev.pthomain.android.glitchy.retrofit.type.ParsedType
import dev.pthomain.android.glitchy.retrofit.type.ReturnTypeParser
import dev.pthomain.android.glitchy.retrofit.type.RxReturnTypeParser
import io.reactivex.Observable
import io.reactivex.Single
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

//TODO JavaDoc
internal class DejaVuReturnTypeParser<E> : ReturnTypeParser<DejaVuReturnType>
        where E : Throwable,
              E : NetworkErrorPredicate {

    override fun parseReturnType(returnType: Type,
                                 annotations: Array<Annotation>): ParsedType<DejaVuReturnType> {
        val parsedRxType = RxSingleReturnTypeParser.parseReturnType(
                returnType,
                annotations
        )

        val isSingle = parsedRxType.metadata
        val parsedType = parsedRxType.parsedType

        val isDejaVuResult = rawType(parsedType) == DejaVuResult::class.java
        val responseClass = getFirstParameterUpperBound(parsedType)!! as Class<*>

        val (parsedResultType, outcomeType) = if (isDejaVuResult) {
            wrapToRx(responseClass, isSingle) to parsedType
        } else parsedRxType.returnType to parsedType

        return ParsedType(
                DejaVuReturnType(isDejaVuResult, isSingle, responseClass),
                parsedResultType,
                outcomeType
        )
    }

    private fun wrapToRx(outcomeType: Type,
                         isSingle: Boolean): Type = object : ParameterizedType {
        override fun getRawType() = ifElse(isSingle, Single::class.java, Observable::class.java)
        override fun getOwnerType() = null
        override fun getActualTypeArguments() = arrayOf(outcomeType)
    }

}

internal data class DejaVuReturnType(
        val isDejaVuResult: Boolean,
        val isSingle: Boolean,
        val responseClass: Class<*>
)

internal object RxSingleReturnTypeParser
    : ReturnTypeParser<Boolean> by RxReturnTypeParser({ rawType(it) == Single::class.java })