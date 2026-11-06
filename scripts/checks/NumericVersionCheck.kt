/** Compile together with the production getVersionCode function. */
fun main() {
    check(getVersionCode("v0.1.1") == 10100)
    check(getVersionCode("0.1.1") > 10006)
    check(getVersionCode("v0.1.1.1") > getVersionCode("v0.1.1"))
    check(getVersionCode("v0.1.2") > getVersionCode("v0.1.1.99"))
    check(getVersionCode("v1.0.0") > getVersionCode("v0.99.99.99"))
    for (bad in listOf("v0.1", "v0.1.1.1.1", "v0.1.1-rc1", "v0.1.1-test.6", "v0.100.1", "v0.-1.1")) {
        check(runCatching { getVersionCode(bad) }.isFailure) { bad }
    }
    println("11 numeric version checks passed")
}
