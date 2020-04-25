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

package dev.pthomain.android.dejavu.configuration

import android.content.Context
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.DejaVu
import dev.pthomain.android.dejavu.cache.TransientResponse
import dev.pthomain.android.dejavu.di.DejaVuComponent
import dev.pthomain.android.dejavu.shared.PersistenceManager
import dev.pthomain.android.dejavu.shared.Serialiser
import dev.pthomain.android.dejavu.shared.token.instruction.RequestMetadata
import dev.pthomain.android.dejavu.shared.token.instruction.operation.Operation.Remote
import dev.pthomain.android.glitchy.interceptor.error.ErrorFactory
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate

abstract class ConfigurationBuilder<E>
        where E : Throwable,
              E : NetworkErrorPredicate {

    private var logger: Logger = SilentLogger
    private var context: Context? = null
    private var errorFactory: ErrorFactory<E>? = null
    private var persistenceManager: PersistenceManager? = null
    private var serialiser: Serialiser? = null
    private var operationPredicate: (RequestMetadata<*>) -> Remote? = CachePredicate.Inactive
    private var durationPredicate: (TransientResponse<*>) -> Int? = { null }

    /**
     * Sets custom logger.
     */
    fun withContext(context: Context) =
            apply { this.context = context }

    /**
     * Sets custom logger.
     */
    fun withLogger(logger: Logger) =
            apply { this.logger = logger }

    /**
     * Sets custom Serialiser.
     */
    fun withSerialiser(serialiser: Serialiser) =
            apply { this.serialiser = serialiser }

    /**
     * Sets a custom ErrorFactory implementation.
     */
    fun withErrorFactory(errorFactory: ErrorFactory<E>) =
            apply { this.errorFactory = errorFactory }

    /**
     * Provide a different PersistenceManager to handle the persistence of the cached requests.
     * The default implementation is FilePersistenceManager which saves the responses to
     * the filesystem.
     *
     * @see dev.pthomain.android.dejavu.interceptors.cache.persistence.base.KeyValuePersistenceManager
     * @see dev.pthomain.android.dejavu.interceptors.cache.persistence.database.DatabasePersistenceManager
     *
     * @param persistenceManager the PersistenceManager implementation to override the default one
     */
    fun withPersistence(persistenceManager: PersistenceManager) =
            apply { this.persistenceManager = persistenceManager }

    /**
     * Sets a predicate for ad-hoc response caching. This predicate will be called for every
     * request and will always take precedence on the provided request operation.
     *
     * It will be called with the target response class and associated request metadata before
     * the call is made in order to establish the operation to associate with that request.
     * @see Remote //TODO explain remote vs local
     *
     * Returning null means the cache will instead take into account the directives defined
     * as annotation or header on that request (if present).
     *
     * To cache all response with default CACHE operation and expiration period, use CachePredicate.CacheAll.
     * To disable any caching, use CachePredicate.CacheNone.
     *
     * Otherwise, you can implement your own predicate to return the appropriate operation base on
     * the given RequestMetadata for the request being made.
     *///TODO rename to Provider
    fun withOperationPredicate(operationPredicate: (metadata: RequestMetadata<*>) -> Remote?) =
            apply { this.operationPredicate = operationPredicate }

    /**
     * Sets a predicate for ad-hoc response duration caching.
     *
     * If not null, the value returned by this predicate will take precedence on the
     * cache duration provided in the request's operation.
     *
     * This is useful for responses containing cache duration information, enabling server-side
     * cache control.
     *///TODO rename to Provider
    fun withDurationPredicate(durationPredicate: (TransientResponse<*>) -> Int?) =
            apply { this.durationPredicate = durationPredicate }


    protected abstract fun componentProvider(
            context: Context,
            logger: Logger,
            errorFactory: ErrorFactory<E>,
            serialiser: Serialiser,
            persistenceManager: PersistenceManager,
            operationPredicate: (metadata: RequestMetadata<*>) -> Remote?,
            durationPredicate: (TransientResponse<*>) -> Int?
    ): DejaVuComponent<E>

    /**
     * Returns an instance of DejaVu.
     */
    fun build() = DejaVu(
            componentProvider(
                    checkProvided(context?.applicationContext),
                    logger,
                    checkProvided(errorFactory),
                    checkProvided(serialiser),
                    checkProvided(persistenceManager),
                    operationPredicate,
                    durationPredicate
            )
    )

    private inline fun <reified T> checkProvided(target: T?): T = target
            ?: throw IllegalStateException("Please provide an instance of ${T::class.java.simpleName}")
}