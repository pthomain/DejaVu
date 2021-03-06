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

package dev.pthomain.android.dejavu.demo.presenter.base

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.boilerplate.core.utils.rx.ioUi
import dev.pthomain.android.boilerplate.ui.mvp.MvpPresenter
import dev.pthomain.android.dejavu.cache.metadata.response.DejaVuResult
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.Behaviour
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.Behaviour.OFFLINE
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.Behaviour.ONLINE
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.FreshnessPriority
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.CachePriority.FreshnessPriority.ANY
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Local.Clear
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Local.Invalidate
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.DoNotCache
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Type.*
import dev.pthomain.android.dejavu.demo.DemoActivity
import dev.pthomain.android.dejavu.demo.DemoMvpContract.*
import dev.pthomain.android.dejavu.demo.dejavu.DejaVuClient
import dev.pthomain.android.dejavu.demo.dejavu.clients.base.ObservableClients
import dev.pthomain.android.dejavu.demo.dejavu.clients.base.SingleClients
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.DejaVuFactory
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.DejaVuFactory.PersistenceType
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.DejaVuFactory.PersistenceType.FILE
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.ErrorFactoryType
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.ErrorFactoryType.Default
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.SerialiserType
import dev.pthomain.android.dejavu.demo.dejavu.clients.factories.SerialiserType.Gson
import dev.pthomain.android.dejavu.demo.dejavu.clients.model.CatFactResponse
import dev.pthomain.android.dejavu.demo.presenter.base.CompositePresenter.Method.RETROFIT_ANNOTATION
import io.reactivex.Observable
import io.reactivex.Single

internal abstract class BaseDemoPresenter<S : SingleClients.Operations, O : ObservableClients.Operations, C : DejaVuClient<S, O>>
protected constructor(
        demoActivity: DemoActivity,
        private val uiLogger: Logger
) : MvpPresenter<DemoMvpView, DemoPresenter, DemoViewComponent>(demoActivity),
        DemoPresenter {

    protected val dejaVuFactory = DejaVuFactory(uiLogger, demoActivity)

    private var instructionType = CACHE
    private var behaviour = ONLINE

    override var serialiserType: SerialiserType = Gson
    protected var errorFactoryType: ErrorFactoryType<*> = Default

    final override var persistence = FILE
        set(value) {
            field = value
            dejaVuClient = newClient(persistence)
        }

    protected abstract fun newClient(persistence: PersistenceType): C

    protected var dejaVuClient: C = newClient(persistence)
        private set

    protected fun dataClient() = dejaVuClient.dataClient(useSingle)
    protected fun operationClient() = dejaVuClient.operationsClient(useSingle)

    final override var useSingle = false
    final override var method = RETROFIT_ANNOTATION
    final override var freshness = ANY
    final override var encrypt = false
    final override var compress = false

    private fun getCacheSerialisation() = when {
        encrypt && compress -> "compress,encrypt"
        encrypt -> "encrypt"
        compress -> "compress"
        else -> ""
    }

    final override fun getCacheOperation() =
            when (instructionType) {
                CACHE -> Cache(
                        priority = CachePriority.with(behaviour, freshness),
                        serialisation = getCacheSerialisation()
                )
                DO_NOT_CACHE -> DoNotCache
                INVALIDATE -> Invalidate
                CLEAR -> Clear()
            }

    final override fun loadCatFact(isRefresh: Boolean) {
        instructionType = CACHE
        behaviour = ifElse(isRefresh, Behaviour.INVALIDATE, ONLINE)

        subscribeData(
                getDataObservable(
                        CachePriority.with(behaviour, freshness),
                        encrypt,
                        compress
                )
        )
    }

    final override fun offline() {
        instructionType = CACHE
        behaviour = OFFLINE
        subscribeData(getOfflineSingle(freshness).toObservable())
    }

    final override fun clearEntries() {
        instructionType = CLEAR
        subscribeResult(getClearEntriesResult())
    }

    final override fun invalidate() {
        instructionType = INVALIDATE
        subscribeResult(getInvalidateResult())
    }

    private fun subscribeData(observable: Observable<CatFactResponse>) =
            observable.compose { composer<CatFactResponse>(it) }
                    .autoSubscribe(mvpView::showCatFact)

    private fun subscribeResult(observable: Observable<DejaVuResult<CatFactResponse>>) =
            observable.compose { composer<DejaVuResult<CatFactResponse>>(it) }
                    .autoSubscribe(mvpView::showResult)

    private fun <T : Any> composer(upstream: Observable<T>) =
            upstream.ioUi()
                    .doOnSubscribe { mvpView.onCallStarted() }
                    .doOnError { uiLogger.e(this, it) }
                    .doFinally(::onCallComplete)

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onDestroy() {
        subscriptions.clear()
    }

    private fun onCallComplete() {
        mvpView.onCallComplete()
//        dejaVu.getStatistics().ioUi()
//                .doOnSuccess { it.log(uiLogger) }
//                .doOnError { uiLogger.e(this, it, "Could not show stats") }
//                .autoSubscribe()
    }

    protected abstract fun getDataObservable(cachePriority: CachePriority,
                                             encrypt: Boolean,
                                             compress: Boolean): Observable<CatFactResponse>

    protected abstract fun getOfflineSingle(freshness: FreshnessPriority): Single<CatFactResponse>
    protected abstract fun getClearEntriesResult(): Observable<DejaVuResult<CatFactResponse>>
    protected abstract fun getInvalidateResult(): Observable<DejaVuResult<CatFactResponse>>

    companion object {
        internal const val BASE_URL = "https://catfact.ninja/"
        internal const val ENDPOINT = "fact"
    }

}

