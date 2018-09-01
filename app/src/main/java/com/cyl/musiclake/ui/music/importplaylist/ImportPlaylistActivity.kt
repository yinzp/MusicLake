package com.cyl.musiclake.ui.music.importplaylist

import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.View
import android.webkit.WebView
import com.afollestad.materialdialogs.MaterialDialog
import com.cyl.musiclake.R
import com.cyl.musiclake.api.MusicApiServiceImpl
import com.cyl.musiclake.api.PlaylistApiServiceImpl
import com.cyl.musiclake.base.BaseActivity
import com.cyl.musiclake.base.BaseContract
import com.cyl.musiclake.base.BasePresenter
import com.cyl.musiclake.bean.Music
import com.cyl.musiclake.bean.Playlist
import com.cyl.musiclake.common.Constants
import com.cyl.musiclake.common.NavigationHelper
import com.cyl.musiclake.event.PlaylistEvent
import com.cyl.musiclake.net.ApiManager
import com.cyl.musiclake.net.RequestCallBack
import com.cyl.musiclake.player.PlayManager
import com.cyl.musiclake.ui.OnlinePlaylistUtils
import com.cyl.musiclake.ui.music.dialog.BottomDialogFragment
import com.cyl.musiclake.ui.music.local.adapter.SongAdapter
import com.cyl.musiclake.utils.ToastUtils
import kotlinx.android.synthetic.main.activity_import_playlist.*
import org.greenrobot.eventbus.EventBus


class ImportPlaylistActivity : BaseActivity<BasePresenter<BaseContract.BaseView>>() {

    var mAdapter: SongAdapter? = null
    var name: String? = null
    var vendor: String? = null
    var musicList = mutableListOf<Music>()

    override fun getLayoutResID(): Int {
        return R.layout.activity_import_playlist
    }

    override fun initView() {
    }

    override fun initData() {
    }

    override fun initInjector() {
    }

    override fun listener() {
        super.listener()
        syncBtn.setOnClickListener {
            showLoading(true)
            val playlistLink = playlistInputView.editText?.text.toString()
            getPlaylistId(playlistLink)
        }
        importBtn.setOnClickListener {
            if (name == null) {
                ToastUtils.show("请先同步获取歌曲！")
                return@setOnClickListener
            }
            if (vendor == null) {
                ToastUtils.show("请先同步获取歌曲！")
                return@setOnClickListener
            }
            if (musicList.size == 0) return@setOnClickListener
            MaterialDialog.Builder(this)
                    .title("是否将${musicList.size}首歌导入到歌单")
                    .positiveText("确定")
                    .negativeText("取消")
                    .inputRangeRes(2, 20, R.color.red)
                    .input("请输入歌单名", name.toString(), false) { _, _ -> }
                    .onPositive { dialog1, _ ->
                        val title = dialog1.inputEditText?.text.toString()
                        OnlinePlaylistUtils.createPlaylist(title, success = {
                            it.pid?.let { it1 ->
                                ApiManager.request(PlaylistApiServiceImpl.collectBatchMusic(it1, vendor.toString(), musicList), object : RequestCallBack<String> {
                                    override fun success(result: String?) {
                                        this@ImportPlaylistActivity.finish()
                                        ToastUtils.show(result)
                                        EventBus.getDefault().post(PlaylistEvent(Constants.PLAYLIST_CUSTOM_ID))
                                    }

                                    override fun error(msg: String?) {
                                        ToastUtils.show(msg)
                                        EventBus.getDefault().post(PlaylistEvent(Constants.PLAYLIST_CUSTOM_ID))
                                    }

                                })
                            }
                        })
                    }.build()
                    .show()

        }
    }

    private fun getPlaylistId(link: String) {
        when {
            link.contains("http://music.163.com") -> {
                val len = link.lastIndexOf("playlist/") + "playlist/".length
                val id = link.substring(len, len + link.substring(len).indexOf("/"))
                importMusic("netease", id)
            }
            link.contains("http://y.qq.com") -> {
                val len = link.lastIndexOf("id=") + "id=".length
                val id = link.substring(len, len + link.substring(len).indexOf("&"))
                importMusic("qq", id)
            }
            link.contains("https://www.xiami.com") -> {
                val len = link.lastIndexOf("collect/") + "collect/".length
                val id = link.substring(len, link.indexOf("?"))
                importMusic("xiami", id)
            }
            else -> {
                ToastUtils.show("请输入有效的链接！")
            }
        }
    }

    private fun importMusic(vendor: String, url: String) {
        this.vendor = vendor
        val observable = MusicApiServiceImpl.getPlaylistSongs(vendor, url, 1, 20)
        ApiManager.request(observable, object : RequestCallBack<Playlist> {
            override fun success(result: Playlist) {
                showLoading(false)
                musicList.clear()
                result.musicList?.forEach {
                    if (!it.isCp) {
                        musicList.add(it)
                    }
                }
                result.musicList = musicList
                showResultAdapter(result)
            }

            override fun error(msg: String) {
                showLoading(false)
                ToastUtils.show("分享链接异常，解析失败！")
            }
        })
    }

    fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    fun showResultAdapter(result: Playlist) {
        mAdapter = SongAdapter(result.musicList)
        this.name = result.name
        resultRsv.adapter = mAdapter
        resultRsv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        mAdapter?.bindToRecyclerView(resultRsv)

        mAdapter?.setOnItemClickListener { adapter, view, position ->
            if (view.id != R.id.iv_more) {
                PlayManager.play(position, result.musicList, Constants.PLAYLIST_DOWNLOAD_ID + result.pid)
                mAdapter?.notifyDataSetChanged()
                NavigationHelper.navigateToPlaying(this@ImportPlaylistActivity, view.findViewById(R.id.iv_cover))
            }
        }
        mAdapter?.setOnItemChildClickListener { _, _, position ->
            BottomDialogFragment.newInstance(result.musicList[position]).show(this@ImportPlaylistActivity)
        }

    }

}
