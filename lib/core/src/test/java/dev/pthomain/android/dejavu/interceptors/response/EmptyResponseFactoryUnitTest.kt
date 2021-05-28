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

package dev.pthomain.android.dejavu.interceptors.response

import com.nhaarman.mockitokotlin2.*
import dev.pthomain.android.dejavu.cache.metadata.token.CacheStatus.EMPTY
import dev.pthomain.android.dejavu.configuration.error.glitch.Glitch
import dev.pthomain.android.dejavu.test.assertEqualsWithContext
import dev.pthomain.android.dejavu.test.assertNotNullWithContext
import dev.pthomain.android.dejavu.test.assertNullWithContext
import dev.pthomain.android.dejavu.test.instructionToken
import dev.pthomain.android.dejavu.test.network.model.TestResponse
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.ErrorFactory
import org.junit.Before
import org.junit.Test

class EmptyResponseFactoryUnitTest {

    private lateinit var mockErrorFactory: ErrorFactory<Glitch>

    private lateinit var target: EmptyResponseFactory<Glitch>

    //TODO update test for DONE and EMPTY

    @Before
    fun setUp() {
        mockErrorFactory = mock()
        target = EmptyResponseFactory(mockErrorFactory)
    }

    @Test
    fun testEmptyResponseWrapperObservable() {
        val instructionToken = instructionToken()
        val mockError = mock<Glitch>()

        whenever(mockErrorFactory(any())).thenReturn(mockError)

        val wrapper = target.create(instructionToken)

        val captor = argumentCaptor<NoSuchElementException>()
        verify(mockErrorFactory).invoke(captor.capture())
        val capturedException = captor.firstValue

        assertNotNullWithContext(
                capturedException,
                "Wrong exception"
        )

        assertEqualsWithContext(
                TestResponse::class.java,
                wrapper.responseClass,
                "Wrong response class"
        )

        assertNullWithContext(
                wrapper.response,
                "Response should be null"
        )

        val metadata = wrapper.metadata

        assertEqualsWithContext(
                mockError,
                metadata.exception,
                "Exception didn't match"
        )

        assertEqualsWithContext(
                instructionToken.copy(status = EMPTY),
                metadata.cacheToken,
                "Cache token status should be EMPTY"
        )
    }

}