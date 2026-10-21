package info.chrzanowski.airpets.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class PetQuickChatPopupTest {
    private val fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()
    private val scope = CoroutineScope(SupervisorJob())

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() {
        scope.cancel()
        fixture.tearDown()
    }

    @Test
    fun `popup title follows the session name including renames and untitled sessions`() = onEdt {
        val shown = mutableListOf<JBPopup>()
        var session = PetSession("a", "Refactor the cache", PetState.WAITING)
        val chat = PetQuickChatPopup(
            ProjectManager.getInstance().defaultProject, scope,
            { id -> session.takeIf { it.id == id } }, { _, _, _ -> null }, {}, {},
            showPopup = { popup, _ -> shown += popup },
        )
        fun assertTitle(expected: String) {
            val labels = shown.last().content.children().filterIsInstance<JLabel>().map { it.text }
            assertTrue("Expected popup title '$expected', found $labels", expected in labels)
        }
        try {
            chat.open("a", owner(), anchor())
            assertTitle(session.title)
            session = session.copy(title = "Review the cache changes")
            chat.refresh()
            assertTitle(session.title)
            session = session.copy(title = "  ")
            chat.refresh()
            assertTitle("Untitled session")
            chat.close()
            session = session.copy(id = "b", title = "Fix the build")
            chat.open("b", owner(), anchor())
            assertTitle(session.title)
        } finally {
            chat.dispose()
        }
    }

    @Test
    fun `dismissal and switching preserve the same composer and shutdown releases it`() = onEdt {
        val shown = mutableListOf<JBPopup>()
        val changes = mutableListOf<String?>()
        val views = mutableListOf<RecordingView>()
        val lifetimes = mutableListOf<Disposable>()
        val owner = owner()
        val chat = chat(shown, changes, create = { _, lifetime, _ ->
            lifetimes += lifetime
            RecordingView().also(views::add)
        })
        try {
            chat.open("a", owner, anchor())
            assertEquals("a", chat.sessionId)
            assertEquals(1, views.size)
            assertTrue(shown.single().content.children().contains(views.single().component))
            assertTrue(shown.single().isCancelKeyEnabled)
            views.single().draft.text = "An unsent reply"
            chat.open("a", owner, anchor())
            assertEquals("Repeat clicks must reuse the same composer", 1, views.size)
            chat.open("b", owner, anchor())
            assertEquals(0, views.first().disposals)
            assertFalse(Disposer.isDisposed(lifetimes.first()))
            assertTrue(shown.first().isDisposed)
            assertEquals(2, views.size)
            assertEquals(listOf("a", null, "b"), changes)
            chat.close()
            assertEquals(listOf("a", null, "b", null), changes)
            chat.open("a", owner, anchor())
            assertEquals("Reopening must not reconstruct AIR's composer", 2, views.size)
            assertEquals("An unsent reply", views.first().draft.text)
            assertTrue(shown.last().content.children().contains(views.first().component))
            shown.last().cancel()
            assertNull("Native Escape and outside dismissal must clear the active popup", chat.sessionId)
            chat.open("a", owner, anchor())
            assertEquals(2, views.size)
            assertEquals("An unsent reply", views.first().draft.text)
            chat.dispose()
            assertTrue(views.all { it.disposals == 1 })
            assertTrue(lifetimes.all(Disposer::isDisposed))
            chat.close()
            assertTrue(views.all { it.disposals == 1 })
        } finally {
            chat.dispose()
        }
    }

    @Test
    fun `unavailable native content offers explicit full AIR navigation and stale buttons cannot act`() = onEdt {
        val shown = mutableListOf<JBPopup>()
        val opened = mutableListOf<String>()
        var available = true
        val chat = chat(shown, create = { _, _, _ -> null }, onOpen = opened::add, available = { available })
        try {
            chat.open("a", owner(), anchor())
            assertTrue(shown.last().content.children().filterIsInstance<JTextArea>().any { it.text.contains("unavailable") })
            val stale = shown.last().content.children().filterIsInstance<JButton>().single { it.text == "Open in Air" }
            stale.doClick(0)
            assertEquals(listOf("a"), opened)
            assertNull(chat.sessionId)
            chat.open("b", owner(), anchor())
            stale.doClick(0)
            assertEquals("A closed session's action must not open another conversation", listOf("a"), opened)
            val removed = shown.last().content.children().filterIsInstance<JButton>().single { it.text == "Open in Air" }
            available = false
            removed.doClick(0)
            chat.refresh()
            assertNull(chat.sessionId)
            assertEquals(listOf("a"), opened)
        } finally {
            chat.dispose()
        }
    }

    @Test
    fun `closing cancels pending creation and removal or hiding closes the popup`() = onEdt {
        val shown = mutableListOf<JBPopup>()
        var cancelled = 0
        val owner = owner()
        val chat = chat(shown, create = { _, _, _ ->
            try { awaitCancellation() } finally { cancelled++ }
        })
        try {
            chat.open("a", owner, anchor())
            assertEquals("a", chat.sessionId)
            chat.close()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals(1, cancelled)
            chat.open("b", owner, anchor())
            owner.isVisible = false
            chat.refresh()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertNull(chat.sessionId)
            assertEquals(2, cancelled)
            val count = shown.size
            chat.open("a", owner, anchor())
            assertEquals(count, shown.size)
            owner.isVisible = true
            chat.dispose()
            chat.open("a", owner, anchor())
            assertEquals(count, shown.size)
        } finally {
            chat.dispose()
        }
    }

    @Test
    fun `removing a session releases its hidden native view and unknown sessions cannot open`() = onEdt {
        val shown = mutableListOf<JBPopup>()
        val views = mutableListOf<RecordingView>()
        var available = true
        val chat = chat(shown, create = { _, _, _ -> RecordingView().also(views::add) }, available = { available })
        try {
            chat.open("a", owner(), anchor())
            chat.close()
            assertEquals(0, views.single().disposals)
            available = false
            chat.refresh(retainMissing = true)
            assertEquals("A temporary discovery outage must not discard a draft", 0, views.single().disposals)
            chat.refresh()
            assertEquals(1, views.single().disposals)
            chat.open("a", owner(), anchor())
            assertEquals(1, shown.size)
        } finally {
            chat.dispose()
        }
    }

    @Test
    fun `popup fits small IDE frames at normal and enlarged zoom`() = onEdt {
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                JBUIScale.setUserScaleFactorForTest(scale)
                val shown = mutableListOf<JBPopup>()
                val owner = owner().apply { setSize(700, 480) }
                val chat = PetQuickChatPopup(
                    ProjectManager.getInstance().defaultProject, scope,
                    { PetSession(it, "Title", PetState.WAITING) }, { _, _, _ -> null }, {}, {},
                    showPopup = { popup, point ->
                        shown += popup
                        assertTrue(Rectangle(0, 0, owner.width, owner.height).contains(Rectangle(point.point, popup.content.preferredSize)))
                    },
                )
                try {
                    chat.open("a", owner, Rectangle(670, 420, 30, 40))
                    assertEquals(1, shown.size)
                } finally {
                    chat.dispose()
                }
            }
        } finally {
            JBUIScale.setUserScaleFactorForTest(originalScale)
        }
    }

    private fun chat(
        shown: MutableList<JBPopup>,
        changes: MutableList<String?> = mutableListOf(),
        create: suspend (String, Disposable, CoroutineScope) -> QuickChatView?,
        onOpen: (String) -> Unit = {},
        available: () -> Boolean = { true },
    ) = PetQuickChatPopup(
        ProjectManager.getInstance().defaultProject, scope,
        { id -> if (available()) PetSession(id, "Title", PetState.WAITING) else null },
        create, onOpen, changes::add,
        showPopup = { popup, _ -> shown += popup },
    )

    private fun owner() = object : JPanel() {
        override fun isShowing() = isVisible
        override fun getLocationOnScreen() = Point()
    }.apply { setSize(JBUI.scale(900), JBUI.scale(700)) }

    private fun anchor() = Rectangle(200, 500, 64, 84)
    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
    private fun Container.children(): List<Component> = components.flatMap { listOf(it) + if (it is Container) it.children() else emptyList() }

    private class RecordingView : QuickChatView {
        val draft = JTextArea()
        override val component = JPanel().apply { add(draft) }
        override val preferredFocusComponent: JComponent? = null
        var disposals = 0
        override fun dispose() { disposals++ }
    }
}