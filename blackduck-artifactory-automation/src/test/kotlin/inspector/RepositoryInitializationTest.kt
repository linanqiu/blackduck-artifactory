package inspector

import SpringTest
import com.synopsys.integration.blackduck.artifactory.BlackDuckArtifactoryProperty
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.PackageType
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.artifacts.PropertiesApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.repositories.RepositoryType
import com.synopsys.integration.blackduck.artifactory.automation.plugin.BlackDuckPluginApiService
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.InspectionStatus
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.SupportedPackageType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired

class RepositoryInitializationTest : SpringTest() {
    @Autowired
    lateinit var blackDuckPluginApiService: BlackDuckPluginApiService

    @Autowired
    lateinit var propertiesApiService: PropertiesApiService

    @ParameterizedTest
    @EnumSource(PackageType.Defaults::class)
    fun emptyRepositoryInitialization(packageType: PackageType) {
        val supported = SupportedPackageType.getAsSupportedPackageType(packageType.packageType).isPresent

        val repository = repositoryManager.createRepository(packageType, RepositoryType.REMOTE)
        repositoryManager.addRepositoryToInspection(application.containerHash, repository)

        blackDuckPluginApiService.blackDuckInitializeRepositories()
        val itemProperties = propertiesApiService.getProperties(repository.key)

        val propertyKey = BlackDuckArtifactoryProperty.INSPECTION_STATUS.getName()
        val inspectionStatuses = itemProperties.properties[propertyKey]
        Assertions.assertEquals(1, inspectionStatuses?.size)

        val inspectionStatus = inspectionStatuses!![0]
        if (supported) {
            Assertions.assertEquals(InspectionStatus.SUCCESS.name, inspectionStatus)
        } else {
            Assertions.assertEquals(InspectionStatus.FAILURE.name, inspectionStatus)
        }

        cleanup(repository, supported)
    }
}