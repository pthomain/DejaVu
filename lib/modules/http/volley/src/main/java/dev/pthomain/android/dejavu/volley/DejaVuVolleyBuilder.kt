package dev.pthomain.android.dejavu.volley

import dev.pthomain.android.boilerplate.core.builder.ExtensionBuilder
import dev.pthomain.android.glitchy.core.interceptor.interceptors.error.NetworkErrorPredicate
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DejaVuVolleyBuilder<E> internal constructor()
    : ExtensionBuilder<DejaVuVolley<E>, Module, DejaVuVolleyBuilder<E>>
        where E : Throwable,
              E : NetworkErrorPredicate {

    private var parentModules: List<Module>? = null

    private val module = module {
        single { VolleyObservable.Factory<E>(get(), get(), get()) }
    }

    override fun accept(modules: List<Module>) = apply {
        parentModules = modules
    }

    /**
     * Returns an instance of DejaVu.
     */
    override fun build(): DejaVuVolley<E> {
        val parentModules = this.parentModules
                ?: throw IllegalStateException("This builder needs to call DejaVuBuilder::extend")

        return koinApplication {
            modules(parentModules + module)
        }.koin.run {
            DejaVuVolley(get())
        }
    }
}