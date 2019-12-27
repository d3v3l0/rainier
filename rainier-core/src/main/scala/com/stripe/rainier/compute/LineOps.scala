package com.stripe.rainier.compute

private[compute] object LineOps {

  private def axb(nc: NonConstant): (Coefficients, Decimal) =
    nc match {
      case l: Line => (l.ax, l.b)
      case l: LogLine =>
        LogLineOps
          .distribute(l)
          .getOrElse((Coefficients(l -> Decimal.One), Decimal.Zero))
      case _ => (Coefficients(nc -> Decimal.One), Decimal.Zero)
    }

  def sum(left: NonConstant, right: NonConstant): Real = {
    val (lax, lb) = axb(left)
    val (rax, rb) = axb(right)

    val merged = lax.merge(rax)
    if (merged.isEmpty)
      Scalar(lb + rb)
    else
      simplify(merged, lb + rb)
  }

  def scale(nc: NonConstant, v: Decimal): Real = {
    val (ax, b) = axb(nc)
    simplify(ax.mapCoefficients(_ * v), b * v)
  }

  def translate(nc: NonConstant, v: Decimal): Real = {
    val (ax, b) = axb(nc)
    simplify(ax, b + v)
  }

  /*
  Multiply two lines, using the distribution rule, to produce a new Line.
   */
  def multiply(left: Line, right: Line): Line = {
    val allLeft = (Real.one, left.b) :: left.ax.toList
    val allRight = (Real.one, right.b) :: right.ax.toList
    val terms = allLeft.flatMap {
      case (x, a) =>
        allRight.map {
          case (y, c) =>
            (x * y, a * c)
        }
    }
    val (newAx, newB) =
      terms.foldLeft((Coefficients.Empty, Decimal.Zero)) {
        case ((nAx, nB), (x: NonConstant, a)) =>
          (nAx.merge(Coefficients(x -> a)), nB)
        case ((nAx, nB), (Scalar(x), a)) =>
          (nAx, nB + x * a)
      }
    Line(newAx, newB)
  }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term with positive a, we can simplify log(ax) to
  log(a) + log(x). Since we can precompute log(a), this just trades a
  multiply for an add, and there's a chance that log(x) will simplify further.
   */

  def log(line: Line): Option[Real] =
    line.ax match {
      case Coefficients.One(x, a)
          if (a >= Decimal.Zero) && (line.b == Decimal.Zero) =>
        Some(x.log + Math.log(a.toDouble))
      case _ => None
    }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term, we can simplify ax.pow(k) to
  a.pow(k) * x.pow(k). Since we can precompute a.pow(k), this just moves
  a multiply around, and there's a chance that a.pow(k) will simplify further.
   */
  def pow(line: Line, exponent: Decimal): Option[Real] =
    line.ax match {
      case Coefficients.One(x, a) if line.b == Decimal.Zero =>
        Some(x.pow(exponent) * RealOps.pow(a, exponent))
      case _ => None
    }

  /*
  Factor a scalar constant k out of ax+b and return it along with
  (a/k)x + b/k. We don't want to do this while we're building up the
  computation because we want terms to aggregate and cancel out as much as possible.
  But at the last minute before compilation, this can reduce the total number of
  multiplication ops needed, by reducing some of the weights in ax down to 1 or -1.
  We want to pick the k that maximizes how many get reduced that way.
   */
  def factor(line: Line): (Coefficients, Decimal, Decimal) = {
    val coefficientFreqs =
      line.ax.coefficients
        .groupBy(_.abs)
        .map { case (a, xs) => (a, xs.size) }

    val (k, cnt) = coefficientFreqs.maxBy(_._2)
    if (cnt > coefficientFreqs.getOrElse(Decimal.One, 0))
      (line.ax.mapCoefficients(_ / k), line.b / k, k)
    else
      (line.ax, line.b, Decimal.One)
  }

  private def simplify(ax: Coefficients, b: Decimal): Real =
    ax match {
      case Coefficients.Empty => Scalar(b)
      case Coefficients.One(x, Decimal.One) if b == Decimal.Zero =>
        x
      case _ => Line(ax, b)
    }
}
