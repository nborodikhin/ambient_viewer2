package com.pinnacleimagingsystems.ambientviewer2.viewer

import androidx.lifecycle.*
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import com.pinnacleimagingsystems.ambientviewer2.ConsumableEvent
import com.pinnacleimagingsystems.ambientviewer2.R

class ViewerActivity : AppCompatActivity(), ViewerFragment.Host {
    companion object {
        const val PARAM_FILES = "files"
        const val PARAM_VIEWER_MODE = "continuous"

        private const val FRAGMENT_TAG = "viewerFragment"
    }

    sealed class Command {
        object CloseActivity: Command()

        fun asConsumable() = ConsumableEvent(this)
    }

    interface Presenter: LifecycleObserver {
        val latestCommand: LiveData<ConsumableEvent<Command>>
        val currentFile: LiveData<String>
        val files: Array<String>

        fun hasPrev(): Boolean
        fun hasNext(): Boolean

        fun viewNext()
        fun viewPrev()

        fun onViewerError(filename: String)
    }

    class PresenterImpl: Presenter, ViewModel() {
        val uninitializedFiles = arrayOf<String>()
        override var files = uninitializedFiles
        private val banned = mutableSetOf<String>()

        private var curIndex = 0

        override val latestCommand = MutableLiveData<ConsumableEvent<Command>>()
        override val currentFile = MutableLiveData<String>()

        fun init(files: Array<String>?) {
            @Suppress("SuspiciousEqualsCombination")
            if (this.files !== uninitializedFiles) {
                return
            }

            this.files = files ?: uninitializedFiles
            this.curIndex = -1

            if (hasNext()) {
                viewNext()
            } else {
                exit()
            }
        }

        private fun exit() {
            post(Command.CloseActivity)
        }

        private fun updateCurrentFile() {
            currentFile.postValue(files[curIndex])
        }

        override fun hasPrev(): Boolean {
            return prevIndex() != null
        }

        private fun prevIndex(): Int? {
            var index = curIndex - 1
            while (index >= 0 && files[index] in banned) {
                index--
            }

            return if (index >= 0) index else null
        }

        override fun hasNext(): Boolean {
            return nextIndex() != null
        }

        private fun nextIndex(): Int? {
            var index = curIndex + 1

            while(index < files.size && files[index] in banned) {
                index++
            }

            return if (index < files.size) index else null
        }

        override fun onViewerError(filename: String) {
            ban(filename)
            if (hasNext()) {
                viewNext()
            } else if (hasPrev()) {
                viewPrev()
            } else {
                exit()
            }
        }

        private fun ban(file: String) {
            banned.add(file)
        }

        override fun viewNext() {
            val index = nextIndex()
            if (index != null) {
                curIndex = index
                updateCurrentFile()
            } else {
                exit()
            }
        }

        override fun viewPrev() {
            val index = prevIndex()
            if (index != null) {
                curIndex = index
                updateCurrentFile()
            }
        }

        private fun post(command: Command) {
            latestCommand.postValue(command.asConsumable())
        }
    }

    private val views by lazy {
        object {
            val mainLayout: View = findViewById(R.id.main_layout)
            val viewPager: androidx.viewpager.widget.ViewPager = findViewById(R.id.view_pager)
            val fragmentContent: View = findViewById(R.id.fragment_content)
        }
    }

    lateinit var presenter: Presenter
    lateinit var fragmentAdapter: androidx.viewpager.widget.PagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_viewer)

        presenter = ViewModelProviders.of(this)[PresenterImpl::class.java].apply {
            init(intent.getStringArrayExtra(PARAM_FILES))
        }
        val viewMode = intent.getBooleanExtra(PARAM_VIEWER_MODE, false)

        setupFullscreen()

        lifecycle.addObserver(presenter)

        presenter.latestCommand.observe(
                this,
                Observer { value -> value?.consume(this@ViewerActivity::onCommand) }
        )

        fragmentAdapter = object : androidx.fragment.app.FragmentStatePagerAdapter(supportFragmentManager) {
            override fun getItem(index: Int) = ViewerFragment.create(presenter.files[index], index, viewMode)
            override fun getCount() = presenter.files.size
        }

        views.viewPager.run {
            adapter = fragmentAdapter

            val pageChangeListener = object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(p0: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    val visiblePosition = when {
                        positionOffsetPixels == 0 -> currentItem
                        currentItem == position -> currentItem + 1
                        else -> currentItem - 1
                    }
                    post { onPageVisible(visiblePosition) }
                }

                override fun onPageSelected(position: Int) {
                    post { onPageVisible(position) }
                }
            }
            addOnPageChangeListener(pageChangeListener)
        }
    }

    fun onPageVisible(position: Int) {
        (getFragment(position) as? (ViewerFragment))?.apply {
            onVisible()
        }
    }

    private fun getFragment(position: Int): androidx.fragment.app.Fragment? {
        return supportFragmentManager.fragments
                .find { fragment -> (fragment as? ViewerFragment)?.fileId == position}
    }

    private fun onCommand(command: Command?) {
        command ?: return

        @Suppress("UNUSED_VARIABLE")
        val foo = when(command) {
            is Command.CloseActivity -> {
                finish()
            }
        }
    }

    private fun setupFullscreen() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                makeUiFullscreen()
            }
        }

        makeUiFullscreen()
    }

    private fun makeUiFullscreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()
    }

    override fun onViewerError(file: String?) {
        file ?: return
        presenter.onViewerError(file)
    }
}
