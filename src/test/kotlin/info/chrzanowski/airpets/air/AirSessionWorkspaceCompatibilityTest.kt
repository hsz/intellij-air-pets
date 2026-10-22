package info.chrzanowski.airpets.air

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore

class AirSessionWorkspaceCompatibilityTest {
    @Test
    fun `session discovery works when AIR SessionWorkspace is an interface`() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
                val internalName = name.replace('.', '/')
                val isolated = name.startsWith(AirSessionSource::class.java.name) ||
                    name.startsWith(AirSessionSourceTest::class.java.name) ||
                    internalName in setOf(WORKSPACE, "$CATALOG/ProjectWorkspace", "$CATALOG/WorktreeWorkspace", "$CATALOG/SessionsSnapshot")
                if (!isolated) return@synchronized super.loadClass(name, resolve)
                val loaded = findLoadedClass(name) ?: run {
                    val bytes = requireNotNull(getResourceAsStream("$internalName.class")).use { it.readBytes() }
                    val compatible = when (internalName) {
                        WORKSPACE -> workspaceInterface(bytes)
                        "$CATALOG/ProjectWorkspace", "$CATALOG/WorktreeWorkspace" -> implementWorkspaceInterface(bytes)
                        else -> bytes
                    }
                    defineClass(name, compatible, 0, compatible.size)
                }
                if (resolve) resolveClass(loaded)
                loaded
            }
        }
        val result = JUnitCore.runClasses(loader.loadClass(AirSessionSourceTest::class.java.name))
        assertTrue(result.failures.joinToString("\n") { it.trace }, result.wasSuccessful())
    }

    private fun workspaceInterface(bytes: ByteArray): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT, WORKSPACE, null, "java/lang/Object", null)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?,
            ): MethodVisitor? {
                if (name != "<init>" && access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) == Opcodes.ACC_PUBLIC) {
                    writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, name, descriptor, signature, exceptions).visitEnd()
                }
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun implementWorkspaceInterface(bytes: ByteArray): ByteArray {
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visit(
                version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>,
            ) {
                super.visit(version, access, name, signature, "java/lang/Object", arrayOf(*interfaces, WORKSPACE))
            }

            override fun visitMethod(
                access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?,
            ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    if (owner == WORKSPACE && name == "<init>") {
                        // Concrete workspaces already store and override every catalog property.
                        Type.getArgumentTypes(descriptor).reversed().forEach {
                            super.visitInsn(if (it.size == 2) Opcodes.POP2 else Opcodes.POP)
                        }
                        super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", name, "()V", false)
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
        }, 0)
        return writer.toByteArray()
    }

    private companion object {
        const val CATALOG = "com/intellij/air/frontend/session/catalog"
        const val WORKSPACE = "$CATALOG/SessionWorkspace"
    }
}