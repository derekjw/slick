package slick.ast

import TypeUtil.typeToTypeUtil
import Util._
import slick.util.ConstArray

/** A SQL comprehension */
final case class Comprehension(sym: TermSymbol, from: Node, select: Node, where: Option[Node] = None,
                               groupBy: Option[Node] = None, orderBy: ConstArray[(Node, Ordering)] = ConstArray.empty,
                               having: Option[Node] = None,
                               fetch: Option[Node] = None, offset: Option[Node] = None) extends DefNode {
  type Self = Comprehension
  lazy val children = (ConstArray.newBuilder() + from + select ++ where ++ groupBy ++ orderBy.map(_._1) ++ having ++ fetch ++ offset).result
  override def childNames =
    Seq("from "+sym, "select") ++
    where.map(_ => "where") ++
    groupBy.map(_ => "groupBy") ++
    orderBy.map("orderBy " + _._2).toSeq ++
    having.map(_ => "having") ++
    fetch.map(_ => "fetch") ++
    offset.map(_ => "offset")
  protected[this] def rebuild(ch: ConstArray[Node]) = {
    val newFrom = ch(0)
    val newSelect = ch(1)
    val whereOffset = 2
    val newWhere = ch.slice(whereOffset, whereOffset + where.productArity)
    val groupByOffset = whereOffset + newWhere.length
    val newGroupBy = ch.slice(groupByOffset, groupByOffset + groupBy.productArity)
    val orderByOffset = groupByOffset + newGroupBy.length
    val newOrderBy = ch.slice(orderByOffset, orderByOffset + orderBy.length)
    val havingOffset = orderByOffset + newOrderBy.length
    val newHaving = ch.slice(havingOffset, havingOffset + having.productArity)
    val fetchOffset = havingOffset + newHaving.length
    val newFetch = ch.slice(fetchOffset, fetchOffset + fetch.productArity)
    val offsetOffset = fetchOffset + newFetch.length
    val newOffset = ch.slice(offsetOffset, offsetOffset + offset.productArity)
    copy(
      from = newFrom,
      select = newSelect,
      where = newWhere.headOption,
      groupBy = newGroupBy.headOption,
      orderBy = orderBy.zip(newOrderBy).map { case ((_, o), n) => (n, o) },
      having = newHaving.headOption,
      fetch = newFetch.headOption,
      offset = newOffset.headOption
    )
  }
  def generators = ConstArray((sym, from))
  override def getDumpInfo = super.getDumpInfo.copy(mainInfo = "")
  protected[this] def rebuildWithSymbols(gen: ConstArray[TermSymbol]) = copy(sym = gen.head)
  def withInferredType(scope: SymbolScope, typeChildren: Boolean): Self = {
    // Assign type to "from" Node and compute the resulting scope
    val f2 = from.infer(typeChildren)(scope)
    val genScope = scope + (sym -> f2.nodeType.asCollectionType.elementType)
    // Assign types to "select", "where", "groupBy", "orderBy", "having", "fetch" and "offset" Nodes
    val s2 = select.infer(typeChildren)(genScope)
    val w2 = mapOrNone(where)(_.infer(typeChildren)(genScope))
    val g2 = mapOrNone(groupBy)(_.infer(typeChildren)(genScope))
    val o = orderBy.map(_._1)
    val o2 = o.endoMap(_.infer(typeChildren)(genScope))
    val h2 = mapOrNone(having)(_.infer(typeChildren)(genScope))
    val fetch2 = mapOrNone(fetch)(_.infer(typeChildren)(genScope))
    val offset2 = mapOrNone(offset)(_.infer(typeChildren)(genScope))
    // Check if the nodes changed
    val same = (f2 eq from) && (s2 eq select) && w2.isEmpty && g2.isEmpty && (o2 eq o) && h2.isEmpty && fetch2.isEmpty && offset2.isEmpty
    val newType =
      if(!hasType) CollectionType(f2.nodeType.asCollectionType.cons, s2.nodeType.asCollectionType.elementType)
      else nodeType
    if(same && newType == nodeType) this else {
      copy(
        from = f2,
        select = s2,
        where = w2.orElse(where),
        groupBy = g2.orElse(groupBy),
        orderBy = if(o2 eq o) orderBy else orderBy.zip(o2).map { case ((_, o), n) => (n, o) },
        having = h2.orElse(having),
        fetch = fetch2.orElse(fetch),
        offset = offset2.orElse(offset)
      ) :@ newType
    }
  }
}

/** The row_number window function */
final case class RowNumber(by: ConstArray[(Node, Ordering)] = ConstArray.empty) extends SimplyTypedNode {
  type Self = RowNumber
  def buildType = ScalaBaseType.longType
  lazy val children = by.map(_._1)
  protected[this] def rebuild(ch: ConstArray[Node]) =
    copy(by = by.zip(ch).map{ case ((_, o), n) => (n, o) })
  override def childNames = by.zipWithIndex.map("by" + _._2).toSeq
  override def getDumpInfo = super.getDumpInfo.copy(mainInfo = "")
}
