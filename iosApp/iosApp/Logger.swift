import Logging

let logger: Logger = {
    var logger = Logger(label: "org.miker.floatsauce")
    logger.logLevel = .debug
    return logger
}()
