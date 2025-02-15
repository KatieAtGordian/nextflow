/*
 * Copyright 2019, Google Inc
 * Copyright 2018, WuxiNextcode
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

package nextflow.cloud.google.batch

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cloud.google.batch.client.BatchConfig
import nextflow.cloud.google.util.GsBashLib
import nextflow.executor.SimpleFileCopyStrategy
import nextflow.processor.TaskBean
import nextflow.processor.TaskRun
import nextflow.util.Escape
/**
 * Defines the file/script copy strategies for Google Pipelines.
 *
 * @author  Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Ólafur Haukur Flygenring <olafurh@wuxinextcode.com>
 */
@Slf4j
@CompileStatic
class GoogleBatchFileCopyStrategy extends SimpleFileCopyStrategy {

    BatchConfig config
    GoogleBatchTaskHandler handler
    TaskBean task

    GoogleBatchFileCopyStrategy(TaskBean bean, GoogleBatchTaskHandler handler) {
        super(bean)
        this.handler = handler
        this.config = handler.getExecutor().getConfig()
        this.task = bean
    }

    String getBeforeStartScript() {
        GsBashLib.fromBatchConfig(config)
    }

    @Override
    String getStageInputFilesScript(Map<String, Path> inputFiles) {
        final remoteTaskDir = Escape.uriPath(workDir)
        final stagingCommands = []

        for(String target : inputFiles.keySet()) {
            final sourcePath = inputFiles.get(target)

            final cmd = config.maxTransferAttempts > 1
                    ? "downloads+=(\"nxf_cp_retry nxf_gs_download ${Escape.uriPath(sourcePath)} ${Escape.path(target)}\")"
                    : "downloads+=(\"nxf_gs_download ${Escape.uriPath(sourcePath)} ${Escape.path(target)}\")"

            stagingCommands.add(cmd)
        }

        final result = new StringBuilder()
        // touch
        result.append("echo start | gsutil -q cp  -c - ${remoteTaskDir}/${TaskRun.CMD_START}").append('\n')

        // stage files
        if( stagingCommands ) {
            result.append('downloads=(true)\n')
            result.append(stagingCommands.join('\n')).append('\n')
            result.append('nxf_parallel "${downloads[@]}"\n')
        }

        result.toString()
    }

    /**
     * Trim-trailing-slash
     * @return
     */
    private String tts(String loc) {
        while( log && loc.size()>1 && loc.endsWith('/') )
            loc = loc.substring(0,loc.length()-1)
        return loc
    }

    @Override
    String getUnstageOutputFilesScript(List<String> outputFiles, Path targetDir) {
        final remoteTaskDir = Escape.uriPath(workDir)
        
        final patterns = normalizeGlobStarPaths(outputFiles)
        // create a bash script that will copy the out file to the working directory
        log.trace "[GLS] Unstaging file path: $patterns"

        if( !patterns )
            return null

        final escape = new ArrayList(outputFiles.size())
        for( String it : patterns )
            escape.add(tts(Escape.path(it)))

        """\
        uploads=()
        IFS=\$'\\n'
        for name in \$(eval "ls -1d ${escape.join(' ')}" | sort | uniq); do
            uploads+=("nxf_gs_upload '\$name' ${remoteTaskDir}")
        done
        unset IFS
        nxf_parallel "\${uploads[@]}"
        """.stripIndent(true)
    }


    String copyFile(String local, Path target) {
        /*
         * -m = run in parallel
         * -q = quiet mode
         * cp = copy
         * -R = recursive copy
         */
        "gsutil -m -q cp -R ${Escape.path(local)} ${Escape.uriPath(target)}"
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getEnvScript(Map environment, boolean container) {
        if( container )
            throw new IllegalArgumentException("Parameter `container` not supported by ${this.class.simpleName}")

        final localTaskDir = Escape.path(workDir)
        final result = new StringBuilder()
        final copy = environment ? new HashMap<String,String>(environment) : Collections.<String,String>emptyMap()
        // remove any external PATH
        if( copy.containsKey('PATH') )
            copy.remove('PATH')
        // finally render the environment
        final envSnippet = super.getEnvScript(copy,false)
        if( envSnippet )
            result << envSnippet
        return result.toString()
    }

    static String uploadCmd(String source, Path target) {
        "nxf_gs_upload ${Escape.path(source)} ${Escape.uriPath(target)}"
    }
}
