package voyadgerodin.plugin

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import voyadgerodin.plugin.dto.ArtifactDTO
import voyadgerodin.plugin.dto.EdgesDTO
import voyadgerodin.plugin.dto.ProcessDTO
import voyadgerodin.plugin.dto.SessionDTO

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ApiManager {
    private String serverURL = ''
    private HttpClient httpClient

    ApiManager(String serverURL) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        this.serverURL = serverURL
    }

    private static HttpRequest buildHttpRequest(String url, String method, Map body) {
        if (method == 'POST') {
            return HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header('Content-Type', 'application/json')
                    .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)))
                    .build()
        } else if (method == 'PUT') {
            return HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header('Content-Type', 'application/json')
                    .PUT(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)))
                    .build()
        }
        return null
    }

    Integer sendSession(SessionDTO sessionDTO) {
        def body = [sessionData: sessionDTO,]
        def request = buildHttpRequest(
                "${this.serverURL}/api/session",
                'POST',
                body
        )

        try {
            def response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 201) {
                throw new RuntimeException("HTTP error: ${response.statusCode()}")
            }

            def json = new JsonSlurper().parseText(response.body())

            Integer sessionId = Integer.parseInt("${json.id}")

            if (!sessionId) {
                return null
            }

            return sessionId
        } catch (Exception e) {
            return null
        }
    }

    void sendSessionStatus(Integer sessionId, String status) {
        if (sessionId == null) {
            return
        }

        def body = [
                status: status,
        ]
        def request = buildHttpRequest(
                "${this.serverURL}/api/sessions/${sessionId}/status",
                'PUT',
                body
        )

        try {
            this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {}
    }

    void sendProcessesGraph(List<ProcessDTO> processes, EdgesDTO edges) {
        def request = HttpRequest.newBuilder()
                .uri(new URI("${this.serverURL}/api/processes"))
                .header('Content-Type', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson([
                        processes: processes,
                        edges: edges,
                ])))
                .build()

        try {
            this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {}
    }

    void sendProcessStatus(Integer sessionId, int processId, String status, long time, telemetry) {
        def request = HttpRequest.newBuilder()
                .uri(new URI("${this.serverURL}/api/process"))
                .header('Content-Type', 'application/json')
                .PUT(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson([
                        sessionId: sessionId,
                        processId: processId,
                        status: status,
                        time: time,
                        telemetry: telemetry,
                ])))
                .build()

        try {
            this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {}
    }

    void sendArtifact(ArtifactDTO artifact) {
        if (artifact.sessionId == null) {
            return
        }

        def body = [
                artifact: artifact,
        ]
        def request = buildHttpRequest(
                "${this.serverURL}/api/artifacts",
                'POST',
                body
        )

        try {
            this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {}
    }
}
