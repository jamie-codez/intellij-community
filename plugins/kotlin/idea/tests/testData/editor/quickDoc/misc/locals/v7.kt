fun variableContainerA() {
    val v1: String
    var v2: AuxFaceA
    @AuxAnnA val v3: AuxClassA
    var (v4, v5) = AuxDataClassA(0, "")
    val v6 = 1
    var <caret>v7 = object : AuxFaceA {}
    var v8: AuxFaceA = object : AuxFaceA {}
    lateinit var v9: AuxClassB
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">local</span> <span style="color:#000080;font-weight:bold;">var</span> <span style="color:#000000;">v7</span><span style="">: </span>&lt;anonymous object : <span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span>&gt;</pre></div>
