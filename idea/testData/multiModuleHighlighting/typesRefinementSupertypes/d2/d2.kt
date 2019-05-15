package foo
import d0.AnotherSupertype

<error descr="[UNSUPPORTED_FEATURE] The feature \"multi platform projects\" is experimental and should be enabled explicitly">actual</error> interface Supertype : AnotherSupertype {
    fun foo() {}
}


