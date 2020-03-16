package com.mredrock.cyxbs.qa.pages.answer.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.mredrock.cyxbs.common.service.ServiceManager
import com.mredrock.cyxbs.common.service.account.IAccountService
import com.mredrock.cyxbs.common.utils.extensions.gone
import com.mredrock.cyxbs.common.utils.extensions.invisible
import com.mredrock.cyxbs.common.utils.extensions.setAvatarImageFromUrl
import com.mredrock.cyxbs.common.utils.extensions.visible
import com.mredrock.cyxbs.qa.R
import com.mredrock.cyxbs.qa.bean.Answer
import com.mredrock.cyxbs.qa.component.recycler.BaseEndlessRvAdapter
import com.mredrock.cyxbs.qa.component.recycler.BaseViewHolder
import com.mredrock.cyxbs.qa.utils.setPraise
import com.mredrock.cyxbs.qa.utils.timeDescription
import kotlinx.android.synthetic.main.qa_recycler_item_answer.view.*

/**
 * Created By jay68 on 2018/9/30.
 */
class AnswerListAdapter : BaseEndlessRvAdapter<Answer>(DIFF_CALLBACK) {
    companion object {
        @JvmStatic
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Answer>() {
            override fun areItemsTheSame(oldItem: Answer, newItem: Answer) = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Answer, newItem: Answer) = oldItem == newItem
        }
    }

    var onPraiseClickListener: ((Int, Answer) -> Unit)? = null
    var onReportClickListener: ((String) -> Unit)? = null
    var onItemClickListener: ((Int, Answer) -> Unit)? = null


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AnswerViewHolder(parent)

    override fun onItemClickListener(holder: BaseViewHolder<Answer>, position: Int, data: Answer) {
        super.onItemClickListener(holder, position, data)
        onItemClickListener?.invoke(position, data)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<Answer>, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.itemView.apply {
            tv_answer_praise_count.setOnClickListener {
                getItem(position)?.let { it1 -> onPraiseClickListener?.invoke(position, it1) }
            }
            if (getItem(position)?.userId != ServiceManager.getService(IAccountService::class.java).getUserService().getStuNum())
                btn_answer_more.setOnClickListener {
                    getItem(position)?.id?.let { it1 -> onReportClickListener?.invoke(it1) }
                }
        }
    }

    inner class AnswerViewHolder(parent: ViewGroup) : BaseViewHolder<Answer>(parent, R.layout.qa_recycler_item_answer) {
        override fun refresh(data: Answer?) {
            data ?: return
            itemView.apply {
                //判断是否显示
                when (data.userId) {
                    ServiceManager.getService(IAccountService::class.java).getUserService().getStuNum() -> {
                        btn_answer_more.invisible()
                    }
                    else -> {
                        btn_answer_more.visible()
                    }
                }
                when (data.commentNumInt) {
                    0 -> {
                        tv_answer_reply_count.gone()
                    }
                    else -> {
                        tv_answer_reply_count.visible()
                    }
                }
                iv_answer_avatar.setAvatarImageFromUrl(data.photoThumbnailSrc)
                tv_answer_nickname.text = data.nickname
                if (data.isAdopted) {
                    tv_adopted.visible()
                }
                tv_answer_content.text = data.content
                tv_answer_publish_at.text = timeDescription(System.currentTimeMillis(), data.createdAt)
                tv_answer_reply_count.text = context.getString(R.string.qa_answer_reply_count, data.commentNum)
                tv_answer_praise_count.setPraise(data.praiseNum, data.isPraised)

            }
        }
    }
}