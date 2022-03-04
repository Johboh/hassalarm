package com.fjun.hassalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fjun.hassalarm.databinding.ActivityHistoryBinding
import com.fjun.hassalarm.history.AppDatabase
import com.fjun.hassalarm.history.Publish
import com.fjun.hassalarm.history.PublishDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {


    class HistoryViewModel(publishDao: PublishDao) : ViewModel() {
        val list: LiveData<List<Publish>> = publishDao.getAll()
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

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(AppDatabase.getDatabase(this).publishDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bindings = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(bindings.root)
        val adapter = HistoryAdapter() {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle(getString(R.string.history_error_dialog_title))
            dialog.setMessage(it.errorMessage)
            dialog.setPositiveButton(R.string.history_error_dialog_button_ok, null)
            dialog.create().show()
        }
        bindings.list.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            this.adapter = adapter
            viewModel.list.observe(this@HistoryActivity) {
                adapter.submitList(it)
            }
        }
        bindings.buttonHistoryClear.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@HistoryActivity).publishDao().deleteAll()
                }
                Toast.makeText(this@HistoryActivity, R.string.history_cleared, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = Intent(context, HistoryActivity::class.java)
    }
}