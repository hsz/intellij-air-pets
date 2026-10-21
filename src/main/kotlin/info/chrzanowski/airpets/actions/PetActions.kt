package info.chrzanowski.airpets.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import info.chrzanowski.airpets.AirPetsService

class TogglePetsAction : ToggleAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun isSelected(event: AnActionEvent) = event.project?.service<AirPetsService>()?.enabled == true
    override fun setSelected(event: AnActionEvent, state: Boolean) {
        event.project?.service<AirPetsService>()?.setEnabled(state)
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = event.project != null
    }
}

class ToggleDemoAction : ToggleAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun isSelected(event: AnActionEvent) = event.project?.service<AirPetsService>()?.demo == true
    override fun setSelected(event: AnActionEvent, state: Boolean) {
        event.project?.service<AirPetsService>()?.setDemo(state)
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = event.project != null
    }
}