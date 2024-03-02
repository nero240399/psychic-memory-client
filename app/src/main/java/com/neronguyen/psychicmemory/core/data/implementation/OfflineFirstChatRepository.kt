package com.neronguyen.psychicmemory.core.data.implementation

import com.neronguyen.psychicmemory.core.data.ChatRepository
import com.neronguyen.psychicmemory.core.database.LocalDataSource
import com.neronguyen.psychicmemory.core.database.model.asExternalModel
import com.neronguyen.psychicmemory.core.firebase.util.currentUser
import com.neronguyen.psychicmemory.core.firebase.util.getToken
import com.neronguyen.psychicmemory.core.model.ChatMessage
import com.neronguyen.psychicmemory.core.network.NetworkDataSource
import com.neronguyen.psychicmemory.core.network.model.asEntity
import com.neronguyen.psychicmemory.core.network.model.asExternalModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineFirstChatRepository(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: LocalDataSource,
    private val ioDispatcher: CoroutineDispatcher
) : ChatRepository {


    override suspend fun connectToSocket(): Flow<ChatMessage> {
        return withContext(ioDispatcher) {
            networkDataSource.connectToSocket(
                url = "ws://192.168.1.12:8080/chat",
                token = getToken()
            ).map { it.asExternalModel(currentUser.id) }
        }
    }

    override suspend fun sendMessage(message: String) {
        withContext(ioDispatcher) {
            networkDataSource.sendMessage(message)
        }
    }

    override suspend fun disconnect() {
        withContext(ioDispatcher) {
            networkDataSource.disconnectSocket()
        }
    }

    override fun getChatHistoryStream(): Flow<List<ChatMessage>> {
        return localDataSource.observeAllMessages()
            .map { messages ->
                messages.map { message ->
                    message.asExternalModel(currentUser.id)
                }
            }
    }

    override suspend fun refreshChatHistory() {
        withContext(ioDispatcher) {
            val chatHistory = async {
                networkDataSource.getChatHistory(
                    url = "http://192.168.1.12:8080/chatHistory",
                    token = getToken()
                )
            }
            localDataSource.deleteAllMessages()
            localDataSource.insertMessages(chatHistory.await().map { it.asEntity() })
        }
    }
}