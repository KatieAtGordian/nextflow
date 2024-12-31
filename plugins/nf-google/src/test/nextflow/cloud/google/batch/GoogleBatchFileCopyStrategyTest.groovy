package nextflow.cloud.google.batch

import spock.lang.Specification

class GoogleBatchFileCopyStrategyTest extends Specification {

    def 'should create stage input files script'() {
        given:
        def bean = Mock(TaskBean) {
            getWorkDir() >> mockGsPath('gs://my-bucket/work/xx/yy')
        }
        def handler = Mock(GoogleBatchTaskHandler) {
            getExecutor() >> Mock(GoogleBatchExecutor) {
                getConfig() >> Mock(GoogleBatchConfig)
            }
        }
        def strategy = new GoogleBatchFileCopyStrategy(bean, handler)

        when:
        def inputs = ['foo.txt': mockGsPath('gs://my-bucket/bar/foo.txt')]
        def result = strategy.getStageInputFilesScript(inputs)
        then:
        result == '''\
                echo start | gsutil -q cp  -c - gs://my-bucket/work/xx/yy/.command.begin
                downloads=(true)
                downloads+=("nxf_gs_download gs://my-bucket/bar/foo.txt foo.txt ")
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
                getConfig() >> Mock(GoogleBatchConfig)
            }
        }
        def strategy = new GoogleBatchFileCopyStrategy(bean, handler)

        when:
        def result = strategy.getUnstageOutputFilesScript(['foo.txt'], mockGsPath('gs://my-bucket/output'))
        then:
        result == '''\
                uploads=()
                IFS=$'\\n'
                for name in $(eval "ls -1d foo.txt" | sort | uniq); do
                    uploads+=("nxf_gs_upload 'foo.txt' gs://my-bucket/output")
                done
                unset IFS
                nxf_parallel "${uploads[@]}"
                '''.stripIndent()
    }
}