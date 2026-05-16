package com.abetworks.abetcrm.ui.pipeline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abetworks.abetcrm.data.model.*
import com.abetworks.abetcrm.databinding.FragmentPipelineBinding
import com.abetworks.abetcrm.databinding.ItemPipelineColumnBinding
import com.abetworks.abetcrm.databinding.ItemPipelineLeadBinding
import com.abetworks.abetcrm.ui.MainActivity
import com.abetworks.abetcrm.ui.leads.LeadDetailActivity
import com.abetworks.abetcrm.util.PhoneUtils
import kotlinx.coroutines.launch

class PipelineFragment : Fragment() {

    private var _binding: FragmentPipelineBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentPipelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).viewModel
        val adapter = PipelineAdapter(
            onLeadClick = { lead ->
                val intent = Intent(requireContext(), LeadDetailActivity::class.java)
                intent.putExtra("lead_id", lead.id)
                startActivity(intent)
            },
            onStageChange = { lead, stage -> vm.updateStage(lead.id, stage) },
            onWhatsAppClick = { lead ->
                val uri = Uri.parse(PhoneUtils.waLink(lead.whatsappNumber.ifBlank { lead.phone }))
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        )
        binding.recyclerPipeline.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerPipeline.adapter = adapter

        lifecycleScope.launch {
            vm.pipeline.collect { pipeline ->
                adapter.submitPipeline(pipeline)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Column Adapter ─────────────────────────────────────────────────────────
class PipelineAdapter(
    private val onLeadClick: (Lead) -> Unit,
    private val onStageChange: (Lead, LeadStage) -> Unit,
    private val onWhatsAppClick: (Lead) -> Unit
) : RecyclerView.Adapter<PipelineAdapter.ColumnVH>() {

    private val columns = LeadStage.values().toList()
    private var pipeline = mapOf<LeadStage, List<Lead>>()

    fun submitPipeline(p: Map<LeadStage, List<Lead>>) { pipeline = p; notifyDataSetChanged() }

    inner class ColumnVH(val b: ItemPipelineColumnBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ColumnVH(ItemPipelineColumnBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = columns.size

    override fun onBindViewHolder(holder: ColumnVH, position: Int) {
        val stage = columns[position]
        val leads = pipeline[stage] ?: emptyList()
        holder.b.tvStageTitle.text = stage.name
        holder.b.tvCount.text = leads.size.toString()

        val stageColor = when (stage) {
            LeadStage.NEW        -> 0xFF4F8EF7.toInt()
            LeadStage.CONTACTED  -> 0xFFF7A84F.toInt()
            LeadStage.INTERESTED -> 0xFFA84FF7.toInt()
            LeadStage.WON        -> 0xFF4FF79E.toInt()
            LeadStage.LOST       -> 0xFFF74F4F.toInt()
        }
        holder.b.viewStageColor.setBackgroundColor(stageColor)

        val leadAdapter = PipelineLeadAdapter(leads, onLeadClick, onStageChange, onWhatsAppClick)
        holder.b.recyclerLeads.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.b.recyclerLeads.adapter = leadAdapter
    }
}

// ── Lead card inside column ─────────────────────────────────────────────────
class PipelineLeadAdapter(
    private val leads: List<Lead>,
    private val onLeadClick: (Lead) -> Unit,
    private val onStageChange: (Lead, LeadStage) -> Unit,
    private val onWhatsAppClick: (Lead) -> Unit
) : RecyclerView.Adapter<PipelineLeadAdapter.VH>() {

    inner class VH(val b: ItemPipelineLeadBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPipelineLeadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = leads.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val lead = leads[position]
        holder.b.tvLeadName.text = lead.name
        holder.b.tvLeadPhone.text = PhoneUtils.format(lead.phone)
        holder.b.tvLastMsg.text = if (lead.lastMessage.isNotBlank()) "💬 ${lead.lastMessage.take(50)}" else ""
        holder.b.tvLastMsg.visibility = if (lead.lastMessage.isNotBlank()) View.VISIBLE else View.GONE
        holder.b.root.setOnClickListener { onLeadClick(lead) }
        holder.b.btnWA.setOnClickListener { onWhatsAppClick(lead) }

        // Quick stage advance button
        val nextStage = when (lead.stage) {
            LeadStage.NEW        -> LeadStage.CONTACTED
            LeadStage.CONTACTED  -> LeadStage.INTERESTED
            LeadStage.INTERESTED -> LeadStage.WON
            else -> null
        }
        if (nextStage != null) {
            holder.b.btnAdvance.visibility = View.VISIBLE
            holder.b.btnAdvance.text = "→ ${nextStage.name}"
            holder.b.btnAdvance.setOnClickListener { onStageChange(lead, nextStage) }
        } else {
            holder.b.btnAdvance.visibility = View.GONE
        }
    }
}
