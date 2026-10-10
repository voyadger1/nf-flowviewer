/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package voyadgerodin.plugin

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskProcessor
import nextflow.trace.TraceObserverV2
import nextflow.trace.event.FilePublishEvent
import nextflow.trace.event.TaskEvent
import voyadgerodin.plugin.dto.*

import java.net.http.HttpClient

/**
 * Implements an observer that allows implementing custom
 * logic on nextflow execution events.
 */
@Slf4j
@CompileStatic
class FlowviewerObserver implements TraceObserverV2 {
    private Integer sessionId
    private Integer artifactMaxSize = 512
    private Session session
    private String serverURL = ''
    private String runBy = null
    private String workflowName = ''
    private HttpClient httpClient
    private ApiManager apiManager

    FlowviewerObserver() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
    }

    static String getSessionREADME (String workflowDir, String scriptName) {
        def infoFileName = scriptName.replaceFirst(/\.nf$/, '.md')
        def infoFile = new File("${workflowDir}/${infoFileName}")
        if (infoFile.exists()) {
            return infoFile.readLines().join('\n')
        }

        def readmeFile = new File("${workflowDir}/README.md")
        if (readmeFile.exists()) {
            return readmeFile.readLines().join('\n')
        }

        return null
    }

    /* ------------------ METRICS ------------------ */

    @Override
    boolean enableMetrics() {
        return true
    }

    /* ------------------ FLOW ------------------ */

    @Override
    void onFlowCreate(Session session) {
        this.session = session

        def config = session.config.navigate('flowViewer')
        this.serverURL = config.getAt('serverUrl').toString()

        if (config.getAt('runBy')?.toString()){
            this.runBy = config.getAt('runBy').toString()
        }

        String envRunBy = System.getenv('FLOWVIEWER_RUN_BY')
        if (envRunBy) {
            this.runBy = envRunBy
        }

        if (config.getAt('workflowName')?.toString()){
            this.workflowName = config.getAt('workflowName').toString()
        }

        if (config.getAt('artifactMaxSize')){
            Integer _artifactMaxSize = Integer.parseInt(config.getAt('artifactMaxSize').toString())
            if (_artifactMaxSize) {
                this.artifactMaxSize = _artifactMaxSize
            }
        }

        this.apiManager = new ApiManager(serverURL)

        def sessionInfo = getSessionREADME("${session.getWorkDir()}/..", session.getScriptName())

        SessionDTO sessionDTO = new SessionDTO(
                uniqueId: session.getUniqueId(),
                workflowName: this.workflowName,
                sessionInfo: sessionInfo,
                scriptName: session.getScriptName(),
                runName: session.getRunName(),
                runBy: this.runBy,
                profile: session.getProfile(),
                ansiLog: session.getAnsiLog(),
                baseDir: session.getBaseDir()?.toString(),
                binDir: session.getBinDir()?.toString(),
                bucketDir: session.getBucketDir()?.toString(),
                workDir: session.getWorkDir()?.toString(),
                commandLine: session.getCommandLine(),
                commitId: session.getCommitId(),
                poolSize: session.getPoolSize(),
                resolvedConfig: session.getResolvedConfig(),
        )

        this.sessionId = this.apiManager.sendSession(sessionDTO)
    }

    @Override
    void onFlowBegin() {
        List<ProcessDTO> processes = []
        EdgesDTO edges = new EdgesDTO()

        edges.sessionId = this.sessionId

        Long processIdCurrent = 1

        this.session.dag.vertices.forEach {    {
            Long processId = null
            if ("${it.type}" == 'PROCESS') {
                processId = processIdCurrent
                processIdCurrent += 1
            }

            processes.add(
                    new ProcessDTO(
                            vertexId: it.id,
                            sessionId: this.sessionId,
                            processId: processId,
                            label: it.label,
                            type: it.type,
                            workflow: it.workflow,
                            status: 'CREATED'
                    )
            )
        }}

        this.session.dag.edges.forEach {    {
            edges.edges.add(new EdgeDTO(id: it.id, label: it.label, fromId: it.from?.id, toId: it.to?.id))
        }}

        this.apiManager.sendProcessesGraph(processes, edges)

        this.apiManager.sendSessionStatus(this.sessionId, 'RUNNING')
    }

    @Override
    void onFlowComplete() {
        if (this.session.success) {
            this.apiManager.sendSessionStatus(this.sessionId, 'SUCCEEDED')
        } else {
            this.apiManager.sendSessionStatus(this.sessionId, 'FAILED')
        }
    }

    /* ------------------ PROCESS ------------------ */

    @Override
    void onProcessCreate(TaskProcessor processor) {}

    @Override
    void onProcessTerminate(TaskProcessor process){}

    @Override
    void onTaskStart(TaskEvent event) {
        def startTime = Long.parseLong(event.trace.get('start').toString())

        def processId = event.getHandler().getTask().getProcessor().getId()
        this.apiManager.sendProcessStatus(this.sessionId, processId, 'RUNNING', startTime, null)
    }

    @Override
    void onTaskComplete(TaskEvent event) {
        def handler = event.getHandler()
        def task = handler.getTask()

        def processId = task.getProcessor().getId()
        def exitCode = event.trace.get('exit')
        def completeTime = Long.parseLong(event.trace.get('complete').toString())
        def telemetry = null

        def trace = event.trace
        if (trace) {
            telemetry = [
                    hash        : trace.store.get('hash'),
                    realtime    : trace.store.get('realtime'),
                    startTime   : trace.store.get('start'),
                    completeTime: trace.store.get('complete'),
                    cpuPercent  : trace.store.get('%cpu'),
                    cpus        : trace.store.get('cpus'),
                    cpu_model   : trace.store.get('cpu_model'),
                    mem         : trace.store.get('%mem'),
                    rss         : trace.store.get('rss'),
                    vmem        : trace.store.get('vmem'),
                    peakRss     : trace.store.get('peak_rss'),
                    peakVmem    : trace.store.get('peak_vmem'),
                    memory      : trace.store.get('memory'),
                    rchar       : trace.store.get('rchar'),
                    wchar       : trace.store.get('wchar'),
                    readBytes   : trace.store.get('read_bytes'),
                    writeBytes  : trace.store.get('write_bytes'),
                    container   : trace.store.get('container'),
                    disk        : trace.store.get('disk'),
                    module      : JsonOutput.toJson(trace.store.get('module')),
                    exitStatus  : trace.store.get('exit'),
                    hostname    : trace.store.get('hostname'),
                    inv_ctxt    : trace.store.get('inv_ctxt'),
                    vol_ctxt    : trace.store.get('vol_ctxt'),
                    error_action: trace.store.get('error_action'),
                    queue       : trace.store.get('queue'),
                    scratch     : trace.store.get('scratch'),
            ]
        }

        if (exitCode == 0) {
            this.apiManager.sendProcessStatus(this.sessionId, processId, 'SUCCEEDED', completeTime, telemetry)
        } else {
            this.apiManager.sendProcessStatus(this.sessionId, processId, 'FAILED', completeTime, telemetry)
        }
    }

    @Override
    void onTaskCached(TaskEvent event) {
        def processId = event.getHandler().getTask().getProcessor().getId()
        def completeTime = Long.parseLong(event.trace.get('complete').toString())
        this.apiManager.sendProcessStatus(this.sessionId, processId, 'CACHED', completeTime, null)
    }

    @Override
    void onFilePublish(FilePublishEvent event) {
        def sourcePath = event.source.toString()
        def matcher = sourcePath =~ /work\/([a-f0-9]{2})\/([a-f0-9]{30,})/
        def hash = ''
        if (matcher.find()) {
            hash = matcher.group(1) + '/' + matcher.group(2)
        }

        String data = event.source.withInputStream { stream ->
            byte[] bytes = stream.readNBytes(this.artifactMaxSize)
            new String(bytes, 'UTF-8')
        }

        ArtifactDTO artifact = new ArtifactDTO(
                sessionId: this.sessionId,
                name: event.source.getName(),
                uri: event.source.getUri(),
                size: event.source.size(),
                permissions: event.source.getPermissions(),
                fileSystem: event.source.getFileSystem(),
                data: data,
                hash: hash
        )

        this.apiManager.sendArtifact(artifact)
    }
}
