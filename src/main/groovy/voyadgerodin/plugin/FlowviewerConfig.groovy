package voyadgerodin.plugin

import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('flowViewer')
class FlowviewerConfig implements ConfigScope {
    @Description("Server URL")
    @ConfigOption
    String serverUrl
}
