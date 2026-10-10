package voyadgerodin.plugin.dto

import nextflow.dag.DAG

class ProcessDTO {
    Long vertexId
    Integer sessionId
    Long processId
    String label
    DAG.Type type
    String workflow
    String status
}
