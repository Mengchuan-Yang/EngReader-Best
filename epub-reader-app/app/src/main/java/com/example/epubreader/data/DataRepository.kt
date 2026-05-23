package com.engreader.app.data

import com.engreader.app.data.local.EngReaderDatabase
import com.engreader.app.data.repository.createRoomRepositories
import com.engreader.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Legacy facade kept for current UI wiring while the richer domain repositories are introduced.
 */
interface DataRepository {
  val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Android")) }
}

class RoomDataRepository(private val bookRepository: BookRepository) : DataRepository {
  override val data: Flow<List<String>> =
    bookRepository.observeBooks().map { books -> books.map { it.title } }
}

fun createDataRepository(database: EngReaderDatabase): DataRepository =
  RoomDataRepository(createRoomRepositories(database).books)
