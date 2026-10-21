package info.chrzanowski.airpets

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

class PetOptions : BaseState() {
    var enabled by property(true)
    var labels by property(true)
    var animated by property(true)
    var cost by property(false)
    var duration by property(false)
}

@Service(Service.Level.PROJECT)
@State(name = "AirPets", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AirPetsSettings : SimplePersistentStateComponent<PetOptions>(PetOptions())