package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.SessionId
import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.shared.core.identity.LocalSessionId
import com.intellij.air.shared.core.identity.LocalSessionIdentity
import com.intellij.air.shared.core.identity.SessionWorkspaceId
import com.intellij.air.shared.core.thread.AgentId
import com.intellij.air.shared.core.thread.AgentLaunchTargetId
import com.intellij.air.shared.core.thread.AgentThreadInteractionSurfaces
import com.intellij.air.shared.core.thread.terminalAgentLaunchTargetId
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore

class AirQuickChatCompatibilityTest {
    @Test
    fun `quick chat supports the IJ 271 summary getter and identity based context`() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
                val internalName = name.replace('.', '/')
                val isolated = name.startsWith(AirQuickChatCompatibilityCases::class.java.name) ||
                    name.startsWith("info.chrzanowski.airpets.air.AirQuickChatKt") ||
                    name == AirQuickChatTarget::class.java.name || internalName == SUMMARY || internalName == CONTEXT
                if (!isolated) return@synchronized super.loadClass(name, resolve)
                val loaded = findLoadedClass(name) ?: run {
                    val bytes = when (internalName) {
                        CONTEXT -> identityContext()
                        else -> requireNotNull(getResourceAsStream("$internalName.class")).use { it.readBytes() }.let {
                            if (internalName == SUMMARY) modernSummary(it) else it
                        }
                    }
                    defineClass(name, bytes, 0, bytes.size)
                }
                if (resolve) resolveClass(loaded)
                loaded
            }
        }
        val result = JUnitCore.runClasses(loader.loadClass(AirQuickChatCompatibilityCases::class.java.name))
        assertTrue(result.failures.joinToString("\n") { it.trace }, result.wasSuccessful())
    }

    private fun modernSummary(bytes: ByteArray): ByteArray {
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?,
            ): MethodVisitor? {
                if (name == "getInteractionSurfaceId") return null
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }, 0)
        writer.visitMethod(Opcodes.ACC_PUBLIC, "getEffectiveInteractionSurfaceId-Q0pYR-A", "()Ljava/lang/String;", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitFieldInsn(Opcodes.GETFIELD, SUMMARY, "interactionSurfaceId", "Ljava/lang/String;")
            visitVarInsn(Opcodes.ALOAD, 0)
            visitFieldInsn(Opcodes.GETFIELD, SUMMARY, "id", "Lcom/intellij/air/frontend/session/catalog/SessionId;")
            visitMethodInsn(
                Opcodes.INVOKESTATIC, "info/chrzanowski/airpets/air/AirQuickChatCompatibilityTestKt", "compatibilityEffectiveSurfaceId",
                "(Ljava/lang/String;Lcom/intellij/air/frontend/session/catalog/SessionId;)Ljava/lang/String;", false,
            )
            visitInsn(Opcodes.ARETURN)
            visitMaxs(2, 1)
            visitEnd()
        }
        return writer.toByteArray()
    }

    private fun identityContext(): ByteArray {
        val writer = ClassWriter(0)
        val identity = "Lcom/intellij/air/thread/view/AgentThreadViewContentIdentity;"
        val profile = "Lcom/intellij/air/shared/prompt/AgentPromptLaunchProfile;"
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, CONTEXT, null, "java/lang/Object", null)
        listOf("identity" to identity, "launchProfile" to profile, "threadTitle" to "Ljava/lang/String;").forEach { (name, type) ->
            writer.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, name, type, null, null).visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "($identity${profile}Ljava/lang/String;)V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            listOf("identity" to identity, "launchProfile" to profile, "threadTitle" to "Ljava/lang/String;").forEachIndexed { index, (name, type) ->
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, index + 1)
                visitFieldInsn(Opcodes.PUTFIELD, CONTEXT, name, type)
            }
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 4)
            visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "getIdentity", "()$identity", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitFieldInsn(Opcodes.GETFIELD, CONTEXT, "identity", identity)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private companion object {
        const val SUMMARY = "com/intellij/air/frontend/session/catalog/SessionSummary"
        const val CONTEXT = "com/intellij/air/thread/view/AgentThreadViewContentContext"
    }
}

class AirQuickChatCompatibilityCases {
    @Test
    fun `native surfaces retain exact session routing`() {
        listOf(null, AgentThreadInteractionSurfaces.NATIVE.value).forEach { surface ->
            val target = requireNotNull(nativeChatTarget(summary(surface)))
            val identity = target.context.identity
            assertEquals(AgentThreadInteractionSurfaces.NATIVE, identity.interactionSurfaceId)
            assertEquals("junie:runtime-session", identity.threadIdentity)
            assertEquals("runtime-session", identity.threadId)
            assertEquals("/worktree", identity.projectDirectory)
            assertEquals("/worktree", identity.projectPath)
            assertEquals(SessionWorkspaceId("/worktree"), identity.sessionWorkspaceId)
            assertEquals(AgentLaunchTargetId.from("local"), identity.launchTargetId)
            assertEquals(AgentId.from("junie"), identity.agentId)
            assertEquals("Existing session", target.context.threadTitle)
            assertNull(target.context.launchProfile)
            assertEquals(identity.threadIdentity, target.address.sessionIdentity)
        }
    }

    @Test
    fun `unspecified nonterminal surfaces use the native renderer`() {
        val target = nativeChatTarget(summary(null))
        assertTrue("Quick chat is unavailable for this session. Open it in Air to continue.", target != null)
        assertEquals(AgentThreadInteractionSurfaces.NATIVE, requireNotNull(target).context.identity.interactionSurfaceId)
        assertEquals("runtime-session", target.context.identity.threadId)
        assertEquals("junie:runtime-session", target.address.sessionIdentity)
    }

    @Test
    fun `nonnative and invalid surfaces cannot select a native renderer`() {
        listOf("terminal", "", "unknown-surface").forEach { surface ->
            assertNull(nativeChatTarget(summary(surface)))
        }
    }

    @Test
    fun `unspecified terminal surfaces cannot select a native renderer`() {
        val terminal = summary(null, terminalAgentLaunchTargetId(AgentId.from("junie")))
        assertNull(nativeChatTarget(terminal))
    }

    private fun summary(surface: String?, launchTargetId: AgentLaunchTargetId = AgentLaunchTargetId.from("local")) = SessionSummary(
        id = SessionId(SessionWorkspaceId("/worktree"), AgentId.from("junie"), launchTargetId, "runtime-session"),
        title = "Existing session",
        updatedAtMs = 1,
        archived = false,
        interactionSurfaceId = surface,
        localSessionIdentity = LocalSessionIdentity("/worktree", LocalSessionId("local-session")),
        agentSessionId = "external-session",
    )
}

internal fun compatibilityEffectiveSurfaceId(surface: String?, id: SessionId): String? = surface
    ?: AgentThreadInteractionSurfaces.TERMINAL.value.takeIf { id.launchTargetId == terminalAgentLaunchTargetId(id.agentId) }