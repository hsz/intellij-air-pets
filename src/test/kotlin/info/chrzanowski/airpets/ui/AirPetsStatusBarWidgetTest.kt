package info.chrzanowski.airpets.ui

import com.intellij.air.shared.core.icons.AirSharedCoreIcons
import com.intellij.mock.MockProject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.impl.status.TextPanel
import info.chrzanowski.airpets.AirPetsService
import info.chrzanowski.airpets.AirPetsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

class AirPetsStatusBarWidgetTest {
    private val disposable = Disposer.newDisposable()
    @Suppress("JetBrainsInternalApiUsage")
    private val project = MockProject(null, disposable)
    private val scope = CoroutineScope(SupervisorJob())
    private val settings = AirPetsSettings()
    private val factory = AirPetsStatusBarWidgetFactory()

    init {
        project.registerService(AirPetsSettings::class.java, settings)
        project.registerService(AirPetsService::class.java, AirPetsService(project, scope))
    }

    @After
    fun tearDown() {
        scope.cancel()
        SwingUtilities.invokeAndWait { Disposer.dispose(disposable) }
    }

    @Test
    fun `native widget remains available with no sessions and when pets are hidden`() {
        SwingUtilities.invokeAndWait {
            val widget = factory.createWidget(project)
            try {
                assertEquals(factory.id, widget.ID())
                assertTrue(factory.isEnabledByDefault)
                assertTrue(factory.isAvailable(project))
                val component = (widget as CustomStatusBarWidget).component as TextPanel.WithIconAndArrows
                assertEquals("Pets · 0", component.text)
                assertSame("Use AIR's theme-aware icon", AirSharedCoreIcons.Air, component.icon)
                assertEquals(project.getService(AirPetsService::class.java).statusTooltip, component.toolTipText)
                assertTrue(requireNotNull(component.toolTipText).contains("0 live sessions"))
                assertTrue("The status-bar component must handle menu clicks", component.mouseListeners.isNotEmpty())

                settings.state.enabled = false
                (widget as AirPetsStatusBarWidget).updatePresentation()
                assertTrue("Hiding pets must not hide their preferences", factory.isAvailable(project))
                assertEquals("Pets · off", component.text)
                assertSame(AirSharedCoreIcons.Air, component.icon)
                assertEquals(project.getService(AirPetsService::class.java).statusTooltip, component.toolTipText)
                assertTrue(requireNotNull(component.toolTipText).contains("pets hidden"))
                assertTrue("The hidden-state widget must still handle menu clicks", component.mouseListeners.isNotEmpty())
            } finally {
                factory.disposeWidget(widget)
            }
        }
    }

    @Test
    fun `widget factory is registered with the matching native status bar id`() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/META-INF/plugin.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val extensions = document.getElementsByTagName("statusBarWidgetFactory")
        assertEquals(1, extensions.length)
        val attributes = extensions.item(0).attributes
        assertEquals(factory.id, attributes.getNamedItem("id").nodeValue)
        assertEquals(factory.javaClass.name, attributes.getNamedItem("implementation").nodeValue)
    }
}