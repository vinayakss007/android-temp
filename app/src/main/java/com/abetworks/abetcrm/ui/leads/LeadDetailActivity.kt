package com.abetworks.abetcrm.ui.leads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.abetworks.abetcrm.data.model.*
import com.abetworks.abetcrm.databinding.ActivityLeadDetailBinding
import com.abetworks.abetcrm.databinding.ItemActivityBinding
import com.abetworks.abetcrm.ui.LeadViewModel
import com.abetworks.abetcrm.ui.LeadViewModelFactory
import com.abetworks.abetcrm.util.PhoneUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LeadDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeadDetailBinding
    private lateinit var viewModel: LeadViewModel
    private var lead: Lead? = null
    private val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeadDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]

        val leadId = intent.getLongExtra("lead_id", -1L)

        // Stage spinner
        val stages = LeadStage.values().map { it.name }
        binding.spinnerStage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, stages)

        if (leadId == -1L) {
            // New lead mode
            supportActionBar?.title = "New Lead"
            binding.groupActions.visibility = View.GONE
            binding.btnSave.setOnClickListener { saveNewLead() }
        } else {
            // Edit/view existing lead
            lifecycleScope.launch {
                viewModel.leads.collect { list ->
                    val found = list.find { it.id == leadId }
                    if (found != null && lead == null) {
                        lead = found
                        bindLead(found)
                    }
                }
            }
            // Activities
            val actAdapter = ActivityAdapter()
            binding.recyclerActivities.layoutManager = LinearLayoutManager(this)
            binding.recyclerActivities.adapter = actAdapter
            lifecycleScope.launch {
                viewModel.activitiesFor(leadId).collect { acts ->
                    actAdapter.submitList(acts)
                }
            }
            binding.btnSave.setOnClickListener { updateLead() }
            binding.btnWhatsapp.setOnClickListener {
                lead?.let {
                    val uri = Uri.parse(PhoneUtils.waLink(it.whatsappNumber.ifBlank { it.phone }))
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
            binding.btnCall.setOnClickListener {
                lead?.let {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(PhoneUtils.dialUri(it.phone))))
                }
            }
            binding.btnAddNote.setOnClickListener {
                val note = binding.etNote.text.toString().trim()
                if (note.isNotBlank()) {
                    viewModel.addNote(leadId, note)
                    binding.etNote.text?.clear()
                }
            }
            binding.btnDelete.setOnClickListener {
                lead?.let { l ->
                    viewModel.deleteLead(l)
                    finish()
                }
            }
        }
    }

    private fun bindLead(lead: Lead) {
        supportActionBar?.title = lead.name
        binding.etName.setText(lead.name)
        binding.etPhone.setText(lead.phone)
        binding.etWhatsapp.setText(lead.whatsappNumber)
        binding.etNotes.setText(lead.notes)
        binding.etLastMsg.setText(lead.lastMessage)
        binding.spinnerStage.setSelection(LeadStage.values().indexOf(lead.stage))
        if (lead.lastMessage.isNotBlank()) {
            binding.tvLastMsg.visibility = View.VISIBLE
            binding.tvLastMsg.text = "Last WhatsApp: \"${lead.lastMessage}\""
        }
    }

    private fun saveNewLead() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(this, "Name and phone required", Toast.LENGTH_SHORT).show()
            return
        }
        val stage = LeadStage.values()[binding.spinnerStage.selectedItemPosition]
        val lead = Lead(
            name = name, phone = PhoneUtils.normalize(phone),
            whatsappNumber = binding.etWhatsapp.text.toString().trim().ifBlank { PhoneUtils.normalize(phone) },
            notes = binding.etNotes.text.toString().trim(),
            stage = stage, source = LeadSource.MANUAL
        )
        viewModel.saveLead(lead)
        finish()
    }

    private fun updateLead() {
        val current = lead ?: return
        val stage = LeadStage.values()[binding.spinnerStage.selectedItemPosition]
        val updated = current.copy(
            name = binding.etName.text.toString().trim().ifBlank { current.name },
            notes = binding.etNotes.text.toString().trim(),
            stage = stage
        )
        viewModel.saveLead(updated)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }
}

// ── Activity Log Adapter ──────────────────────────────────────────────────────
class ActivityAdapter : RecyclerView.Adapter<ActivityAdapter.VH>() {
    private var list = listOf<Activity>()
    private val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    fun submitList(l: List<Activity>) { list = l; notifyDataSetChanged() }

    inner class VH(val b: ItemActivityBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = list.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val act = list[position]
        val icon = when (act.type) {
            ActivityType.CALL_INCOMING    -> "📞"
            ActivityType.CALL_OUTGOING    -> "📤"
            ActivityType.CALL_MISSED      -> "📵"
            ActivityType.WHATSAPP_MESSAGE -> "💬"
            ActivityType.NOTE             -> "📝"
            ActivityType.STAGE_CHANGE     -> "🔄"
            ActivityType.CONTACT_IMPORTED -> "👤"
        }
        holder.b.tvActivityIcon.text = icon
        holder.b.tvActivityDesc.text = act.description
        holder.b.tvActivityTime.text = sdf.format(Date(act.timestamp))
    }
}
