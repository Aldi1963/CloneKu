package top.niunaijun.blackboxa.view.list

import android.view.View
import android.view.ViewGroup
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.databinding.ItemPackageBinding
import top.niunaijun.blackboxa.util.CloneRisk



class ListAdapter : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return ListVH(inflate(R.layout.item_package,parent))
    }

    class ListVH(itemView:View) :RVHolder<InstalledAppBean>(itemView){

        val binding = ItemPackageBinding.bind(itemView)
        override fun setContent(item: InstalledAppBean, isSelected: Boolean, payload: Any?) {
            binding.icon.setImageDrawable(item.icon)
            binding.name.text = item.name
            binding.packageName.text = item.packageName
            binding.cornerLabel.visibility = if (item.isInstall) {
                View.VISIBLE
            } else {
                View.GONE
            }
            // Label risiko clone (item 3 + item 7): hanya heuristik, teksnya
            // sengaja memakai "kemungkinan"/"risiko", bukan kepastian.
            when (item.risk) {
                CloneRisk.Risk.PAYMENT -> {
                    binding.riskLabel.visibility = View.VISIBLE
                    binding.riskLabel.setText(R.string.clone_risk_payment_label)
                }
                CloneRisk.Risk.MESSAGING -> {
                    binding.riskLabel.visibility = View.VISIBLE
                    binding.riskLabel.setText(R.string.clone_risk_messaging_label)
                }
                CloneRisk.Risk.BOTH -> {
                    binding.riskLabel.visibility = View.VISIBLE
                    binding.riskLabel.setText(R.string.clone_risk_both_label)
                }
                CloneRisk.Risk.NONE -> {
                    binding.riskLabel.visibility = View.GONE
                }
            }
        }
    }
}