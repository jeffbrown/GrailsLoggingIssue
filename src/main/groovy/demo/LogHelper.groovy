package demo

trait LogHelper {

    void log() {
        String simpleName = this.class.simpleName

        log.error ">>> ${simpleName} error message"
        log.warn ">>> ${simpleName} warn message"
        log.info ">>> ${simpleName} info message"
        log.debug ">>> ${simpleName} debug message"
        log.trace ">>> ${simpleName} trace message"
    }

}
