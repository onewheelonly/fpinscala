package fpinscala.laziness

import Stream._
trait Stream[+A] {

  def foldRight[B](z: => B)(f: (A, => B) => B): B = // The arrow `=>` in front of the argument type `B` means that the function `f` takes its second argument by name and may choose not to evaluate it.
    this match {
      case Cons(h,t) => f(h(), t().foldRight(z)(f)) // If `f` doesn't evaluate its second argument, the recursion never occurs.
      case _ => z
    }

  def exists(p: A => Boolean): Boolean = 
    foldRight(false)((a, b) => p(a) || b) // Here `b` is the unevaluated recursive step that folds the tail of the stream. If `p(a)` returns `true`, `b` will never be evaluated and the computation terminates early.

  @annotation.tailrec
  final def find(f: A => Boolean): Option[A] = this match {
    case Empty => None
    case Cons(h, t) => if (f(h())) Some(h()) else t().find(f)
  }
  def take(n: Int): Stream[A] = this match {
    case Cons(h, t) if (n>=1) => cons(h(), t().take(n-1))
    case _ => Empty
  }

  def drop(n: Int): Stream[A] = this match {
    case Cons(h, t) if (n>=1) => t().drop(n-1)
    case c: Cons[A] => c
    case _ => Empty
  }

  def takeWhile(p: A => Boolean): Stream[A] = this match {
    case Cons(h, t) if p(h()) => cons(h(), t().takeWhile(p))
    case _ => Empty
  }

  def forAll(p: A => Boolean): Boolean = foldRight(true)((a,b) => p(a) && b)

  def takeWhileFolded(p: A => Boolean): Stream[A] =
    foldRight(Empty: Stream[A])((a,b) => if (p(a)) cons(a, b) else Empty)

  def headOption: Option[A] = foldRight(Option.empty[A])((a,_) => Some(a))

  // 5.7 map, filter, append, flatmap using foldRight. Part of the exercise is
  // writing your own function signatures.
  def map[B](f: A => B): Stream[B] = foldRight(Empty: Stream[B])((a,b) => cons(f(a), b))

  def filter(f: A => Boolean): Stream[A] = 
    foldRight(Empty: Stream[A])((a,b) => if (f(a)) cons(a, b) else b)

  def append[B>:A](x: => Stream[B]): Stream[B] = 
    foldRight(x)((a,b) => cons(a,b))

  def flatMap[B](f: A => Stream[B]): Stream[B] = {
    foldRight(Empty: Stream[B])( (a,b) => f(a) append b )
  }

  def startsWith[B](s: Stream[B]): Boolean = {
    val zippedStream = this.zipAll(s)
    val nonMatch = zippedStream filter (x => (x._1 != x._2 && x._2 != None) || x._1 == None)
    nonMatch == Empty
  }

  def toList: List[A] = foldRight(List.empty[A])((b, a) => b :: a)

  def mapUnfold[B](f: A => B): Stream[B] = {
    unfold(this)(s => s match {
      case Cons(h, t) => Some((f(h()), t()))
      case _ => None
    })
  }

  def takeUnfold(n: Int): Stream[A] = unfold((this, n)){
    case (Cons(h,t), i) if (i>0) => Some((h(), (t(),i-1)))
    case _ => None
  }

  def takeWhileUnfold(p: A => Boolean): Stream[A] = unfold(this){
    case Cons(h,t) if p(h()) => Some((h(), t()))
    case _ => None
  }

  def zipWith[B,C](s: Stream[B])(f: (A,B)=>C): Stream[C] = unfold((this, s)){
    case (Cons(h1, t1), Cons(h2, t2)) => Some((f(h1(),h2()), (t1(), t2())))
    case _ => None
  }

  // def zipWith2(s: Stream[A])(f: (A,A)=>A): Stream[A] = unfold((this, s)){
  //   case (Cons(h1, t1), Cons(h2, t2)) => Some((f(h1(),h2()), (t1(), t2())))
  //   case _ => None
  // }

  def zipAll[B](s2: Stream[B]): Stream[(Option[A],Option[B])] = {
    unfold((this, s2)){
      case (Cons(h1, t1), Cons(h2, t2)) => Some((Some(h1()),Some(h2())), (t1(), t2()))
      case (Cons(h1, t1), _) => Some((Some(h1()),None), (t1(), Empty))
      case (_, Cons(h2, t2)) => Some((None, Some(h2())), (Empty, t2()))
      case _ => None
    }
  }

  def tails: Stream[Stream[A]] = unfold((this, false)){
      case (x: Cons[A], _) => Some(x, (x.t(), false))
      case (Empty, false) => Some(Empty, (Empty, true))
      case _ => None
    }   
}
case object Empty extends Stream[Nothing]
case class Cons[+A](h: () => A, t: () => Stream[A]) extends Stream[A]

object Stream {
  def cons[A](hd: => A, tl: => Stream[A]): Stream[A] = {
    lazy val head = hd
    lazy val tail = tl
    Cons(() => head, () => tail)
  }

  def empty[A]: Stream[A] = Empty

  def apply[A](as: A*): Stream[A] =
    if (as.isEmpty) empty 
    else cons(as.head, apply(as.tail: _*))

  val ones: Stream[Int] = Stream.cons(1, ones)
  def constant[A](a: A): Stream[A] = Stream.cons(a, constant(a))
  def from(n: Int): Stream[Int] = Stream.cons(n, from(n+1))

  def fibs: Stream[Int] = {
    def fibsInner(currentHigh: Int, nextValue: Int) : Stream[Int] = 
      Stream.cons(currentHigh, fibsInner(nextValue, currentHigh + nextValue))
    fibsInner(0, 1)
  }

  def unfold[A, S](z: S)(f: S => Option[(A, S)]): Stream[A] = {
    def mapFunc(x: (A,S)) : Stream[A] = Stream.cons(x._1, unfold(x._2)(f))
    f(z) map mapFunc getOrElse Empty
  }

  val onesUnfold: Stream[Int] = unfold(1)(_ => Some(1,1))
  def constantUnfold[A](a: A): Stream[A] = unfold(a)(x => Some(x,x))
  def fromUnfold(n: Int): Stream[Int] = unfold(n)(x => Some(x+1,x+1))
  def fibsUnfold: Stream[Int] = unfold((0,1))( (t) => Some((t._1, (t._2, t._1 + t._2))) )
}
