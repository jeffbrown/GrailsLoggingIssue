package grailsloggingissue

import demo.LogHelper
import groovy.util.logging.Slf4j

// TODO: Comment out the following after uncommenting the grails-logging plugin in build.gradle
@Slf4j
class BootStrap implements LogHelper {

    def init = { servletContext ->
        log()
    }
    def destroy = {
    }
}
