package com.abetworks.abetcrm.ui.leads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abetworks.abetcrm.R
import com.abetworks.abetcrm.data.model.*
import com.abetworks.abetcrm.databinding.FragmentLeadsBinding
import com.abetworks.abetcrm.databinding.ItemLeadBinding
import com.abetworks.abetcrm.ui.MainActivity
import com.abetworks.abetcrm.ui.LeadViewModel
import com.abetworks.abetcrm.util.PhoneUtils
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LeadsFragment : Fragment() {

    private var _binding: FragmentLeadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LeadViewModel
    private lateinit var adapter: LeadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = (requireActivity() as MainActivity).viewModel

        setupRecyclerView()
        setupSearch()
        setupStageChips()
        setupFab()
        observeLeads()
        observeStats()
    }

    private fun setupRecyclerView() {
        adapter = LeadAdapter(
            onLeadClick = { lead ->
                val intent = Intent(requireContext(), LeadDetailActivity::class.java)
                intent.putExtra("lead_id", lead.id)
                startActivity(intent)
            },
            onWhatsAppClick = { lead ->
                val uri = Uri.parse(PhoneUtils.waLink(lead.whatsappNumber.ifBlank { lead.phone }))
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onCallClick = { lead ->
                val uri = Uri.parse(PhoneUtils.dialUri(lead.phone))
                startActivity(Intent(Intent.ACTION_DIAL, uri))
            }
        )
        binding.recyclerLeads.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLeads.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                viewModel.setQuery(q ?: "")
                return true
            }
        })
    }

    private fun setupStageChips() {
        val stages = listOf(null) + LeadStage.values().toList()
        stages.forEach { stage ->
            val chip = Chip(requireContext()).apply {
                text = stage?.name ?: "All"
                isCheckable = true
                isChecked = stage == null
                setOnClickListener { viewModel.setStageFilter(stage) }
            }
            binding.chipGroupStages.addView(chip)
        }
    }

    private fun setupFab() {
        binding.fabAddLead.setOnClickListener {
            val intent = Intent(requireContext(), LeadDetailActivity::class.java)
            intent.putExtra("lead_id", -1L) // -1 = new lead
            startActivity(intent)
        }

        binding.btnImportContacts.setOnClickListener { viewModel.importContacts() }
        binding.btnImportCalls.setOnClickListener { viewModel.importCallLog() }
        binding.btnSync.setOnClickListener { viewModel.syncNow() }
    }

    private fun observeLeads() {
        lifecycleScope.launch {
            viewModel.leads.collect { leads ->
                adapter.submitList(leads)
                binding.tvEmpty.visibility = if (leads.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeStats() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvStatTotal.text   = stats.total.toString()
            binding.tvStatWon.text     = stats.won.toString()
            binding.tvStatWA.text      = stats.whatsapp.toString()
            binding.tvConversion.text  = if (stats.total > 0) "${(stats.won * 100 / stats.total)}%" else "0%"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Lead Adapter ─────────────────────────────────────────────────────────────
class LeadAdapter(
    private val onLeadClick: (Lead) -> Unit,
    private val onWhatsAppClick: (Lead) -> Unit,
    private val onCallClick: (Lead) -> Unit
) : RecyclerView.Adapter<LeadAdapter.VH>() {

    private var leads = listOf<Lead>()
    private val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun submitList(list: List<Lead>) { leads = list; notifyDataSetChanged() }

    inner class VH(val b: ItemLeadBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLeadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = leads.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val lead = leads[position]
        val b = holder.b

        b.tvName.text   = lead.name
        b.tvPhone.text  = PhoneUtils.format(lead.phone)
        b.tvSource.text = lead.source.name
        b.tvStage.text  = lead.stage.name

        b.tvLastMsg.visibility = if (lead.lastMessage.isNotBlank()) View.VISIBLE else View.GONE
        b.tvLastMsg.text       = "💬 ${lead.lastMessage.take(60)}"

        b.tvFollowUp.visibility = if (lead.followUpDate != null) View.VISIBLE else View.GONE
        lead.followUpDate?.let { b.tvFollowUp.text = "🔔 ${sdf.format(Date(it))}" }

        // Stage color indicator
        val stageColor = when (lead.stage) {
            LeadStage.NEW        -> 0xFF4F8EF7.toInt()
            LeadStage.CONTACTED  -> 0xFFF7A84F.toInt()
            LeadStage.INTERESTED -> 0xFFA84FF7.toInt()
            LeadStage.WON        -> 0xFF4FF79E.toInt()
            LeadStage.LOST       -> 0xFFF74F4F.toInt()
        }
        b.viewStageBar.setBackgroundColor(stageColor)

        b.root.setOnClickListener          { onLeadClick(lead) }
        b.btnWhatsapp.setOnClickListener   { onWhatsAppClick(lead) }
        b.btnCall.setOnClickListener       { onCallClick(lead) }
    }
}
