// CHOSEN_OPTION: Add use-site target 'all'
// COMPILER_ARGUMENTS: -Xannotation-target-all

annotation class A

class Property {
    @A<caret>
    val foo: String
        get() = ""
}