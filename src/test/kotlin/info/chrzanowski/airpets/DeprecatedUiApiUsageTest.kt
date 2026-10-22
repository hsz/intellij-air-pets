package info.chrzanowski.airpets

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.Assert.assertTrue
import org.junit.Test

class DeprecatedUiApiUsageTest {
    @Test
    fun `custom status bar widget does not declare legacy presentations or bridges`() {
        val presentations = mutableListOf<String>()
        reader("ui/AirPetsStatusBarWidget").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?,
            ): MethodVisitor? {
                if (name == "getPresentation") presentations += descriptor
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertTrue(presentations.toString(), presentations.isEmpty())
    }

    @Test
    fun `quick chat does not call deprecated disposal or focus methods`() {
        val calls = mutableListOf<String>()
        for (type in listOf("air/AirQuickChatKt\$createAirQuickChat\$host\$1", "ui/PetQuickChatPopup")) {
            reader(type).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?,
                ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        if (owner == "com/intellij/openapi/util/Disposer" && name == "isDisposed" ||
                            owner == "com/intellij/openapi/wm/IdeFocusManager" && name == "doWhenFocusSettlesDown") {
                            calls += "$owner.$name$descriptor"
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        assertTrue(calls.toString(), calls.isEmpty())
    }

    private fun reader(type: String): ClassReader = requireNotNull(
        javaClass.getResourceAsStream("/info/chrzanowski/airpets/$type.class"),
    ).use { ClassReader(it) }
}