package info.chrzanowski.airpets.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import info.chrzanowski.airpets.PetOptions
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.visibleSessions
import javax.swing.Icon

internal fun petMenuActions(
    options: PetOptions,
    demo: () -> Boolean,
    sessions: List<PetSession>,
    message: String?,
    onEnabled: (Boolean) -> Unit,
    onDemo: (Boolean) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onLabels: (Boolean) -> Unit,
    onCost: (Boolean) -> Unit,
    onDuration: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
): DefaultActionGroup = DefaultActionGroup().apply {
    add(menuToggle("Show pets", { options.enabled }, onEnabled))
    add(menuToggle("Animate pets", { options.animated }, onAnimated))
    add(menuToggle("Show session names on hover", { options.labels }, onLabels))
    add(menuToggle("Show session cost", { options.cost }, onCost, "AIR-reported cost in USD; ≈ marks an estimate."))
    add(
        menuToggle(
            "Show session time",
            { options.duration },
            onDuration,
            "Working time observed during this project opening, excluding waiting and historical work.",
        ),
    )
    addSeparator(if (demo()) "Demo Sessions (Not Live Data)" else "Live Sessions")
    message?.let {
        add(
            menuAction(it, it, AllIcons.General.Warning, enabled = false) {},
        )
    }
    val visible = visibleSessions(sessions, Int.MAX_VALUE)
    add(
        object : DefaultActionGroup("All Sessions (${visible.size})", true) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = visible.isNotEmpty()
                event.presentation.isHideGroupIfEmpty = false
            }
        }.apply {
            visible.forEach { session ->
                add(
                    menuAction(
                        "${session.title.take(80)} — ${session.state.label}",
                        session.detail,
                        session.state.menuIcon(),
                    ) { onOpen(session.id) },
                )
            }
        },
    )
    add(
        object : DefaultActionGroup("State Guide", true) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(event: AnActionEvent) {
                event.presentation.isDisableGroupIfEmpty = false
            }
        }.apply {
            PetState.entries.forEach { state ->
                add(
                    menuAction("${state.badge}  ${state.label}", null, state.menuIcon(), enabled = false) {},
                )
            }
        },
    )
    addSeparator()
    add(menuToggle("Demo mode", demo, onDemo))
}

private fun menuToggle(
    text: String,
    selected: () -> Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) =
    object : DumbAwareToggleAction(text) {
        init {
            templatePresentation.description = description
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(event: AnActionEvent) = selected()
        override fun setSelected(event: AnActionEvent, state: Boolean) = onChange(state)
    }

private fun menuAction(text: String, description: String?, icon: Icon?, enabled: Boolean = true, onClick: () -> Unit) =
    object : DumbAwareAction(text, description, icon) {
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = enabled
        }

        override fun actionPerformed(event: AnActionEvent) = onClick()
    }

private fun PetState.menuIcon() = when (this) {
    PetState.WORKING -> AllIcons.RunConfigurations.TestState.Run
    PetState.WAITING -> AllIcons.General.QuestionDialog
    PetState.REVIEW -> AllIcons.Actions.Preview
    PetState.RESTING -> AllIcons.Actions.Pause
    PetState.DONE -> AllIcons.General.InspectionsOK
    PetState.ERROR -> AllIcons.General.Error
}