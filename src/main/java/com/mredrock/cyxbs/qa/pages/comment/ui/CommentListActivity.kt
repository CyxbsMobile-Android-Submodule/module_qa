package com.mredrock.cyxbs.qa.pages.comment.ui

import android.app.Activity
import android.app.ProgressDialog
import android.os.Bundle
import android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.alibaba.android.arouter.facade.annotation.Route
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mredrock.cyxbs.common.bean.RedrockApiWrapper
import com.mredrock.cyxbs.common.bean.isSuccessful
import com.mredrock.cyxbs.common.config.QA_COMMENT_LIST
import com.mredrock.cyxbs.common.event.OpenShareCommentEvent
import com.mredrock.cyxbs.common.service.ServiceManager
import com.mredrock.cyxbs.common.service.account.IAccountService
import com.mredrock.cyxbs.common.ui.BaseActivity
import com.mredrock.cyxbs.common.utils.extensions.gone
import com.mredrock.cyxbs.common.utils.extensions.toast
import com.mredrock.cyxbs.common.viewmodel.event.ProgressDialogEvent
import com.mredrock.cyxbs.qa.R
import com.mredrock.cyxbs.qa.bean.Answer
import com.mredrock.cyxbs.qa.bean.Question
import com.mredrock.cyxbs.qa.component.recycler.RvAdapterWrapper
import com.mredrock.cyxbs.qa.network.NetworkState
import com.mredrock.cyxbs.qa.pages.answer.ui.dialog.ReportDialog
import com.mredrock.cyxbs.qa.pages.comment.AdoptAnswerEvent
import com.mredrock.cyxbs.qa.pages.comment.viewmodel.CommentListViewModel
import com.mredrock.cyxbs.qa.ui.adapter.EmptyRvAdapter
import com.mredrock.cyxbs.qa.ui.adapter.FooterRvAdapter
import com.mredrock.cyxbs.qa.utils.setPraise
import kotlinx.android.synthetic.main.qa_activity_comment_list.*
import kotlinx.android.synthetic.main.qa_comment_new_publish_layout.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.indeterminateProgressDialog
import org.jetbrains.anko.longToast
import org.jetbrains.anko.singleLine
import org.jetbrains.anko.startActivityForResult

@Route(path = QA_COMMENT_LIST)
class CommentListActivity : BaseActivity() {
    companion object {
        const val REQUEST_CODE = 0x123
        const val PARAM_QUESTION = "question"
        const val PARAM_ANSWER = "answer"

        fun activityStart(activity: Activity,
                          question: Question,
                          answer: Answer) {
            activity.startActivityForResult<CommentListActivity>(REQUEST_CODE,
                    PARAM_QUESTION to question,
                    PARAM_ANSWER to answer)
        }
    }

    private lateinit var viewModel: CommentListViewModel
    private var progressDialog: ProgressDialog? = null
    private fun initProgressBar() = indeterminateProgressDialog(message = "Loading...") {
        setOnDismissListener { viewModel.onProgressDialogDismissed() }
    }

    override val isFragmentActivity = false

    private lateinit var headerAdapter: CommentListHeaderRvAdapter
    private lateinit var emptyRvAdapter: EmptyRvAdapter
    private lateinit var footerRvAdapter: FooterRvAdapter
    private lateinit var commentListRvAdapter: CommentListRvAdapter

    private lateinit var answer: Answer
    private lateinit var question: Question


    override fun onCreate(savedInstanceState: Bundle?) {
        answer = intent.getParcelableExtra(PARAM_ANSWER)
        question = intent.getParcelableExtra(PARAM_QUESTION)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_comment_list)
        if (intent.getParcelableExtra<Question>(PARAM_QUESTION) != null && intent.getParcelableExtra<Answer>(PARAM_ANSWER) != null) {
            answer = intent.getParcelableExtra(PARAM_ANSWER)
            question = intent.getParcelableExtra(PARAM_QUESTION)
            initViewModel(question.id, answer)
            val showAdoptIcon = question.hasAdoptedAnswer || !question.isSelf
            val isEmotion = question.isEmotion
            initToolbar()
            initRv(showAdoptIcon, isEmotion)
            initCommentSheet()
        }
    }

    private fun initViewModel(questionId: String, answer: Answer) {
        viewModel = ViewModelProviders.of(this, CommentListViewModel.Factory(questionId, answer)).get(CommentListViewModel::class.java)
        viewModel.apply {
            toastEvent.observe { str -> str?.let { toast(it) } }
            longToastEvent.observe { str -> str?.let { longToast(it) } }
            progressDialogEvent.observe {
                it ?: return@observe
                if (it != ProgressDialogEvent.DISMISS_DIALOG_EVENT && progressDialog?.isShowing != true) {
                    progressDialog = progressDialog ?: initProgressBar()
                    progressDialog?.show()
                } else if (it == ProgressDialogEvent.DISMISS_DIALOG_EVENT && progressDialog?.isShowing != false) {
                    progressDialog?.dismiss()
                }
            }
        }
    }

    private fun initToolbar() {
        qa_ib_toolbar_back.setOnClickListener { finish() }
        val commentNub = answer.commentNum
        qa_tv_toolbar_title.text = baseContext.getString(R.string.qa_comment_list_comment_count, commentNub)
        val mStuNum = ServiceManager.getService(IAccountService::class.java).getUserService().getStuNum()
        if (answer.userId == mStuNum) {
            qa_ib_toolbar_more.setOnClickListener {
                ReportDialog(this).apply {
                    setType(resources.getStringArray(R.array.qa_title_type)[1])
                    pressReport = {
                        viewModel.reportAnswer(it)
                    }
                    viewModel.backPreActivityReportAnswerEvent.observeNotNull {
                        dismiss()
                    }
                }.show()
            }
        } else {
            qa_ib_toolbar_more.gone()
        }
    }

    private fun initRv(showAdoptIcon: Boolean, isEmotion: Boolean) {
        headerAdapter = CommentListHeaderRvAdapter(isEmotion, showAdoptIcon)
        emptyRvAdapter = EmptyRvAdapter(getString(R.string.qa_comment_list_no_comment_hint))
        commentListRvAdapter = CommentListRvAdapter(isEmotion).apply {
            onReportClickListener = { commentId ->
                ReportDialog(this@CommentListActivity).apply {
                    setType(resources.getStringArray(R.array.qa_title_type)[2])
                    pressReport = {
                        viewModel.reportComment(it, commentId)
                    }
                    viewModel.backPreActivityReportAnswerEvent.observeNotNull {
                        dismiss()
                    }
                }.show()
            }
        }
        footerRvAdapter = FooterRvAdapter { viewModel.retryFailedListRequest() }
        val adapterWrapper = RvAdapterWrapper(commentListRvAdapter, headerAdapter, footerRvAdapter, emptyRvAdapter)
        rv_comment_list.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CommentListActivity)
            adapter = adapterWrapper
        }
        swipe_refresh_layout.setOnRefreshListener { viewModel.invalidateCommentList() }
        observeListChangeEvent()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun adoptAnswer(event: AdoptAnswerEvent) {
        viewModel.adoptAnswer(event.aId)
    }

    private inline fun <T> LiveData<T>.observe(crossinline onChange: (T?) -> Unit) = observe(this@CommentListActivity, Observer { onChange(it) })

    private inline fun <T> LiveData<T>.observeNotNull(crossinline onChange: (T) -> Unit) = observe(this@CommentListActivity, Observer {
        it ?: return@Observer
        onChange(it)
    })

    private fun observeListChangeEvent() = viewModel.apply {
        answerLiveData.observeNotNull { headerAdapter.refreshData(listOf(it)) }

        commentList.observe { commentListRvAdapter.submitList(it) }

        networkState.observeNotNull { footerRvAdapter.refreshData(listOf(it)) }

        initialLoad.observe {
            when (it) {
                NetworkState.LOADING -> {
                    swipe_refresh_layout.isRefreshing = true
                    emptyRvAdapter.showHolder(3)
                }
                else -> {
                    swipe_refresh_layout.isRefreshing = false
                    emptyRvAdapter.hideHolder()
                }
            }
        }
        refreshPreActivityEvent.observeNotNull {
            tv_comment_praise.setPraise(answerLiveData.value?.praiseNum, answerLiveData.value?.isPraised)
        }
    }

    private fun initCommentSheet() {
        et_new_comment.apply {
            inputType = TYPE_TEXT_FLAG_MULTI_LINE
            singleLine = false
            setOnEditorActionListener { p0, p1, p2 ->
                if (p1 == EditorInfo.IME_ACTION_SEND) {
                    viewModel.sendComment(et_new_comment.text.toString())
                    et_new_comment.text = null
                }
                false
            }
        }
        tv_comment_praise.apply {
            setPraise(answer.praiseNum, answer.isPraised)
            setOnClickListener { viewModel.clickPraiseButton() }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun openShareComment(event: OpenShareCommentEvent) {
        if (intent.getParcelableExtra<Question>(PARAM_QUESTION) != null && intent.getParcelableExtra<Question>(PARAM_ANSWER) != null) {
            EventBus.getDefault().removeStickyEvent(event)
        } else {
            var answerWrapper: RedrockApiWrapper<Answer>? = null

            try {
                answerWrapper = Gson().fromJson<RedrockApiWrapper<Answer>>(event.answerJson,
                        object : TypeToken<RedrockApiWrapper<Answer>>() {}.type)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (answerWrapper == null) {
                toast("无法识别问题,可能问题已过期")
            } else if (!answerWrapper.isSuccessful) {
                toast(answerWrapper.info ?: "未知错误")
            } else {
                initViewModel(event.questionId, answerWrapper.data)
                val showAdoptIcon = question.hasAdoptedAnswer || !question.isSelf
                val isEmotion = question.isEmotion
                initToolbar()
                initRv(showAdoptIcon, isEmotion)
                initCommentSheet()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (progressDialog?.isShowing == true) {
            progressDialog?.dismiss()
        }
    }
}
