package info.chrzanowski.airpets.ui

import com.intellij.air.shared.core.icons.AirSharedCoreIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.AirPetsService
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager

class AirPetsStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = ID
    override fun getDisplayName() = "Air Pets"
    override fun isAvailable(project: Project) = !project.isDisposed
    override fun createWidget(project: Project): StatusBarWidget = AirPetsStatusBarWidget(project)

    companion object {
        const val ID = "AirPets"
    }
}

internal class AirPetsStatusBarWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget.TextPresentation {
    private val component = TextPanel.WithIconAndArrows(::getTooltipText).apply {
        icon = AirSharedCoreIcons.Air
        setTextAlignment(JComponent.CENTER_ALIGNMENT)
        border = JBUI.CurrentTheme.StatusBar.Widget.border()
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                getClickConsumer().consume(event)
                return true
            }
        }.installOn(this)
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    init {
        updatePresentation()
    }

    override fun ID() = AirPetsStatusBarWidgetFactory.ID
    override fun getComponent() = component
    override fun getPresentation() = this
    override fun getText() = project.service<AirPetsService>().statusText
    override fun getAlignment() = JComponent.CENTER_ALIGNMENT
    override fun getTooltipText() = project.service<AirPetsService>().statusTooltip
    override fun getClickConsumer() = Consumer<MouseEvent> { event ->
        if (!project.isDisposed) {
            (event.component as? JComponent)?.let { project.service<AirPetsService>().showStatusBarMenu(it) }
        }
    }

    fun updatePresentation() {
        component.text = getText()
        component.revalidate()
        component.repaint()
    }

    override fun dispose() {
        ToolTipManager.sharedInstance().unregisterComponent(component)
    }
}