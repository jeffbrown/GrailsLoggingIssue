package demo.logging

import demo.LogHelper
import groovy.util.logging.Slf4j

@Slf4j
class AnotherLogEmitter implements LogHelper {
    void doSomeLogging() {
        log()
    }
}
