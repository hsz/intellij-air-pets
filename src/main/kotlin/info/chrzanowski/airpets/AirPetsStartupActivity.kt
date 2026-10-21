package info.chrzanowski.airpets

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AirPetsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<AirPetsService>().start()
    }
}