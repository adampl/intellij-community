// FIR_COMPARISON
// FIR_IDENTICAL

class Test : <caret> {
    fun test() {
    }
}

// EXIST: Any, Nothing, Unit, Int, Number, Array
// EXIST_JAVA_ONLY: Thread