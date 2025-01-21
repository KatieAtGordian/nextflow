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

package nextflow.cloud.googleGordian.batch

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cloud.googleGordian.batch.client.BatchConfig
import nextflow.cloud.googleGordian.util.GsBashLib
import nextflow.executor.SimpleFileCopyStrategy
import nextflow.processor.TaskBean
import nextflow.processor.TaskRun
import nextflow.util.Escape


import nextflow.cloud.googleGordian.GoogleSpecification

class GoogleBatchFileCopyStrategyTest extends GoogleSpecification {

    def 'should create stage input files script'() {
        given:
        def bean = Mock(TaskBean) {
            getWorkDir() >> mockGsPath('gs://my-bucket/work/xx/yy')
        }
        def handler = Mock(GoogleBatchTaskHandler) {
            getExecutor() >> Mock(GoogleBatchExecutor) {
                getConfig() >> Mock(BatchConfig)
            }
        }
        def strategy = new GoogleBatchFileCopyStrategy(bean, handler) // null pointer exception

        // file with the same name
        when:
        def inputs = ['foo.txt': mockGsPath('gs://my-bucket/bar/foo.txt')]
        def result = strategy.getStageInputFilesScript(inputs)
        then:
        result == '''\
                echo start | gsutil -q cp  -c - gs://my-bucket/work/xx/yy/.command.begin
                downloads=(true)
                downloads+=("nxf_gs_download gs://my-bucket/bar/foo.txt foo.txt")
                nxf_parallel "${downloads[@]}"
                '''.stripIndent()

        // file with a different name
        when:
        inputs = ['hola.txt': mockGsPath('gs://my-bucket/bar/foo.txt')]
        result = strategy.getStageInputFilesScript(inputs)
        then:
        result == '''\
                echo start | gsutil -q cp  -c - gs://my-bucket/work/xx/yy/.command.begin
                downloads=(true)
                downloads+=("nxf_gs_download gs://my-bucket/bar/foo.txt hola.txt")
                nxf_parallel "${downloads[@]}"
                '''.stripIndent()

        // file name with blanks
        when:
        inputs = ['f o o.txt': mockGsPath('gs://my-bucket/bar/f o o.txt')]
        result = strategy.getStageInputFilesScript(inputs)
        then:
        result == '''\
                echo start | gsutil -q cp  -c - gs://my-bucket/work/xx/yy/.command.begin
                downloads=(true)
                downloads+=("nxf_gs_download gs://my-bucket/bar/f\\ o\\ o.txt f\\ o\\ o.txt")
                nxf_parallel "${downloads[@]}"
                '''.stripIndent()

        // file in a sub-directory
        when:
        inputs = ['subdir/foo.txt': mockGsPath('gs://my-bucket/bar/foo.txt')]
        result = strategy.getStageInputFilesScript(inputs)
        then:
        result == '''\
                echo start | gsutil -q cp  -c - gs://my-bucket/work/xx/yy/.command.begin
                downloads=(true)
                downloads+=("nxf_gs_download gs://my-bucket/bar/foo.txt subdir/foo.txt")
                nxf_parallel "${downloads[@]}"
                '''.stripIndent()
    }

    def 'should create unstage output files script'() {
        given:
        def bean = Mock(TaskBean) {
            getWorkDir() >> mockGsPath('gs://my-bucket/work/xx/yy')
        }
        def handler = Mock(GoogleBatchTaskHandler) {
            getExecutor() >> Mock(GoogleBatchExecutor) {
                getConfig() >> Mock(BatchConfig)
            }
        }
        def strategy = new GoogleBatchFileCopyStrategy(bean, handler)

        when:
        def result = strategy.getUnstageOutputFilesScript(['foo.txt'], mockGsPath('gs://my-bucket/output'))
        println("Generated script:\n$result")
        then:
        result == 
                '''uploads=()
                IFS=$'\n'
                for name in $(eval "ls -1d foo.txt" | sort | uniq); do
                    uploads+=("nxf_gs_upload '$name' gs://my-bucket/output")
                done
                unset IFS
                nxf_parallel "${uploads[@]}"
                '''.stripIndent()
    }

    def 'should set task bean'() {
        given:
        def bean = Mock(TaskBean)
        def handler = Mock(GoogleBatchTaskHandler) {
            getExecutor() >> Mock(GoogleBatchExecutor) {
                getConfig() >> Mock(BatchConfig)
            }
        }

        when:
        def strategy = new GoogleBatchFileCopyStrategy(bean, handler)

        then:
        strategy.task == bean
    }
}


