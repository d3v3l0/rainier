package com.stripe.rainier.sampler

trait Progress {
  def init(chains: Int): List[ProgressState]
}

object SilentProgress extends Progress {
  def init(chains: Int): List[ProgressState] =
    List.fill(chains)(ProgressState(1e6, { _ =>
      ()
    }))
}

case class ProgressState(outputEverySeconds: Double,
                         fn: ProgressState => Unit) {
  var startTime = 0L
  var phaseStartTime = 0L
  var currentPhase = ""
  var currentIteration = 0
  var phaseIterations = 0
  var gradientEvaluations = 0
  var gradientTime = 0L
  var lastGradientTime = 0L
  var phaseAcceptance = 0.0
  var phasePathLength = 0L
  var stepSize = 0.0
  var lastOutputTime = 0L

  def start() = {
    startTime = System.nanoTime()
  }

  def startPhase(phase: String, iterations: Int) = {
    currentPhase = phase
    phaseStartTime = System.nanoTime()
    phaseAcceptance = 0.0
    phasePathLength = 0L
    phaseIterations = iterations
  }

  def startGradient(): Unit = {
    lastGradientTime = System.nanoTime()
  }

  def endGradient(): Unit = {
    gradientTime += (System.nanoTime() - lastGradientTime)
    gradientEvaluations += 1
  }

  def trackIteration(accept: Double, pathLength: Int): Unit = {
    phaseAcceptance += accept
    phasePathLength += pathLength
    currentIteration += 1
  }

  def updateStepSize(n: Double): Unit = {
    stepSize = n
  }

  def checkOutput() = {
    val t = System.nanoTime()
    if (t - lastOutputTime > 5e8) { //0.5s
      fn(this)
      lastOutputTime = t
    }
  }
}