package com.rethinkscala.ast

import com.rethinkscala._
import scala.util.matching.Regex
import com.rethinkscala.Implicits._
import com.rethinkscala.net._
import scala.Some
import com.rethinkscala.net.BlockingQuery

trait Produce[ResultType] extends Term {

  type resultType = ResultType


  def toQuery[R](implicit c: Connection, tt: Manifest[R]): Query[R] = new BlockingQuery[R](this, c, tt)

  //http://stackoverflow.com/a/3461734
  def run(implicit c: Connection, mf: Manifest[ResultType]): Either[RethinkError, ResultType] = toQuery.toResult

  def as[R <: ResultType](implicit c: Connection, tt: Manifest[R]): Either[RethinkError, R] = toQuery.toResult


  def toOpt(implicit c: Connection, mf: Manifest[ResultType]) = run fold(x => None, Some(_))

  def asOpt[R <: ResultType](implicit c: Connection, tt: Manifest[R], d: DummyImplicit) = as[R] fold(x => None, Some(_))

}

sealed trait DataType {

  def name: String
}

case object ObjectData extends DataType {
  def name = "object"
}

case object StringData extends DataType {
  def name = "string"
}

case object ArrayData extends DataType {
  def name = "array"
}

sealed trait Typed {

  implicit def toPredicate1(f: (Var) => Typed) = new Predicate1(f)

  implicit def toBooleanPredicate1(f: (Var) => Binary) = new BooleanPredicate1(f)
  implicit def toBooleanPredicate2(f: (Var,Var) => Binary) = new BooleanPredicate2(f)

  def info = Info(this)

  def typeOf = TypeOf(this)

  def coerceTo(dataType: DataType) = CoerceTo(this, dataType)
}

trait JoinTyped[L,R] extends Typed{
  def zip = Zip(this)
}

trait TableTyped extends Typed

trait Addition extends Typed {
  def +(other: Addition) = add(other)


  def add(other: Addition) = Add(this, other)

  def +=(other: Addition) = Add(this, other)
}

trait Literal extends Addition {
  def unary_~ = not

  def not = Not(this)

  def ===(other: Literal) = eq(other)

  def eq(other: Literal) = Eq(this, other)

  def !=(other: Literal) = ne(other)

  def ne(other: Literal) = Ne(this, other)

  def <(other: Literal) = lt(other)

  def lt(other: Literal) = Lt(this, other)

  def <=(other: Literal) = lte(other)

  def lte(other: Literal) = Le(this, other)

  def >(other: Literal) = gt(other)

  def gt(other: Literal) = Gt(this, other)

  def >=(other: Literal) = gte(other)

  def gte(other: Literal) = Ge(this, other)

}

trait MapTyped extends Typed

trait Array extends Typed

trait ArrayTyped[T] extends Sequence[T] with Array {


  def append(value: Datum) = Append(this, value)

  def :+(value: Datum) = append(value)

  def prepend(value: Datum) = Prepend(this, value)

  def +:(value: Datum) = prepend(value)

  def diff(values: Datum*) = Difference(this, Expr(values))

  def diff(array: ArrayTyped[_]) = Difference(this, array)

 // def idiff[R](array: ArrayTyped[R]) = Difference(array, this)

  //def idiff(values: Datum*) = Difference(Expr(values), this)


  def setInert(value: Datum) = SetInsert(this, value)

  def setUnion(values: Datum*) = SetUnion(this, values)

  def setIntersection(values: Datum*) = SetIntersection(this, values)


  def setDifference(values: Datum*) = SetDifference(this, values)

  def insertAt(index: Int, value: Datum) = InsertAt(this, index, value)

  def spliceAt(index: Int, values: Datum*) = SpliceAt(this, index, values)

  def deleteAt(start: Int, end: Option[Int] = None) = DeleteAt(this, start, end)

  def changeAt(index: Int, value: Datum) = ChangeAt(this, index, value)

}

trait Stream[T] extends Sequence[T]

trait Selection[T] extends Sequence[T] {

  def update(attributes: Map[String, Any], options:UpdateOptions) = Update(this, Left(attributes), options)

  def update(attributes: Map[String, Any]): Update[T] = update(attributes,UpdateOptions())

  def update(p: Var => Typed, options:UpdateOptions) = Update(this, Right(p),options)

  def update(d: Document): Update[T] = update((x: Var) => MakeObj2(d))

  def update(t: Typed, options:UpdateOptions): Update[T] = Update(this, Left(t),options)

  def update(t: Typed): Update[T] = update(t, UpdateOptions())

  def update(p: Var => Typed): Update[T] = update(p, UpdateOptions())

  def replace(p: Var => Typed): Replace[T] = Replace(this, Right(p),UpdateOptions())

  def replace(d: Document): Replace[T] = replace((x: Var) => MakeObj2(d))

  def replace(data: Map[String, Any]): Replace[T] = Replace(this, Left(data),UpdateOptions())

  def delete: Delete[T] = delete()

  def delete(durability: Option[Durability.Kind] = None): Delete[T] = Delete(this)

}

trait StreamSelection[T] extends Selection[T] with Stream[T] {

  def between(start: Int, stop: Int) = Between(this, start, stop)

  def between(start: String, stop: String) = Between(this, start, stop)


}

trait SingleSelection[T] extends Selection[T]

trait Multiply extends Typed {

  def *(other: Numeric) = mul(other)

  def mul(other: Numeric) = Mul(this, other)
}

trait Sequence[T] extends Multiply with Filterable[T] with Record {





  // def field(name: String)(implicit d:DummyImplicit) = GetField(this, name)

  //def \(name: String)(implicit d:DummyImplicit) = field(name)

  //def coerceTo(dataType: DataType)=CoerceTo(this,dataType)

  //def field(name: String) = GetField(this, name)

  //def \(name: String) = field(name)


  def indexesOf(value: Datum) = IndexesOf(this, Left(value))

  //def indexesOf(value: Binary): IndexesOf = indexesOf((x: Var) => value)

  def isEmpty = IsEmpty(this)

  def sample(amount: Int) = Sample(this, amount)

  def indexesOf(p: Var => Binary) = IndexesOf(this, Right(p))

  def apply(index: Int) = Nth(this, index)

  def skip(amount: Int) = Skip(this, amount)

  def slice(start: Int = 0, end: Int = -1) = Slice(this, start, end)

  def apply(prange: SliceRange) = Slice(this, prange.start, prange.end)

  def union(sequence: Sequence[_]) = Union(this, sequence)

  def ++(sequence: Sequence[_]) = union(sequence)

  def eqJoin[R](attr: String, other: Sequence[R], index: Option[String] = None) = EqJoin(this, attr, other, index)

  def innerJoin[R](other: Sequence[R], func: (Var,Var)=>Binary) = InnerJoin(this, other, func)

  def outerJoin[R](other: Sequence[R], func: (Var,Var)=>Binary) = OuterJoin(this, other, func)

  def map(func: Var => Typed) = RMap(this, func)

  def concatMap(func: Var => Typed) = ConcatMap(this, func)

  def order(keys: Ordering*) = OrderBy(this, keys)

  def withFields(keys: String*) = WithFields(this, keys)

  def size = count

  def count = Count(this)

  def count(value: String) = Count(this, Some(Left(value)))

  def count(filter: Var => Binary) = Count(this, Some(Right(filter)))

  def count(value: Binary) = Count(this, Some(Right((x: Var) => value)))

  def mapReduce(grouping: Predicate1, mapping: Predicate1,
                reduction: Predicate2, base: Option[Datum] = None) = GroupMapReduce(this, grouping, mapping, reduction, base)

  def groupBy(method: AggregateByMethod, attrs: String*) = GroupBy(this, method, attrs)

  def contains(attrs: Datum*) = Contains(this, attrs)

  def ?(attr: Datum) = contains(attr)

  // add dummy implicit to allow methods for Ref
  def pluck(attrs: String*)(implicit d: DummyImplicit) = Pluck(this, attrs)

  def without(attrs: String*)(implicit d: DummyImplicit) = Without(this, attrs)

  def pluck(m: Map[String, Any])(implicit d: DummyImplicit) = Pluck(this, m)

  def merge(other: Sequence[_]) = Merge(this, other)

  def +(other: Sequence[_]) = merge(other)

  def foreach(f: Var => Typed) = ForEach(this, f)

}


trait Hash {
  self: Typed =>
  type FieldProduce

  def field(name: String): FieldProduce

  def apply[T <: Typed](name: String): T = GetField(this, name).asInstanceOf[T]

  def \(name: String) = field(name)
}

trait Record extends Typed with Hash {

  def pluck(attrs: String*) = Pluck(this, attrs)

  def pluck(m: Map[String, Any]) = Pluck(this, m)

  def without(attrs: String*) = Without(this, attrs)


  def merge(other: Record) = Merge(this, other)

  def merge(other: Map[String, Any]) = Merge(this, other)

  def +(other: Record) = merge(other)

  def hasFields(values: String*) = HasFields(this, values)

  def keys = Keys(this)

}

trait Binary extends Typed {

  def &(other: Binary) = and(other)

  def and(other: Binary) = All(this, other)

  def rand(other: Binary) = All(other, this)

  def &>(other: Binary) = rand(other)

  // or
  def ||(other: Binary) = or(other)

  def or(other: Binary) = Or(this, other)

  // right or
  def >|(other: Binary) = ror(other)

  def ror(other: Binary) = Or(other, this)
}

trait Strings extends Literal {

  //

  // def ===(regexp: String) = find(regexp)
  def find(regexp: Regex) = find(regexp.toString())

  def find(regex: String) = Match(this, regex)
}

trait Numeric extends Literal with Multiply with Binary {

  def -(other: Numeric) = sub(other)

  def sub(other: Numeric) = Sub(this, other)

  def /(other: Numeric) = div(other)

  def div(other: Numeric) = Div(this, other)

  def %(other: Numeric) = mod(other)

  def mod(other: Numeric) = Mod(this, other)
}

trait Filterable[T] extends Typed {
  self: Sequence[T] =>
  //def filter(value: Binary): Filter[T] = filter((x: Var) => value)

  def filter(value: Map[String, Any]):Filter[T] = Filter(this, Left(value))

  def filter(f: Var => Binary): Filter[T] = Filter(this, Right(f))

}

trait Ref extends Numeric with Binary with Record with ArrayTyped[Any] with Literal with Strings

trait ProduceSequence[T] extends Produce[Iterable[T]] with Sequence[T]{

  type FieldProduce = ProduceTypedArray[T]

  def field(name: String): ProduceTypedArray[T] = GetField[T](this, name)

  def run(implicit c: Connection, mf: Manifest[T], d: DummyImplicit): Either[RethinkError, Seq[T]] = toQuery[T].toResult

  def as[R <: T](implicit c: Connection, mf: Manifest[R], d: DummyImplicit): Either[RethinkError, Seq[R]] = toQuery[R].toResult
  def toOpt(implicit c: Connection, mf: Manifest[T], d: DummyImplicit):Option[Seq[T]] = run fold(x => None, Some(_))


}

trait ProduceAnySequence extends ProduceSequence[Any]

trait ProduceSet extends ProduceArray

trait ProduceBinary extends Produce[Boolean] with Binary

//trait ProduceLiteral extends ProduceLiteral with Literal


trait ProduceDocument[T <: Document] extends Produce[T] with Record with DocumentConversion[T] {

  type FieldProduce = ProduceAny

  def field(name: String): ProduceAny = GetField(this, name)
}

trait ProduceAnyDocument extends ProduceDocument[Document] with Record

trait ProduceNumeric extends Produce[Double] with Numeric

trait ProduceString extends Produce[String] with Strings

trait ProduceAny extends Produce[Any] with Ref {

  type FieldProduce = ProduceAny

  def field(name: String): ProduceAny = GetField(this.asInstanceOf[Typed], name)
}

trait ProduceSingleSelection extends ProduceAnyDocument with SingleSelection[Any]

trait ProduceTypedSingleSelection[T <: Document] extends ProduceDocument[T] with SingleSelection[T]

trait ProduceStreamSelection extends ProduceAnySequence with StreamSelection[Any]

trait ProduceTypedStreamSelection[T] extends ProduceSequence[T] with StreamSelection[T]

trait ProduceArray extends ProduceAnySequence with ArrayTyped[Any]
trait ProduceTypedArray[T] extends ProduceSequence[T] with ArrayTyped[T]

trait ProduceJoin[L,R] extends ProduceSequence[JoinResult[L,R]] with JoinTyped[L,R]

sealed trait LogicSignature