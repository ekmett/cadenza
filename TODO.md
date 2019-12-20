* Replace the use of IndirectCallNode with a custom tree-shaped polymorphic inline cache

  Notions of equality of closures:

  * `arity equality (arity) == too weak`
  * `callTarget equality (callTarget + pap arity ?)`
  * `closure equality == too specific`

* Trampolining forms

* Optimized frames

* Eventual laziness or CBPV for GHC-core style execution

* Chrome debugger support

* AppLam -> direct call
