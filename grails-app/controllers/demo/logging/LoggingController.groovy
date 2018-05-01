package demo.logging

import groovy.util.logging.Slf4j

@Slf4j
class LoggingController {

    def index() {

        log.trace 'This is a trace message'
        log.error 'This is an error message'

        render 'Success'
    }
}
