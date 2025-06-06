// "Replace with safe (?.) call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (map[3]!!) call
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// ERROR: Operator call corresponds to a dot-qualified call 'map[3].remAssign(5)' which is not allowed on a nullable receiver 'map[3]'.
// K2_AFTER_ERROR: Operator call is prohibited on a nullable receiver of type 'Int?'. Use '?.'-qualified call instead.
fun test(map: MutableMap<Int, Int>) {
    map[3] %=<caret> 5
}
