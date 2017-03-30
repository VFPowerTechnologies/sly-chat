package io.slychat.messenger.android.activites.services

import android.widget.AbsListView


abstract class OnScrollFinishListener : AbsListView.OnScrollListener {

    internal var mCurrentScrollState: Int = 0
    internal var mCurrentVisibleItemCount: Int = 0

    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
        mCurrentScrollState = scrollState
        if (isScrollCompleted) {
            onScrollFinished()
        }
    }

    override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        mCurrentVisibleItemCount = visibleItemCount
    }

    private val isScrollCompleted: Boolean
        get() = mCurrentVisibleItemCount > 0 && mCurrentScrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE

    protected abstract fun onScrollFinished()
}