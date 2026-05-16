package com.abetworks.abetcrm.ui

import android.app.Application
import androidx.lifecycle.*
import com.abetworks.abetcrm.data.model.Lead
import com.abetworks.abetcrm.data.model.LeadStage
import com.abetworks.abetcrm.data.repository.LeadRepository
import com.abetworks.abetcrm.service.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LeadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LeadRepository(app)

    // ── Search / filter state ─────────────────────────────────────────────
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _stageFilter = MutableStateFlow<LeadStage?>(null)
    val stageFilter: StateFlow<LeadStage?> = _stageFilter

    // ── Derived lead list ─────────────────────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    val leads: StateFlow<List<Lead>> = _query
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) repo.allLeads() else repo.searchLeads(q)
        }
        .combine(_stageFilter) { list, stage ->
            if (stage == null) list else list.filter { it.stage == stage }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Pipeline columns ──────────────────────────────────────────────────
    val pipeline: StateFlow<Map<LeadStage, List<Lead>>> = leads
        .map { list -> LeadStage.values().associateWith { stage -> list.filter { it.stage == stage } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Stats ─────────────────────────────────────────────────────────────
    data class Stats(val total: Int, val won: Int, val whatsapp: Int)
    private val _stats = MutableLiveData<Stats>()
    val stats: LiveData<Stats> = _stats

    // ── UI state ──────────────────────────────────────────────────────────
    private val _toastMsg = MutableSharedFlow<String>()
    val toastMsg = _toastMsg.asSharedFlow()

    init { refreshStats() }

    fun setQuery(q: String) { _query.value = q }
    fun setStageFilter(stage: LeadStage?) { _stageFilter.value = stage }

    fun updateStage(leadId: Long, stage: LeadStage) = viewModelScope.launch {
        repo.updateStage(leadId, stage)
        SyncManager.scheduleSyncNow(getApplication())
    }

    fun addNote(leadId: Long, note: String) = viewModelScope.launch {
        repo.addNote(leadId, note)
    }

    fun saveLead(lead: Lead) = viewModelScope.launch {
        repo.saveLead(lead)
        SyncManager.scheduleSyncNow(getApplication())
        _toastMsg.emit("Lead saved")
    }

    fun deleteLead(lead: Lead) = viewModelScope.launch {
        repo.deleteLead(lead)
        _toastMsg.emit("Lead deleted")
    }

    fun importContacts() = viewModelScope.launch {
        val count = repo.importContacts()
        _toastMsg.emit("Imported $count contacts as leads")
        refreshStats()
    }

    fun importCallLog() = viewModelScope.launch {
        val count = repo.importCallLog()
        _toastMsg.emit("Imported $count calls as leads")
        refreshStats()
    }

    fun syncNow() {
        SyncManager.scheduleSyncNow(getApplication())
        viewModelScope.launch { _toastMsg.emit("Sync triggered") }
    }

    private fun refreshStats() = viewModelScope.launch {
        val (total, won, wa) = repo.stats()
        _stats.postValue(Stats(total, won, wa))
    }

    fun activitiesFor(leadId: Long) = repo.activitiesForLead(leadId)
}

class LeadViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LeadViewModel(app) as T
    }
}
