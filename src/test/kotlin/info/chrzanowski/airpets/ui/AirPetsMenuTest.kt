package info.chrzanowski.airpets.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import info.chrzanowski.airpets.PetOptions
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class AirPetsMenuTest {
    @get:Rule
    val application: RuleChain = RuleChain.outerRule(ApplicationRule()).around(EdtRule())

    private val options = PetOptions()
    private var demo = false
    private val opened = mutableListOf<String>()
    private val changes = mutableListOf<String>()

    private fun menu(sessions: List<PetSession> = emptyList(), message: String? = null) = petMenuActions(
        options = options,
        demo = { demo },
        sessions = sessions,
        message = message,
        onEnabled = { options.enabled = it; changes += "enabled:$it" },
        onDemo = { demo = it; changes += "demo:$it" },
        onAnimated = { options.animated = it; changes += "animated:$it" },
        onLabels = { options.labels = it; changes += "labels:$it" },
        onCost = { options.cost = it; changes += "cost:$it" },
        onDuration = { options.duration = it; changes += "duration:$it" },
        onOpen = { opened += it },
    )

    @Test
    fun `preferences are native toggles with current state and separate callbacks`() {
        val group = menu()
        val toggles = group.childActionsOrStubs.filterIsInstance<ToggleAction>()
        assertEquals(
            listOf(
                "Show pets",
                "Animate pets",
                "Show session names on hover",
                "Show session cost",
                "Show session time",
                "Demo mode",
            ),
            toggles.map { it.templatePresentation.text },
        )
        toggles.forEachIndexed { index, action ->
            assertTrue(action is DumbAware)
            assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
            val event = event(action)
            assertEquals(index < 3, action.isSelected(event))
            action.actionPerformed(event)
            assertEquals(index >= 3, action.isSelected(event))
        }
        assertEquals(
            listOf(
                "enabled:false",
                "animated:false",
                "labels:false",
                "cost:true",
                "duration:true",
                "demo:true",
            ),
            changes,
        )
        assertTrue(opened.isEmpty())
        options.enabled = true
        assertTrue("Native updates must read current preferences", toggles.first().isSelected(event(toggles.first())))
        demo = false
        assertFalse(toggles.last().isSelected(event(toggles.last())))
    }

    @Test
    fun `cost and time controls are independent and read saved preferences`() {
        options.cost = true
        val toggles = menu().childActionsOrStubs.filterIsInstance<ToggleAction>()
            .associateBy { it.templatePresentation.text }
        val cost = toggles.getValue("Show session cost")
        val duration = toggles.getValue("Show session time")
        assertTrue(cost.isSelected(event(cost)))
        assertFalse(duration.isSelected(event(duration)))
        duration.actionPerformed(event(duration))
        assertTrue(options.cost)
        assertTrue(options.duration)
        cost.actionPerformed(event(cost))
        assertFalse(options.cost)
        assertTrue(options.duration)
        assertEquals(listOf("duration:true", "cost:false"), changes)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `session submenu preserves priority descriptions and identity navigation`() {
        val done = PetSession("done", "Same title", PetState.DONE, detail = "Completed work")
        val waiting = PetSession("waiting", "Same title", PetState.WAITING, detail = "Answer needed")
        val group = menu(listOf(done, waiting, done))
        val sessions = group.childActionsOrStubs.filterIsInstance<DefaultActionGroup>().first()
        assertTrue(sessions.isPopup)
        assertTrue(event(sessions).presentation.isEnabled)
        assertEquals("All Sessions (2)", sessions.templatePresentation.text)
        val actions = sessions.childActionsOrStubs
        assertEquals(
            listOf("Same title — Needs your input", "Same title — Completed"),
            actions.map { it.templatePresentation.text },
        )
        assertEquals(listOf("Answer needed", "Completed work"), actions.map { it.templatePresentation.description })
        actions.forEach { action ->
            assertTrue(action is DumbAware)
            assertNotNull(action.templatePresentation.icon)
            action.actionPerformed(event(action))
        }
        assertEquals(listOf("waiting", "done"), opened)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `hidden pets retain direct preferences and navigation to every session state`() {
        options.enabled = false
        val sessions = PetState.entries.map { PetSession(it.name, it.label, it) }
        val actions = menu(sessions).childActionsOrStubs
        assertEquals(6, actions.filterIsInstance<ToggleAction>().count { event(it).presentation.isEnabled })
        val groups = actions.filterIsInstance<DefaultActionGroup>()
        assertEquals(listOf("All Sessions (${sessions.size})", "State Guide"), groups.map { it.templatePresentation.text })
        groups.first().childActionsOrStubs.forEach { action ->
            assertTrue(event(action).presentation.isEnabled)
            action.actionPerformed(event(action))
        }
        assertEquals(sessions.map { it.id }.toSet(), opened.toSet())
        assertFalse(options.enabled)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `empty sessions keep preferences available and state guide non-actionable`() {
        val actions = menu().childActionsOrStubs
        assertEquals(6, actions.filterIsInstance<ToggleAction>().count { event(it).presentation.isEnabled })
        val sessions = actions.filterIsInstance<DefaultActionGroup>().first()
        assertEquals("All Sessions (0)", sessions.templatePresentation.text)
        assertFalse(event(sessions).presentation.isEnabled)
        assertTrue(sessions.childActionsOrStubs.isEmpty())
        val guide = actions.filterIsInstance<DefaultActionGroup>().last()
        assertEquals("State Guide", guide.templatePresentation.text)
        assertTrue(guide.isPopup)
        assertEquals(PetState.entries.size, guide.childActionsOrStubs.size)
        guide.childActionsOrStubs.forEachIndexed { index, action ->
            assertTrue(requireNotNull(action.templatePresentation.text).contains(PetState.entries[index].label))
            assertNotNull(action.templatePresentation.icon)
            assertFalse(event(action).presentation.isEnabled)
        }
    }

    @Test
    fun `demo and live data are identified by native section captions`() {
        assertTrue(menu().childActionsOrStubs.filterIsInstance<Separator>().any { it.text == "Live Sessions" })
        demo = true
        val actions = menu().childActionsOrStubs
        assertTrue(actions.filterIsInstance<Separator>().any { it.text == "Demo Sessions (Not Live Data)" })
        assertFalse(actions.filterIsInstance<Separator>().any { it.text == "Live Sessions" })
        val toggle = actions.filterIsInstance<ToggleAction>().last()
        assertTrue(toggle.isSelected(event(toggle)))
    }

    @Test
    fun `connection messages remain visible as disabled native warning actions`() {
        val message = "AIR session connection unavailable. Retrying automatically; demo is still available."
        val action = menu(message = message).childActionsOrStubs.single { it.templatePresentation.text == message }
        assertFalse(event(action).presentation.isEnabled)
        assertEquals(message, action.templatePresentation.description)
        assertNotNull(action.templatePresentation.icon)
        assertTrue(
            menu(message = message).childActionsOrStubs.filterIsInstance<ToggleAction>()
                .all { event(it).presentation.isEnabled },
        )
    }

    @Test
    fun `native popup supports search and retains disabled navigation and guide`() {
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            "AIR Pets", menu(), DataContext.EMPTY_CONTEXT, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
        )
        try {
            @Suppress("UNCHECKED_CAST")
            val step = popup.listStep as ListPopupStep<Any>
            assertTrue(step.isSpeedSearchEnabled)
            assertEquals("AIR Pets", step.title)
            val items = step.values.associateBy { step.getTextFor(it) }
            assertTrue(items.containsKey("State Guide"))
            assertTrue(step.isSelectable(items.getValue("State Guide")))
            assertTrue(items.containsKey("All Sessions (0)"))
            assertFalse(step.isSelectable(items.getValue("All Sessions (0)")))
            assertTrue(step.isSelectable(items.getValue("Show pets")))
            @Suppress("UNCHECKED_CAST")
            val guide = step.onChosen(items.getValue("State Guide"), false) as ListPopupStep<Any>
            assertEquals(PetState.entries.size, guide.values.size)
            assertTrue(guide.values.none { guide.isSelectable(it) })
        } finally {
            Disposer.dispose(popup)
        }
    }

    private fun event(action: AnAction) = AnActionEvent.createEvent(
        action,
        DataContext.EMPTY_CONTEXT,
        action.templatePresentation.clone(),
        "AirPets.Popup",
        ActionUiKind.POPUP,
        null,
    ).also { action.update(it) }
}