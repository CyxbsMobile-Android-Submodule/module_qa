package com.mredrock.cyxbs.qa.pages.search.room

import androidx.room.*
import io.reactivex.Maybe

/**
 * Created by yyfbe, Date on 2020/8/16.
 */
@Dao
interface QAHistoryDao {
    @Insert
    fun insertHistory(history: QAHistory)

    @Delete
    fun delete(history: QAHistory)

    @Update
    fun update(history: QAHistory)

    @Query("SELECT * FROM QAHistory  ORDER BY qaHistory_time DESC LIMIT 0,:limit")
    fun getHistory(limit: Int = 15): Maybe<MutableList<QAHistory>>

    @Query("DELETE  FROM  QAHistory")
    fun deleteAll()
}