package info.chrzanowski.airpets.ui

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionCost
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigDecimal
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class SessionHoverCardTest {
    private val fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `card shows literal wrapped title workspace and independently optional metrics`() = onEdt {
        val card = card()
        val session = PetSession(
            "id", "<html>A title that should never become markup</html>", PetState.WAITING,
            cost = SessionCost(BigDecimal("1.23"), estimated = true), observedWorkingMs = 83_000,
            workspacePath = "/worktrees/a long workspace directory", worktreeBranch = "feature/hover-card",
        )
        card.update(session, false, true, false, false, JBUI.scale(320))
        assertTrue(card.texts().contains(session.title))
        assertTrue(card.texts().any { it.contains(session.workspacePath!!) && it.contains("feature/hover-card") })
        assertTrue(card.texts().contains("Needs your input"))
        assertFalse(card.texts().any { it.contains("cost", ignoreCase = true) || it.contains("working time") })
        card.update(session, false, true, true, false, JBUI.scale(320))
        assertTrue(card.texts().contains("Estimated cost (USD): ≈$1.23"))
        assertFalse(card.texts().any { it.contains("working time") })
        card.update(session, false, false, false, true, JBUI.scale(320))
        assertFalse(card.texts().contains(session.title))
        assertTrue(card.texts().contains("AIR session"))
        assertTrue(card.texts().contains("Observed working time: 1m 23s"))
        assertFalse(card.texts().any { it.contains("cost", ignoreCase = true) })
        assertTrue(
            card.children().filterIsInstance<JLabel>()
                .single { it.text.startsWith("Observed working time") }.toolTipText.contains("not AIR's historical"),
        )
    }

    @Test
    fun `missing metrics and workspace are honest and only completed states offer done`() = onEdt {
        var opened = 0
        var finished = 0
        val card = SessionHoverCard({ opened++ }, { finished++ }, {}, {})
        for (state in PetState.entries) {
            card.update(PetSession("id", "Task", state), false, true, true, true, JBUI.scale(320))
            assertEquals(state == PetState.DONE, card.buttons().any { it.text == "Mark as done" })
            assertTrue(card.texts().contains("Cost (USD): Not reported by AIR"))
            assertTrue(card.texts().contains("Observed working time: Not yet observed"))
            assertFalse(card.texts().any { it.contains("Workspace:") })
            card.buttons().single { it.text == "Open session" }.doClick(0)
            card.buttons().firstOrNull { it.text == "Mark as done" }?.doClick(0)
        }
        assertEquals(PetState.entries.size, opened)
        assertEquals(1, finished)
        assertFalse(card.isFocusable)
        assertTrue(card.children().filter { it is JButton || it is JTextArea }.none { it.isFocusable })
    }

    @Test
    fun `demo actions and every card child keep pointer transition callbacks`() = onEdt {
        var entered = 0
        var exited = 0
        val card = SessionHoverCard({}, {}, { entered++ }, { exited++ })
        card.update(PetSession("id", "Demo", PetState.DONE), true, true, false, false, JBUI.scale(320))
        assertTrue(card.texts().any { it.contains("Demo (not live data)") })
        assertTrue(card.buttons().any { it.text == "Change demo state" })
        assertFalse(card.buttons().any { it.text == "Open session" })
        card.children().forEach { child ->
            child.dispatchEvent(MouseEvent(child, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false))
            child.dispatchEvent(MouseEvent(child, MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false))
        }
        assertEquals(card.children().size, entered)
        assertEquals(entered, exited)
    }

    @Test
    fun `native popup does not steal focus and supports escape dismissal`() = onEdt {
        val popup = createHoverPopup(card()) {}
        try {
            assertTrue(popup.isCancelKeyEnabled)
            assertFalse(popup.isFocused)
            assertFalse(popup.content.isFocusCycleRoot)
        } finally {
            Disposer.dispose(popup)
        }
    }

    @Test
    fun `very long titles and paths remain fully available in a bounded scrollable card`() = onEdt {
        val title = "Long title ".repeat(100)
        val card = card()
        card.update(PetSession("id", title, PetState.DONE, workspacePath = "/long-workspace".repeat(50)), false, true, true, true, JBUI.scale(320))
        assertEquals(JBUI.scale(320), card.preferredSize.width)
        assertTrue(card.preferredSize.height < JBUI.scale(340))
        assertTrue(card.texts().contains(title))
        val scroll = card.children().filterIsInstance<JScrollPane>().single()
        assertTrue(scroll.viewport.view.preferredSize.height > scroll.preferredSize.height)
        card.setSize(card.preferredSize)
        layout(card)
        val done = card.buttons().single { it.text == "Mark as done" }
        assertTrue("Actions stay outside the scrolling area", done.parent != scroll.viewport.view)
        assertTrue(done.width > 0)
    }

    @Test
    fun `popup refreshes live data and actions use identity with stale state guards`() = onEdt {
        val owner = object : JPanel() {
            override fun isShowing() = true
            override fun getLocationOnScreen() = Point(0, 0)
        }.apply { setSize(JBUI.scale(900), JBUI.scale(600)) }
        var current: HoverCardData? = HoverCardData(
            PetSession("exact-id", "Same title", PetState.DONE, cost = SessionCost(BigDecimal("1.23"))),
            Rectangle(200, 450, 64, 84), false, true, true, true,
        )
        var pending: (() -> Unit)? = null
        var native: JBPopup? = null
        val opened = mutableListOf<String>()
        val finished = mutableListOf<String>()
        val hover = PetHoverPopup(
            owner, { id -> current?.takeIf { it.session.id == id } }, opened::add, finished::add,
            later = { _, action ->
                pending = action
                val cancel: () -> Unit = { pending = null }
                cancel
            },
            showPopup = { popup, _ -> native = popup },
        )

        fun show(): SessionHoverCard {
            hover.hover("exact-id")
            requireNotNull(pending).invoke()
            return requireNotNull(native).content.children().filterIsInstance<SessionHoverCard>().single()
        }
        try {
            var card = show()
            assertTrue(hover.isOpenFor("exact-id"))
            assertTrue(card.texts().contains("Cost (USD): $1.23"))
            current = requireNotNull(current).copy(
                session = requireNotNull(current).session.copy(
                    title = "Updated title", cost = SessionCost(BigDecimal("2.34")), observedWorkingMs = 42_000,
                ),
            )
            hover.refresh()
            assertTrue(card.texts().contains("Updated title"))
            assertTrue(card.texts().contains("Cost (USD): $2.34"))
            assertTrue(card.texts().contains("Observed working time: 42s"))
            val staleDone = card.buttons().single { it.text == "Mark as done" }
            current =
                requireNotNull(current).copy(session = requireNotNull(current).session.copy(state = PetState.WORKING))
            staleDone.doClick(0)
            assertTrue("A stale completed card must not archive running work", finished.isEmpty())
            assertNull(hover.sessionId)
            card = show()
            assertFalse(card.buttons().any { it.text == "Mark as done" })
            card.buttons().single { it.text == "Open session" }.doClick(0)
            assertEquals(listOf("exact-id"), opened)
            current =
                requireNotNull(current).copy(session = requireNotNull(current).session.copy(state = PetState.DONE))
            card = show()
            card.buttons().single { it.text == "Mark as done" }.doClick(0)
            assertEquals(listOf("exact-id"), finished)
            assertEquals("Finishing must not open", listOf("exact-id"), opened)
            card = show()
            val staleOpen = card.buttons().single { it.text == "Open session" }
            current = null
            hover.refresh()
            assertNull(hover.sessionId)
            staleOpen.doClick(0)
            assertEquals("Removed sessions cannot be opened", listOf("exact-id"), opened)
        } finally {
            hover.cancel()
            native?.takeUnless { it.isDisposed }?.let(Disposer::dispose)
        }
    }

    @Test
    fun `card wraps long content and stays beside pet within bounds at both zoom levels`() = onEdt {
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                JBUIScale.setUserScaleFactorForTest(scale)
                val card = card()
                val session = PetSession(
                    "id",
                    "A long session name with enough words to wrap across multiple lines",
                    PetState.DONE,
                    workspacePath = "/worktrees/a-long-path/feature/compact-hover-card",
                    worktreeBranch = "feature/compact-hover-card",
                    cost = SessionCost(BigDecimal("1.23")),
                    observedWorkingMs = 83_000,
                )
                card.update(session, false, true, true, true, JBUI.scale(320))
                assertEquals(JBUI.scale(320), card.preferredSize.width)
                assertTrue(card.preferredSize.height < JBUI.scale(350))
                card.setSize(card.preferredSize)
                layout(card)
                val area = Rectangle(0, 0, JBUI.scale(900), JBUI.scale(600))
                for (anchor in listOf(
                    Rectangle(JBUI.scale(2), JBUI.scale(30), JBUI.scale(64), JBUI.scale(84)),
                    Rectangle(JBUI.scale(820), JBUI.scale(500), JBUI.scale(64), JBUI.scale(84)),
                    Rectangle(JBUI.scale(420), JBUI.scale(260), JBUI.scale(64), JBUI.scale(84)),
                )) {
                    val location = hoverCardLocation(anchor, card.preferredSize, area)
                    assertTrue(area.contains(Rectangle(location, card.preferredSize)))
                    assertFalse(Rectangle(location, card.preferredSize).intersects(anchor))
                }
                val image = BufferedImage(card.width, card.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    card.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                val output = File("build/reports/air-pets/hover-card-${(scale * 100).toInt()}.png")
                output.parentFile.mkdirs()
                ImageIO.write(image, "png", output)
                card.update(
                    session.copy(title = "Short", workspacePath = null, worktreeBranch = null),
                    false,
                    true,
                    false,
                    false,
                    JBUI.scale(320),
                )
                assertTrue("Live updates should shrink wrapped content", card.preferredSize.height < image.height)
            }
        } finally {
            JBUIScale.setUserScaleFactorForTest(originalScale)
        }
    }

    private fun card() = SessionHoverCard({}, {}, {}, {})
    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
    private fun Container.children(): List<Component> =
        components.flatMap { listOf(it) + if (it is Container) it.children() else emptyList() }

    private fun SessionHoverCard.texts() = children().filter { it.isVisible }.mapNotNull {
        when (it) {
            is JLabel -> it.text; is JTextArea -> it.text; else -> null
        }
    }

    private fun SessionHoverCard.buttons() = children().filterIsInstance<JButton>().filter { it.isVisible }
    private fun layout(container: Container) {
        container.doLayout(); container.components.filterIsInstance<Container>().forEach(::layout)
    }
}