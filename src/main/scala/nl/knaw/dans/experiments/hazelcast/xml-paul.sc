import scala.xml.{Node, NodeSeq, Utility}

val emdWithCoverage = Utility.trim(<emd>
  <emd:coverage>
    <dct:spatial>spatial1</dct:spatial>
    <dct:temporal eas:scheme="ABR" eas:schemeId="archaeology.dcterms.temporal">IJZL</dct:temporal>
    <eas:spatial>
      <eas:point eas:scheme="RD">
        <eas:x>155000</eas:x>
        <eas:y>463000</eas:y>
      </eas:point>
    </eas:spatial>
    <eas:spatial>
      <eas:box eas:scheme="RD">
        <eas:north>1</eas:north>
        <eas:east>3</eas:east>
        <eas:south>4</eas:south>
        <eas:west>2</eas:west>
      </eas:box>
    </eas:spatial>
  </emd:coverage>
</emd>)

def extractSpatialForDc(spatial: Node) = {
  (spatial \ "point", spatial \ "box") match {
    case (Seq(), Seq()) => spatial.text
    case (Seq(), box) => "box"
    case (point, Seq()) => "point"
  }
}

val dcCoverageFromEmdMappings = List("coverage").map(s => s"dc_$s" -> (emdWithCoverage \ s \ "_").map(n => n.label match {
  case "spatial" => extractSpatialForDc(n)
  case _ => n.text
}))
