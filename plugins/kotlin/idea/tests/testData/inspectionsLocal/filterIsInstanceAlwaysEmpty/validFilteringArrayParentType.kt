// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

open class A

class B: A()

fun foo() {
    val array = arrayOf(B())
    val filteredArray = array.filt<caret>erIsInstance<A>()
}