// Used by the vuln workflow to write gradle.lockfile files for osv-scanner. The lockfiles are not committed.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
