// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c: Int)
fun <caret>String.foo(param: String) {
}

context(c1: Int)
fun bar() {
    "baz".foo("boo")
}
