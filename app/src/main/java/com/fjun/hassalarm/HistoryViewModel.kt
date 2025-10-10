package com.fjun.hassalarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fjun.hassalarm.history.Publish
import com.fjun.hassalarm.history.PublishDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val publishDao: PublishDao) : ViewModel() {
    val list: StateFlow<List<Publish>> = publishDao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun clearHistory() {
        viewModelScope.launch {
            publishDao.deleteAll()
        }
    }
}

class HistoryViewModelFactory(private val publishDao: PublishDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(publishDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
