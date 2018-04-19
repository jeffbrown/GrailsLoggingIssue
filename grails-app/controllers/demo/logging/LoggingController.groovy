package demo.logging

import demo.LogHelper
import groovy.util.logging.Slf4j

// TODO: Comment out the following after uncommenting the grails-logging plugin in build.gradle
@Slf4j
class LoggingController implements LogHelper {

    static responseFormats = ['json']

    def index() {
        log()

        new AnotherLogEmitter().doSomeLogging()

        respond([message: 'done'])
    }
}
