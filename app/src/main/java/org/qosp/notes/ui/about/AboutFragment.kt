package org.qosp.notes.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import io.noties.markwon.Markwon
import net.mamoe.yamlkt.Yaml
import org.acra.ACRA
import org.koin.android.ext.android.inject
import org.qosp.notes.BuildConfig
import org.qosp.notes.R
import org.qosp.notes.data.WhatsNew
import org.qosp.notes.data.WhatsNewItem
import org.qosp.notes.databinding.FragmentAboutBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.viewBinding

class AboutFragment : BaseFragment(resId = R.layout.fragment_about) {
    private val binding by viewBinding(FragmentAboutBinding::bind)
    private val changelog by lazy { loadChangelog() }

    override val hasMenu = false
    override val toolbar: Toolbar
        get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String
        get() = getString(R.string.nav_about)

    val markwon: Markwon by inject()

    private fun launchUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar.appBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )
        binding.appVersion.subText = getString(
            R.string.about_version_value,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        val versionedEntries = changelog.updates.filter { !it.version.isNullOrBlank() }
        binding.actionViewChangelog.isVisible = versionedEntries.isNotEmpty()
        versionedEntries.maxByOrNull { it.id }?.let { latestEntry ->
            binding.actionViewChangelog.subText = latestEntry.version?.let { version ->
                getString(R.string.about_changelog_latest_subtext, version, latestEntry.title)
            } ?: latestEntry.title
        }
        binding.actionSupport.isVisible = true
    }

    private fun setupListeners() = with(binding) {
        actionViewChangelog.setOnClickListener { showChangelogDialog() }
        actionWebsite.setOnClickListener { launchUrl(requireContext().getString(R.string.app_website)) }
        actionContribute.setOnClickListener { launchUrl(requireContext().getString(R.string.app_repo)) }
        actionVisitDeveloper.setOnClickListener { launchUrl(requireContext().getString(R.string.app_developer_repo)) }
        actionViewLibraries.setOnClickListener { showLibrariesDialog() }
        actionSupport.setOnClickListener { launchUrl(requireContext().getString(R.string.app_support_page)) }
        actionSendLogs.setOnClickListener {
            try {
                ACRA.errorReporter.setEnabled(true)
                ACRA.errorReporter.handleException(null)
            } finally {
                ACRA.errorReporter.setEnabled(false)
            }
        }
    }

    private fun loadChangelog(): WhatsNew {
        return runCatching {
            val yamlText = requireContext().assets.open("whatsnew.yaml").bufferedReader().use { it.readText() }
            Yaml.decodeFromString(WhatsNew.serializer(), yamlText)
        }.getOrElse {
            WhatsNew(emptyList())
        }
    }

    private fun showChangelogDialog() {
        BaseDialog.build(requireContext()) {
            setTitle(R.string.about_changelog)
            setMessage(markwon.toMarkdown(buildChangelogMarkdown()))
            setPositiveButton(R.string.ok, null)
        }.show()
    }

    private fun buildChangelogMarkdown(): String {
        return changelog.updates
            .filter { !it.version.isNullOrBlank() }
            .sortedByDescending { it.id }
            .joinToString("\n\n") { entry ->
                buildEntryMarkdown(entry)
            }
    }

    private fun buildEntryMarkdown(entry: WhatsNewItem): String {
        val header = entry.version?.let { "## $it" } ?: "## ${entry.title}"
        val subtitle = entry.version?.let { "**${entry.title}**\n\n" }.orEmpty()
        val items = entry.items.joinToString("\n") { "- $it" }
        return "$header\n\n$subtitle$items"
    }

    private fun showLibrariesDialog() {
        BaseDialog.build(requireContext()) {
            setTitle(R.string.about_libraries)
            setMessage(markwon.toMarkdown(getString(R.string.licenses_markdown_text)))
            setPositiveButton(R.string.ok, null)
        }
            .show()
    }
}
