package voyadgerodin.plugin.dto

class EdgeDTO {
    long id
    String label
    Long fromId
    Long toId
}

class EdgesDTO {
    Integer sessionId
    List<EdgeDTO> edges

    EdgesDTO() {
        this.edges = []
    }
}
