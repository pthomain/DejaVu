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

package dev.pthomain.android.dejavu.persistence.sqlite

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.pthomain.android.boilerplate.core.utils.io.useAndLogError
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.persistence.sqlite.SqlOpenHelperCallback.Companion.COLUMNS.*
import dev.pthomain.android.dejavu.persistence.sqlite.SqlOpenHelperCallback.Companion.TABLE_DEJA_VU
import dev.pthomain.android.dejavu.persistence.statistics.BaseStatisticsCompiler
import dev.pthomain.android.dejavu.persistence.statistics.CacheEntry
import dev.pthomain.android.dejavu.shared.token.getCacheStatus
import java.util.*

/**
 * Provides a concrete StatisticsCompiler implementation for SQLite database entries.
 *
 * @param configuration the global cache configuration
 * @param logger a Logger instance
 * @param dateFactory the factory converting timestamps to Dates
 * @param database the SQLite database containing the cache entries
 */
class DatabaseStatisticsCompiler internal constructor(
        private val logger: Logger,
        private val dateFactory: (Long?) -> Date,
        private val database: SupportSQLiteDatabase
) : BaseStatisticsCompiler<Cursor, CursorIterator>() {

    private val projection = arrayOf(
            CLASS.columnName,
            IS_ENCRYPTED.columnName,
            IS_COMPRESSED.columnName,
            DATE.columnName,
            EXPIRY_DATE.columnName
    )

    private val query = """
                    SELECT ${projection.joinToString(", ")}
                    FROM $TABLE_DEJA_VU
                    ORDER BY ${CLASS.columnName} ASC
                """

    /**
     * Returns an CursorIterator of the database entries.
     *
     * @return the CursorIterator
     */
    override fun loadEntries() =
            database.query(query) //FIXME no entries shown
                    .useAndLogError(::CursorIterator, logger)

    /**
     * Converts a database Cursor to a CacheEntry.
     *
     * @param entry the current cursor
     * @return the converted entry
     */
    override fun convert(entry: Cursor) = with(entry) {
        val responseClassName = getString(getColumnIndex(CLASS.columnName))
        val isEncrypted = getInt(getColumnIndex(IS_ENCRYPTED.columnName)) != 0
        val isCompressed = getInt(getColumnIndex(IS_COMPRESSED.columnName)) != 0
        val cacheDate = dateFactory(getLong(getColumnIndex(DATE.columnName)))
        val expiryDate = dateFactory(getLong(getColumnIndex(EXPIRY_DATE.columnName)))
        val status = dateFactory.getCacheStatus(expiryDate)

        CacheEntry(
                Class.forName(responseClassName),
                status,
                isEncrypted,
                isCompressed,
                cacheDate,
                expiryDate
        )
    }

}