package uk.co.glass_software.android.cache_interceptor.configuration

import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.Type.*

data class CacheInstruction constructor(val responseClass: Class<*>,
                                        val operation: Operation) {

    sealed class Operation(val type: Type) {

        object DoNotCache : Operation(DO_NOT_CACHE)

        sealed class Expiring(val durationInMillis: Long?,
                              val freshOnly: Boolean,
                              val mergeOnNextOnError: Boolean?,
                              val encrypt: Boolean?,
                              val compress: Boolean?,
                              val filterFinal: Boolean,
                              type: Type) : Operation(type) {

            class Cache(durationInMillis: Long? = null,
                        freshOnly: Boolean = false,
                        mergeOnNextOnError: Boolean? = null,
                        encrypt: Boolean? = null,
                        compress: Boolean? = null,
                        filterFinal: Boolean = false)
                : Expiring(
                    durationInMillis,
                    freshOnly,
                    mergeOnNextOnError,
                    encrypt,
                    compress,
                    filterFinal,
                    CACHE
            )

            class Refresh(durationInMillis: Long? = null,
                          freshOnly: Boolean = false,
                          mergeOnNextOnError: Boolean? = null,
                          filterFinal: Boolean = false)
                : Expiring(
                    durationInMillis,
                    freshOnly,
                    mergeOnNextOnError,
                    null,
                    null,
                    filterFinal,
                    REFRESH
            )

            class Offline(freshOnly: Boolean = false,
                          mergeOnNextOnError: Boolean? = null)
                : Expiring(
                    null,
                    freshOnly,
                    mergeOnNextOnError,
                    null,
                    null,
                    false,
                    OFFLINE
            )
        }

        object Invalidate : Operation(INVALIDATE)

        data class Clear(val typeToClear: Class<*>? = null,
                         val clearOldEntriesOnly: Boolean = false) : Operation(CLEAR)

        enum class Type(val annotationName: String) {
            DO_NOT_CACHE("@DoNotCache"),
            CACHE("@Cache"),
            INVALIDATE("@Invalidate"),
            REFRESH("@Refresh"),
            OFFLINE("@Offline"),
            CLEAR("@Clear"),
            CLEAR_ALL("@Clear")
        }

        override fun toString() = type.name
    }

}